"""Offline PCA and clustering stability analysis. No production integration."""

import argparse
from collections import Counter
import hashlib
from itertools import permutations
import json
import math
from pathlib import Path
import random
from statistics import fmean

import region_clustering as clustering


def adjusted_rand(left, right):
    if len(left) != len(right) or len(left) < 2:
        raise ValueError("ARI requires equal label lists with at least two samples")
    choose2 = lambda value: value * (value - 1) / 2
    cells = sum(choose2(value) for value in Counter(zip(left, right)).values())
    row_pairs = sum(choose2(value) for value in Counter(left).values())
    column_pairs = sum(choose2(value) for value in Counter(right).values())
    expected = row_pairs * column_pairs / choose2(len(left))
    denominator = (row_pairs + column_pairs) / 2 - expected
    return (cells - expected) / denominator if denominator else 1.0


def match_labels(reference, predicted, k):
    if len(reference) != len(predicted) or not reference or not 2 <= k <= 5:
        raise ValueError("Label matching needs equal nonempty lists and 2..5 clusters")
    if any(label not in range(k) for label in reference + predicted):
        raise ValueError("Unexpected cluster label")
    mapping = max(permutations(range(k)), key=lambda values: sum(a == values[b] for a, b in zip(reference, predicted)))
    return [mapping[label] for label in predicted]


def pca(points):
    """Jacobi eigendecomposition of a small symmetric covariance matrix."""
    clustering.validate_points(points)
    width = len(points[0])
    if not 2 <= width <= 8:
        raise ValueError("Offline PCA supports 2..8 features")
    means = [fmean(column) for column in zip(*points)]
    centered = [[x - mean for x, mean in zip(row, means)] for row in points]
    covariance = [[sum(row[i] * row[j] for row in centered) / (len(points) - 1)
                   for j in range(width)] for i in range(width)]
    matrix = [row[:] for row in covariance]
    vectors = [[float(i == j) for j in range(width)] for i in range(width)]
    for _ in range(1000):
        first, second = max(((i, j) for i in range(width) for j in range(i + 1, width)),
                            key=lambda pair: abs(matrix[pair[0]][pair[1]]))
        cross = matrix[first][second]
        if abs(cross) < 1e-12:
            break
        angle = math.atan2(2 * cross, matrix[second][second] - matrix[first][first]) / 2
        c, s = math.cos(angle), math.sin(angle)
        a, b = matrix[first][first], matrix[second][second]
        for i in range(width):
            if i not in (first, second):
                x, y = matrix[i][first], matrix[i][second]
                matrix[i][first] = matrix[first][i] = c * x - s * y
                matrix[i][second] = matrix[second][i] = s * x + c * y
            x, y = vectors[i][first], vectors[i][second]
            vectors[i][first], vectors[i][second] = c * x - s * y, s * x + c * y
        matrix[first][first] = c * c * a - 2 * s * c * cross + s * s * b
        matrix[second][second] = s * s * a + 2 * s * c * cross + c * c * b
        matrix[first][second] = matrix[second][first] = 0.0
    else:
        raise ValueError("PCA covariance eigendecomposition did not converge")
    order = sorted(range(width), key=lambda i: -matrix[i][i])
    eigenvalues = [max(0.0, matrix[i][i]) for i in order]
    total = sum(eigenvalues)
    if total <= 1e-15:
        raise ValueError("PCA requires nonzero variance")
    components = []
    for index in order:
        component = [row[index] for row in vectors]
        pivot = max(range(width), key=lambda i: abs(component[i]))
        if component[pivot] < 0:
            component = [-x for x in component]
        components.append(component)
    coordinates = [[sum(x * coefficient for x, coefficient in zip(row, axis)) for axis in components[:2]]
                   for row in centered]
    return {"method": "centered-covariance-jacobi", "means": means, "covariance": covariance,
            "eigenvalues": eigenvalues, "components": components,
            "explained_variance_ratio": [value / total for value in eigenvalues], "coordinates": coordinates}


def percentile(values, fraction):
    ordered = sorted(values)
    offset = (len(ordered) - 1) * fraction
    low, high = math.floor(offset), math.ceil(offset)
    return ordered[low] + (ordered[high] - ordered[low]) * (offset - low)


def summary(values):
    return {"count": len(values), "mean": fmean(values), "minimum": min(values), "maximum": max(values),
            "p025": percentile(values, 0.025), "median": percentile(values, 0.5), "p975": percentile(values, 0.975)}


