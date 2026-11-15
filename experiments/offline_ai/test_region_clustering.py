"""Synthetic checks only. These are not Yongin population observations."""

import csv
import json
import math
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import region_clustering as clustering


class RegionClusteringTest(unittest.TestCase):
    def test_recovers_three_separated_groups_and_repeats_exactly(self):
        points = [[0, 0], [0, 0.1], [0.1, 0], [10, 10], [10, 10.1], [10.1, 10],
                  [-10, 10], [-10, 10.1], [-10.1, 10]]
        result = clustering.fit(points)
        self.assertEqual(result, clustering.fit(points))
        for group in range(3):
            self.assertEqual(1, len(set(result["labels"][group * 3: group * 3 + 3])))
        self.assertEqual(3, len(set(result["labels"])))
        self.assertGreater(clustering.silhouette(points, result["labels"]), 0.98)
        self.assertAlmostEqual(0.04, result["inertia"])

    def test_standardization_handles_different_units_and_constant_column(self):
        scaled, means, scales = clustering.standardize([[1, 1000, 7], [3, 3000, 7]])
        self.assertEqual([2, 2000, 7], means)
        self.assertEqual([1, 1000, 1], scales)
        self.assertEqual([[-1, -1, 0], [1, 1, 0]], scaled)

    def test_invalid_vectors_and_cluster_counts_fail(self):
        for points in ([], [[]], [[1], [1, 2]], [[1], [math.nan]], [[1], [math.inf]]):
            with self.subTest(points=points), self.assertRaises(ValueError):
                clustering.standardize(points)
        for points, k in (([[1], [2]], 2), ([[1], [1], [1]], 2), ([[1], [2], [3]], 1)):
            with self.subTest(k=k), self.assertRaises(ValueError):
                clustering.fit(points, k)

    def test_duplicate_points_and_singleton_silhouette(self):
        result = clustering.fit([[0], [0], [10], [10], [20]], k=3)
        self.assertEqual([2, 2, 1], [result["labels"].count(i) for i in range(3)])
        self.assertAlmostEqual(0.8, clustering.silhouette([[0], [0], [10], [10], [20]], result["labels"]))

    def test_silhouette_matches_hand_calculated_distances(self):
        # Within-cluster distance is 2. Cross-cluster means are 11, 9, 9, 11.
        expected = ((11 - 2) / 11 + (9 - 2) / 9) / 2
        self.assertAlmostEqual(expected, clustering.silhouette([[0], [2], [10], [12]], [0, 0, 1, 1]))

    def test_oversized_input_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            path = self.write_csv(root, [[f"s{i}", 100, 40, 20, 20] for i in range(201)])
            with self.assertRaises(ValueError):
                clustering.read_regions(path)

    def test_iteration_limit_fails_instead_of_publishing_unconverged_model(self):
        with self.assertRaises(ValueError):
            clustering.fit([[0], [1], [2]], k=2, max_iterations=0)

    def write_csv(self, root, rows):
        path = Path(root) / "synthetic.csv"
        with path.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(clustering.COLUMNS)
            writer.writerows(rows)
        return path

    def test_training_preserves_provenance_and_predicts_own_assignments(self):
        rows = [[f"synthetic_{i}", 1000, households, young, old] for i, (households, young, old) in
                enumerate([(500, 50, 400), (510, 55, 410), (300, 250, 50), (310, 240, 60),
                           (400, 150, 180), (410, 160, 190)])]
        with tempfile.TemporaryDirectory() as root:
            path = self.write_csv(root, rows)
            report = clustering.train(path, "2026-10-31", ["https://example.org/synthetic"], "synthetic")
            self.assertEqual("synthetic", report["provenance"]["data_kind"])
            self.assertEqual(64, len(report["provenance"]["input_sha256"]))
            self.assertEqual(6, report["metrics"]["sample_count"])
            for region in report["regions"]:
                values = [region["features"][key] for key in clustering.FEATURES]
                self.assertEqual(region["cluster"], clustering.predict(report["model"], values))
            path = self.write_csv(root, list(reversed(rows)))
            reordered = clustering.train(path, "2026-10-31", ["https://example.org/synthetic"], "synthetic")
            self.assertEqual(report["model"], reordered["model"])
            self.assertEqual(report["regions"], reordered["regions"])
            with self.assertRaises(ValueError):
                clustering.predict(report["model"], [math.nan, 1, 1])

    def test_invalid_csv_fails_before_training(self):
        bad_rows = [
            [["a", 100, 50, 80, 80]], [["a", 0, 10, 0, 0]], [["a", 10, 0, 0, 0]],
            [["a", 10, 3, -1, 0]], [["a", 10, 3, 0, 0], ["a", 10, 3, 0, 0]],
            [["a", "1.5", 3, 0, 0]], [["", 10, 3, 0, 0]],
        ]
        with tempfile.TemporaryDirectory() as root:
            for rows in bad_rows:
                with self.subTest(rows=rows), self.assertRaises(ValueError):
                    clustering.read_regions(self.write_csv(root, rows))
            path = Path(root) / "wrong.csv"
            path.write_text("name,count\na,1\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                clustering.read_regions(path)

    def test_cli_generates_explicitly_synthetic_report(self):
        with tempfile.TemporaryDirectory() as root:
            path = self.write_csv(root, [[f"s{i}", 100, 20 + i * 5, 5 + i * 8, 50 - i * 5]
                                         for i in range(6)])
            output = Path(root) / "report.json"
            result = subprocess.run([sys.executable, str(Path(clustering.__file__)), str(path), str(output),
                                     "--reference-date", "2026-10-31", "--source-url", "https://example.org/synthetic",
                                     "--data-kind", "synthetic"], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual("synthetic", json.loads(output.read_text(encoding="utf-8"))["provenance"]["data_kind"])

    def test_invalid_metadata_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            path = self.write_csv(root, [])
            for reference, urls, kind in (("bad", ["https://example.org"], "synthetic"),
                                           ("2026-10-31", [], "synthetic"),
                                           ("2026-10-31", ["http://example.org"], "synthetic"),
                                           ("2026-10-31", ["https://example.org"], "unknown")):
                with self.subTest(reference=reference, kind=kind), self.assertRaises(ValueError):
                    clustering.train(path, reference, urls, kind)


if __name__ == "__main__":
    unittest.main()
