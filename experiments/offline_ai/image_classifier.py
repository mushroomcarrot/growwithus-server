"""Offline color-histogram nearest-centroid baseline for labeled tomato images."""

import argparse
from collections import defaultdict
import csv
import hashlib
import math
from pathlib import Path
from statistics import fmean
import warnings

from evaluation import evaluate_split, provenance, split_groups, write_report

BINS = 8
MAX_FILE_BYTES = 10 * 1024 * 1024
MAX_PIXELS = 16000000


def histogram(pixels):
    counts = [0] * (3 * BINS)
    total = 0
    for pixel in pixels:
        if len(pixel) != 3 or any(not isinstance(value, int) or not 0 <= value <= 255 for value in pixel):
            raise ValueError("Expected RGB pixels with integer channels in 0..255")
        for channel, value in enumerate(pixel):
            counts[channel * BINS + value * BINS // 256] += 1
        total += 1
    if not total:
        raise ValueError("Image has no pixels")
    return [count / total for count in counts]


def features(path):
    try:
        from PIL import Image, ImageOps
    except ImportError as error:
        raise RuntimeError("Image decoding needs Pillow in an offline experiment environment") from error
    path = Path(path)
    if not path.is_file() or not 0 < path.stat().st_size <= MAX_FILE_BYTES:
        raise ValueError("Image must be a nonempty file no larger than 10 MB")
    with warnings.catch_warnings():
        warnings.simplefilter("error", Image.DecompressionBombWarning)
        with Image.open(path) as image:
            if image.format not in ("JPEG", "PNG", "WEBP") or getattr(image, "n_frames", 1) != 1:
                raise ValueError("Use a single-frame JPEG, PNG or WebP image")
            if image.width * image.height > MAX_PIXELS:
                raise ValueError("Image exceeds the 16 million pixel limit")
            image.load()
            rgb = ImageOps.exif_transpose(image).convert("RGB")
            rgb.thumbnail((64, 64))
            raw = rgb.tobytes()
            return histogram(zip(raw[0::3], raw[1::3], raw[2::3]))


def read_images(path):
    root = Path(path).resolve().parent
    rows, hashes = [], {}
    with Path(path).open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != ["id", "group", "path", "label"]:
            raise ValueError("CSV columns must be id,group,path,label")
        for row in reader:
            if None in row or any(value is None or not value.strip() for value in row.values()):
                raise ValueError("Missing or malformed CSV fields")
            row = {key: value.strip() for key, value in row.items()}
            relative = Path(row["path"])
            resolved = (root / relative).resolve()
            if relative.is_absolute() or not resolved.is_relative_to(root):
                raise ValueError("Image paths must stay beneath the manifest directory")
            vector = features(resolved)
            checksum = hashlib.sha256(resolved.read_bytes()).hexdigest()
            previous = hashes.setdefault(checksum, (row["group"], row["label"]))
            if previous != (row["group"], row["label"]):
                raise ValueError("Identical image bytes cross source groups or labels")
            rows.append(dict(row, features=vector, sha256=checksum))
            if len(rows) > 10000:
                raise ValueError("Use at most 10000 images for this offline baseline")
    return rows


def fit(rows):
    grouped = defaultdict(list)
    for row in rows:
        vector = row["features"]
        if len(vector) != 3 * BINS or any(not math.isfinite(x) or x < 0 or x > 1 for x in vector):
            raise ValueError("Expected 24 finite normalized histogram features")
        for channel in range(3):
            if not math.isclose(sum(vector[channel * BINS:(channel + 1) * BINS]), 1.0):
                raise ValueError("Each channel histogram must sum to one")
        grouped[row["label"]].append(vector)
    if len(grouped) < 2:
        raise ValueError("At least two image classes are required")
    return {"schema_version": 1, "algorithm": "rgb-histogram-nearest-centroid", "bins_per_channel": BINS,
            "thumbnail_max_side": 64,
            "centers": {label: [fmean(column) for column in zip(*grouped[label])] for label in sorted(grouped)}}


def predict(model, vector):
    if len(vector) != 3 * BINS or any(not math.isfinite(x) or not 0 <= x <= 1 for x in vector):
        raise ValueError("Expected 24 finite normalized histogram features")
    distances = {label: sum((x - center) ** 2 for x, center in zip(vector, centroid))
                 for label, centroid in model["centers"].items()}
    return {"label": min(distances, key=lambda label: (distances[label], label)),
            "squared_distances": distances, "score_kind": "distance-not-probability"}


def train(path, source_url, data_kind):
    import PIL
    source = provenance(path, source_url, data_kind)
    rows = read_images(path)
    training, testing = split_groups(rows)
    model = fit(training)
    predictions = [predict(model, row["features"])["label"] for row in testing]
    source["pillow_version"] = PIL.__version__
    source["images"] = [{"id": row["id"], "sha256": row["sha256"]} for row in rows]
    return {"provenance": source, "model": model, "evaluation": evaluate_split(training, testing, predictions),
            "limitations": ["Color-histogram baseline only; this is not a CNN or medical/agronomic diagnosis.",
                            "Background, lighting and crop framing can dominate color features.",
                            "Source plant or capture-session groups must be supplied to avoid leakage.",
                            "Byte hashes do not detect re-encoded or near-duplicate photos.",
                            "No unknown-image rejection: unrelated objects may be assigned a class.",
                            "Synthetic color-image checks do not demonstrate tomato disease recognition."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--source-url", required=True)
    parser.add_argument("--data-kind", required=True, choices=("synthetic", "public-labeled"))
    args = parser.parse_args()
    try:
        write_report(args.output, train(args.input, args.source_url, args.data_kind), args.input)
    except (ValueError, OSError, RuntimeError, ImportError) as error:
        parser.exit(1, f"Training failed: {error}\n")
    print(f"Saved {args.data_kind} image baseline to {args.output}")


if __name__ == "__main__":
    main()