def stability(raw, baseline, k=3, seed_runs=50, bootstrap_runs=100):
    if not 2 <= k <= 5 or seed_runs < 1 or bootstrap_runs < 1:
        raise ValueError("Need 2..5 clusters and positive run counts")
    points, _, _ = clustering.standardize(raw)
    seeds = []
    for seed in range(seed_runs):
        fitted = clustering.fit(points, k=k, seed=seed)
        seeds.append({"seed": seed, "ari": adjusted_rand(baseline, fitted["labels"]), "inertia": fitted["inertia"]})
    rng = random.Random(20261115)
    runs, failures, agreements = [], [], [0] * len(raw)
    for run in range(bootstrap_runs):
        indices = [rng.randrange(len(raw)) for _ in raw]
        resampled, means, scales = clustering.standardize([raw[i] for i in indices])
        omitted = [label for label in range(k) if not any(baseline[i] == label for i in indices)]
        try:
            fitted = clustering.fit(resampled, k=k, seed=run)
        except ValueError as error:
            failures.append({"run": run, "reason": str(error)})
            continue
        projected = [[(x - mean) / scale for x, mean, scale in zip(row, means, scales)] for row in raw]
        predicted = clustering.assign(projected, fitted["centers"])
        matched = match_labels(baseline, predicted, k)
        for i, (expected, actual) in enumerate(zip(baseline, matched)):
            agreements[i] += expected == actual
        runs.append({"run": run, "unique_training_samples": len(set(indices)), "omitted_baseline_clusters": omitted,
                     "ari": adjusted_rand(baseline, predicted),
                     "matched_agreement": sum(a == b for a, b in zip(baseline, matched)) / len(raw)})
    if not runs:
        raise ValueError("All bootstrap fits failed")
    return {"seed_runs": seeds, "seed_ari": summary([row["ari"] for row in seeds]),
            "bootstrap_seed": 20261115, "bootstrap_requested": bootstrap_runs, "bootstrap_runs": runs,
            "bootstrap_failures": failures, "bootstrap_ari": summary([row["ari"] for row in runs]),
            "per_sample_agreement": [count / len(runs) for count in agreements],
            "bootstrap_omission_counts": {str(label): sum(label in row["omitted_baseline_clusters"] for row in runs)
                                           for label in range(k)}}


def analyze(csv_path, model_path, output_path, seed_runs=50, bootstrap_runs=100):
    csv_path, model_path, output_path = map(Path, (csv_path, model_path, output_path))
    if output_path.resolve() in (csv_path.resolve(), model_path.resolve()):
        raise ValueError("Analysis output must not overwrite inputs")
    saved = json.loads(model_path.read_text(encoding="utf-8"))
    if hashlib.sha256(csv_path.read_bytes()).hexdigest() != saved["provenance"]["input_sha256"]:
        raise ValueError("CSV fingerprint differs from the published model input")
    rows = clustering.read_regions(csv_path)
    if saved["model"]["features"] != list(clustering.FEATURES) or saved["model"]["k"] != 3:
        raise ValueError("This regional analysis requires the published three-feature, three-cluster model")
    raw = [[row["age_0_14"] / row["population"], row["age_65_plus"] / row["population"],
            row["population"] / row["households"]] for row in rows]
    points, _, _ = clustering.standardize(raw)
    baseline = [clustering.predict(saved["model"], row) for row in raw]
    if [(row["region"], label) for row, label in zip(rows, baseline)] != [(row["region"], row["cluster"]) for row in saved["regions"]]:
        raise ValueError("Published regional assignments do not match model predictions")
    result = {"provenance": saved["provenance"], "sample_count": len(rows), "features": list(clustering.FEATURES),
              "pca": pca(points), "stability": stability(raw, baseline, seed_runs=seed_runs, bootstrap_runs=bootstrap_runs),
              "limitations": ["PCA is a projection of demographic features, not a geographic map or demand score.",
                              "Seed checks use the existing ten-restart configuration.",
                              "Bootstrap resamples regions with replacement, refits scaling and clustering, then assigns all original regions.",
                              "ARI is invariant to label permutations. Per-region agreement uses the best global permutation for each run.",
                              "Bootstrap percentiles are descriptive simulation ranges, not confidence intervals for service performance.",
                              "One date, 39 regions and a two-region cluster limit conclusions. No temporal or customer behavior validation."]}
    result["regions"] = [{"region": row["region"], "cluster": label, "pca": coordinates,
                          "bootstrap_agreement": agreement} for row, label, coordinates, agreement in
                         zip(rows, baseline, result["pca"]["coordinates"], result["stability"]["per_sample_agreement"])]
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(result, ensure_ascii=False, allow_nan=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"pca_explained_variance": result["pca"]["explained_variance_ratio"],
                      "seed_ari": result["stability"]["seed_ari"], "bootstrap_ari": result["stability"]["bootstrap_ari"],
                      "bootstrap_omission_counts": result["stability"]["bootstrap_omission_counts"]}, indent=2))
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("model", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    analyze(args.csv, args.model, args.output)
