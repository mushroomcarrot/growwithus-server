"""Offline demographic clustering. No API requests or production DB access."""

import argparse
import csv
from datetime import date
import hashlib
import json
import math
from pathlib import Path
import random
from statistics import fmean
from urllib.parse import urlparse

FEATURES = ("children_share", "seniors_share", "residents_per_household")
COLUMNS = ("region", "population", "households", "age_0_14", "age_65_plus")


def distance_squared(left, right):
    return sum((a - b) ** 2 for a, b in zip(left, right))


def validate_points(points):
    if len(points) < 2 or not points[0]:
        raise ValueError("At least two nonempty feature vectors are required")
    width = len(points[0])
    if any(len(row) != width or any(not math.isfinite(x) for x in row) for row in points):
        raise ValueError("Feature vectors must have equal width and finite values")


def standardize(points):
    validate_points(points)
    columns = list(zip(*points))
    means = [fmean(column) for column in columns]
    scales = [math.sqrt(fmean((value - mean) ** 2 for value in column)) or 1.0
              for column, mean in zip(columns, means)]
    return [[(x - mean) / scale for x, mean, scale in zip(row, means, scales)]
            for row in points], means, scales


def assign(points, centers):
    return [min(range(len(centers)), key=lambda i: distance_squared(row, centers[i]))
            for row in points]


def initial_centers(points, k, rng):
    centers = [list(rng.choice(points))]
    while len(centers) < k:
        weights = [min(distance_squared(row, center) for center in centers) for row in points]
        centers.append(list(rng.choices(points, weights=weights, k=1)[0]))
    return centers


def fit(points, k=3, seed=42, restarts=10, max_iterations=300):
    """K-means++ with deterministic restarts, operating on standardized features."""
    validate_points(points)
    if not 2 <= k < len(points) or len({tuple(row) for row in points}) < k:
        raise ValueError("Need 2 <= k < sample count and at least k distinct feature vectors")
    if restarts < 1 or max_iterations < 1:
        raise ValueError("Restart and iteration counts must be positive")
    best = None
    rng = random.Random(seed)
    for _ in range(restarts):
        centers = initial_centers(points, k, rng)
        converged = False
        for iteration in range(1, max_iterations + 1):
            labels = assign(points, centers)
            counts = [labels.count(i) for i in range(k)]
            for empty in (i for i, count in enumerate(counts) if count == 0):
                # Move the worst represented point from a cluster that can spare it.
                candidates = [i for i, label in enumerate(labels) if counts[label] > 1]
                moved = max(candidates, key=lambda i: distance_squared(points[i], centers[labels[i]]))
                counts[labels[moved]] -= 1
                labels[moved] = empty
                counts[empty] += 1
            updated = [[fmean(points[i][j] for i, label in enumerate(labels) if label == cluster)
                        for j in range(len(points[0]))] for cluster in range(k)]
            next_labels = assign(points, updated)
            centers = updated
            if next_labels == labels:
                converged = True
                break
        if not converged:
            continue
        # IDs depend on centroids, not on random initialization order.
        order = sorted(range(k), key=lambda i: tuple(centers[i]))
        centers = [centers[i] for i in order]
        labels = assign(points, centers)
        inertia = sum(distance_squared(row, centers[label]) for row, label in zip(points, labels))
        candidate = {"centers": centers, "labels": labels, "inertia": inertia, "iterations": iteration}
        if best is None or inertia < best["inertia"]:
            best = candidate
    if best is None:
        raise ValueError("K-means did not converge within the iteration limit")
    return best


def silhouette(points, labels):
    groups = {label: [i for i, x in enumerate(labels) if x == label] for label in set(labels)}
    if len(groups) < 2 or len(groups) >= len(points):
        raise ValueError("Silhouette requires between 2 and n-1 clusters")
    scores = []
    for i, row in enumerate(points):
        own = [j for j in groups[labels[i]] if j != i]
        if not own:
            scores.append(0.0)
            continue
        a = fmean(math.sqrt(distance_squared(row, points[j])) for j in own)
        b = min(fmean(math.sqrt(distance_squared(row, points[j])) for j in members)
                for label, members in groups.items() if label != labels[i])
        scores.append((b - a) / max(a, b) if max(a, b) else 0.0)
    return fmean(scores)


