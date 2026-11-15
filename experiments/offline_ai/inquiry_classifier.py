"""Character n-gram Multinomial Naive Bayes for labeled agriculture inquiries."""

import argparse
from collections import Counter
import csv
import math
from pathlib import Path
import re
import unicodedata

from evaluation import evaluate_split, provenance, split_groups, write_report


def tokens(text):
    if not isinstance(text, str) or not text.strip() or len(text) > 10000:
        raise ValueError("Text must contain 1 to 10000 characters")
    words = re.findall(r"[a-z0-9가-힣]+", unicodedata.normalize("NFKC", text).lower())
    return Counter(word[i:i + n] for word in words for n in (2, 3, 4)
                   for i in range(max(0, len(word) - n + 1)))


def read_inquiries(path):
    with Path(path).open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != ["id", "group", "text", "label"]:
            raise ValueError("CSV columns must be id,group,text,label")
        rows = []
        for row in reader:
            if None in row or any(value is None or not value.strip() for value in row.values()):
                raise ValueError("Missing or malformed CSV fields")
            row = {key: value.strip() for key, value in row.items()}
            if not tokens(row["text"]):
                raise ValueError("Text must contain at least one two-character token")
            rows.append(row)
            if len(rows) > 50000:
                raise ValueError("Use at most 50000 de-identified inquiries")
    # Identical normalized inquiries in different groups would leak across the split.
    seen = {}
    for row in rows:
        normalized = tuple(sorted(tokens(row["text"]).items()))
        previous = seen.setdefault(normalized, (row["group"], row["label"]))
        if previous != (row["group"], row["label"]):
            raise ValueError("Duplicate inquiry crosses source groups or labels")
    return rows


def fit(rows, alpha=1.0):
    if not math.isfinite(alpha) or alpha <= 0 or not rows:
        raise ValueError("Positive smoothing and training rows are required")
    counts, documents = {}, Counter()
    for row in rows:
        label = row["label"]
        counts.setdefault(label, Counter()).update(tokens(row["text"]))
        documents[label] += 1
    vocabulary = sorted(set().union(*(set(value) for value in counts.values())))
    if len(counts) < 2 or not vocabulary:
        raise ValueError("Need two classes and a nonempty training vocabulary")
    priors, likelihoods = {}, {}
    for label in sorted(counts):
        priors[label] = math.log(documents[label] / len(rows))
        denominator = sum(counts[label].values()) + alpha * len(vocabulary)
        likelihoods[label] = {token: math.log((counts[label][token] + alpha) / denominator) for token in vocabulary}
    return {"schema_version": 1, "algorithm": "character-ngram-multinomial-naive-bayes", "ngram_lengths": [2, 3, 4],
            "alpha": alpha, "vocabulary": vocabulary, "log_priors": priors, "log_likelihoods": likelihoods}


def predict(model, text):
    counts = tokens(text)
    known = {token: count for token, count in counts.items() if token in model["log_likelihoods"][next(iter(model["log_priors"]))]}
    if not known:
        return {"label": None, "reason": "no-known-ngrams", "scores": {}}
    scores = {label: prior + sum(count * model["log_likelihoods"][label][token] for token, count in known.items())
              for label, prior in model["log_priors"].items()}
    maximum = max(scores.values())
    weights = {label: math.exp(score - maximum) for label, score in scores.items()}
    total = sum(weights.values())
    label = min(scores, key=lambda item: (-scores[item], item))
    return {"label": label, "reason": "classified", "scores": {key: value / total for key, value in weights.items()},
            "score_kind": "uncalibrated-naive-bayes-posterior"}


def train(path, source_url, data_kind):
    source = provenance(path, source_url, data_kind)
    training, testing = split_groups(read_inquiries(path))
    model = fit(training)
    predictions = [predict(model, row["text"])["label"] for row in testing]
    return {"provenance": source, "model": model, "evaluation": evaluate_split(training, testing, predictions),
            "limitations": ["Requires human-reviewed labels and source-thread groups.",
                            "Character matching is a baseline, not agronomic reasoning.",
                            "Scores are not calibrated confidence; unknown wording can still be misclassified.",
                            "Synthetic test performance does not establish real inquiry quality."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--source-url", required=True)
    parser.add_argument("--data-kind", required=True, choices=("synthetic", "public-labeled"))
    args = parser.parse_args()
    try:
        write_report(args.output, train(args.input, args.source_url, args.data_kind), args.input)
    except (ValueError, OSError) as error:
        parser.exit(1, f"Training failed: {error}\n")
    print(f"Saved {args.data_kind} inquiry baseline to {args.output}")


if __name__ == "__main__":
    main()
