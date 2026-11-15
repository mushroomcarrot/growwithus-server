"""Shared offline evaluation helpers. Inputs must already be de-identified."""

from collections import Counter
import hashlib
import json
from pathlib import Path
import random
from urllib.parse import urlparse


def split_groups(rows, test_fraction=0.25, seed=42):
    """Keep source threads, plants or capture sessions entirely on one side."""
    if not 0 < test_fraction < 1:
        raise ValueError("Test fraction must be between zero and one")
    labels = sorted({row["label"] for row in rows})
    if len(labels) < 2 or any(not label or label == "__abstain__" for label in labels):
        raise ValueError("At least two named classes are required")
    ids, group_labels = set(), {}
    for row in rows:
        if not row["id"] or row["id"] in ids or not row["group"]:
            raise ValueError("Unique row IDs and nonempty source groups are required")
        ids.add(row["id"])
        previous = group_labels.setdefault(row["group"], row["label"])
        if previous != row["label"]:
            raise ValueError("A group has multiple labels; resolve the label policy before training")
    rng = random.Random(seed)
    test_groups = set()
    for label in labels:
        groups = sorted(group for group, value in group_labels.items() if value == label)
        if len(groups) < 2:
            raise ValueError("Each class needs at least two independent source groups")
        rng.shuffle(groups)
        count = max(1, min(len(groups) - 1, round(len(groups) * test_fraction)))
        test_groups.update(groups[:count])
    training = sorted((row for row in rows if row["group"] not in test_groups), key=lambda row: row["id"])
    testing = sorted((row for row in rows if row["group"] in test_groups), key=lambda row: row["id"])
    return training, testing


def metrics(actual, predicted):
    if len(actual) != len(predicted) or not actual:
        raise ValueError("Evaluation requires equal nonempty label lists")
    labels = sorted(set(actual))
    matrix = {label: {other: 0 for other in labels + ["__abstain__"]} for label in labels}
    for truth, guess in zip(actual, predicted):
        if guess is not None and guess not in labels:
            raise ValueError("Prediction has an unexpected label")
        matrix[truth][guess if guess is not None else "__abstain__"] += 1
    per_class = {}
    for label in labels:
        tp = matrix[label][label]
        fp = sum(matrix[other][label] for other in labels if other != label)
        fn = sum(value for other, value in matrix[label].items() if other != label)
        precision = tp / (tp + fp) if tp + fp else 0.0
        recall = tp / (tp + fn) if tp + fn else 0.0
        per_class[label] = {"precision": precision, "recall": recall,
                            "f1": 2 * precision * recall / (precision + recall) if precision + recall else 0.0}
    return {"accuracy": sum(a == p for a, p in zip(actual, predicted)) / len(actual),
            "macro_f1": sum(row["f1"] for row in per_class.values()) / len(labels),
            "abstained": predicted.count(None), "sample_count": len(actual),
            "per_class": per_class, "confusion_matrix": matrix}


def provenance(path, source_url, data_kind):
    parsed = urlparse(source_url)
    if parsed.scheme != "https" or not parsed.netloc or data_kind not in ("synthetic", "public-labeled"):
        raise ValueError("An HTTPS source URL and explicit data kind are required")
    return {"source_url": source_url, "data_kind": data_kind,
            "manifest_sha256": hashlib.sha256(Path(path).read_bytes()).hexdigest()}


def evaluate_split(training, testing, predictions):
    majority = sorted(Counter(row["label"] for row in training).items(), key=lambda row: (-row[1], row[0]))[0][0]
    actual = [row["label"] for row in testing]
    return {"seed": 42, "method": "stratified-source-group-holdout", "test_fraction": 0.25,
            "training_ids": [row["id"] for row in training], "testing_ids": [row["id"] for row in testing],
            "metrics": metrics(actual, predictions), "majority_baseline": metrics(actual, [majority] * len(actual))}


def write_report(path, report, input_path):
    path = Path(path)
    if path.resolve() == Path(input_path).resolve():
        raise ValueError("Output must not overwrite the dataset manifest")
    payload = json.dumps(report, ensure_ascii=False, allow_nan=False, indent=2) + "\n"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(payload, encoding="utf-8")