def read_regions(path):
    regions = []
    seen = set()
    with Path(path).open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != list(COLUMNS):
            raise ValueError("CSV columns must be: " + ",".join(COLUMNS))
        for line, row in enumerate(reader, 2):
            name = (row["region"] or "").strip()
            if not name or name in seen or None in row:
                raise ValueError(f"Line {line}: missing, duplicate or malformed region")
            try:
                population, households, children, seniors = [int(row[key]) for key in COLUMNS[1:]]
            except (TypeError, ValueError) as error:
                raise ValueError(f"Line {line}: counts must be integers") from error
            if population <= 0 or households <= 0 or min(children, seniors) < 0 or children + seniors > population:
                raise ValueError(f"Line {line}: inconsistent population counts")
            seen.add(name)
            regions.append({"region": name, "population": population, "households": households,
                            "age_0_14": children, "age_65_plus": seniors})
    if len(regions) > 200:
        raise ValueError("This offline regional analysis supports at most 200 aggregate regions")
    # Stable training order even when the source CSV rows are reordered.
    return sorted(regions, key=lambda row: row["region"])


def predict(model, features):
    if len(features) != len(model["features"]) or any(not math.isfinite(x) for x in features):
        raise ValueError("Prediction needs a finite vector matching the trained features")
    standardized = [(x - mean) / scale for x, mean, scale in
                    zip(features, model["means"], model["scales"])]
    return assign([standardized], model["centers"])[0]


def train(path, reference_date, source_urls, data_kind, k=3):
    date.fromisoformat(reference_date)
    if data_kind not in ("public-aggregate", "synthetic"):
        raise ValueError("Explicit data kind required")
    if not source_urls or any(urlparse(url).scheme != "https" or not urlparse(url).netloc for url in source_urls):
        raise ValueError("HTTPS source URLs are required")
    regions = read_regions(path)
    raw = [[row["age_0_14"] / row["population"], row["age_65_plus"] / row["population"],
            row["population"] / row["households"]] for row in regions]
    points, means, scales = standardize(raw)
    result = fit(points, k=k)
    model = {"schema_version": 1, "algorithm": "k-means++", "seed": 42, "restarts": 10,
             "k": k, "features": list(FEATURES), "means": means, "scales": scales,
             "centers": result["centers"]}
    clusters = []
    for label, center in enumerate(result["centers"]):
        clusters.append({"cluster": label, "size": result["labels"].count(label),
                         "centroid": dict(zip(FEATURES, [x * scale + mean for x, scale, mean in
                                                        zip(center, scales, means)]))})
    return {"provenance": {"data_kind": data_kind, "reference_date": reference_date,
                           "source_urls": source_urls, "input_sha256": hashlib.sha256(Path(path).read_bytes()).hexdigest()},
            "limitations": ["Describes aggregate demographic similarity, not individual preferences or service demand.",
                            "Cluster count is configured, not a discovered ground truth.",
                            "Source period, resident definition and district boundaries must match before training."],
            "model": model, "metrics": {"inertia": result["inertia"], "silhouette": silhouette(points, result["labels"]),
                                       "iterations": result["iterations"], "sample_count": len(regions)},
            "clusters": clusters,
            "regions": [dict(row, cluster=label, features=dict(zip(FEATURES, vector)))
                        for row, label, vector in zip(regions, result["labels"], raw)]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--reference-date", required=True)
    parser.add_argument("--source-url", action="append", required=True)
    parser.add_argument("--data-kind", choices=("public-aggregate", "synthetic"), required=True)
    parser.add_argument("--clusters", type=int, default=3)
    args = parser.parse_args()
    if args.input.resolve() == args.output.resolve():
        parser.error("Input and output must differ")
    try:
        report = train(args.input, args.reference_date, args.source_url, args.data_kind, args.clusters)
        payload = json.dumps(report, ensure_ascii=False, allow_nan=False, indent=2) + "\n"
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload, encoding="utf-8")
    except (ValueError, OSError) as error:
        parser.exit(1, f"Training failed: {error}\n")
    print(f"Saved {args.data_kind} clustering: {report['metrics']['sample_count']} regions, k={args.clusters}")


if __name__ == "__main__":
    main()
