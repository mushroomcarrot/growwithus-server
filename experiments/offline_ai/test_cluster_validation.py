import math
import importlib.util
import json
from pathlib import Path
import unittest

import cluster_validation as validation
import region_clustering as clustering


class ClusterValidationTest(unittest.TestCase):
    def test_ari_ignores_label_numbers(self):
        self.assertEqual(1.0, validation.adjusted_rand([0, 0, 1, 1], [1, 1, 0, 0]))
        self.assertAlmostEqual(-0.5, validation.adjusted_rand([0, 0, 1, 1], [0, 1, 0, 1]))
        self.assertEqual(1.0, validation.adjusted_rand([0, 0, 0], [1, 1, 1]))
        with self.assertRaises(ValueError):
            validation.adjusted_rand([0], [0, 1])

    def test_matching_preserves_partition(self):
        reference = [0, 0, 1, 1, 2, 2]
        self.assertEqual(reference, validation.match_labels(reference, [2, 2, 0, 0, 1, 1], 3))
        with self.assertRaises(ValueError):
            validation.match_labels([0, 1], [0, 4], 3)

    def test_rank_one_pca_has_known_axis_and_variance(self):
        result = validation.pca([[-1, -1, 0], [1, 1, 0]])
        self.assertAlmostEqual(4.0, result["eigenvalues"][0])
        self.assertAlmostEqual(1.0, result["explained_variance_ratio"][0])
        self.assertAlmostEqual(1 / math.sqrt(2), result["components"][0][0])
        self.assertAlmostEqual(1 / math.sqrt(2), result["components"][0][1])
        self.assertAlmostEqual(math.sqrt(2), abs(result["coordinates"][0][0]))

    def test_pca_eigenvectors_are_orthonormal_and_satisfy_covariance_equation(self):
        points = [[1, 4, 2], [2, 3, 4], [6, 5, 9], [8, 2, 5], [7, 4, 6]]
        result = validation.pca(points)
        for i, axis in enumerate(result["components"]):
            self.assertAlmostEqual(1, sum(x * x for x in axis))
            for j, other in enumerate(result["components"]):
                if i != j:
                    self.assertAlmostEqual(0, sum(x * y for x, y in zip(axis, other)))
            product = [sum(x * y for x, y in zip(row, axis)) for row in result["covariance"]]
            for actual, coefficient in zip(product, axis):
                self.assertAlmostEqual(result["eigenvalues"][i] * coefficient, actual)
        translated = validation.pca([[x + 100 for x in row] for row in points])
        for original, shifted in zip(result["eigenvalues"], translated["eigenvalues"]):
            self.assertAlmostEqual(original, shifted)
        self.assertAlmostEqual(1, sum(result["explained_variance_ratio"]))

    def test_pca_rejects_zero_variance_and_invalid_inputs(self):
        for points in ([[1, 1], [1, 1]], [[1, 2], [1, math.nan]], [[1], [2]]):
            with self.subTest(points=points), self.assertRaises(ValueError):
                validation.pca(points)

    def test_stability_is_reproducible_and_accounts_for_every_run(self):
        raw = [[0, 0], [0.1, 0.1], [0.2, 0], [10, 10], [10.1, 10.1], [10.2, 10],
               [-10, 10], [-10.1, 10.1], [-10.2, 10]]
        points, _, _ = clustering.standardize(raw)
        baseline = clustering.fit(points)["labels"]
        first = validation.stability(raw, baseline, seed_runs=3, bootstrap_runs=10)
        self.assertEqual(first, validation.stability(raw, baseline, seed_runs=3, bootstrap_runs=10))
        self.assertEqual(10, len(first["bootstrap_runs"]) + len(first["bootstrap_failures"]))
        self.assertEqual(1, first["seed_ari"]["mean"])
        self.assertEqual(len(raw), len(first["per_sample_agreement"]))
        self.assertTrue(all(0 <= rate <= 1 for rate in first["per_sample_agreement"]))

    def test_percentile_uses_linear_interpolation(self):
        self.assertEqual(1.75, validation.percentile([1, 4, 2, 3], 0.25))

    @unittest.skipUnless(importlib.util.find_spec("numpy"), "Optional independent numerical reference")
    def test_pca_matches_numpy_eigendecomposition(self):
        import numpy as np
        points = [[1, 4, 2], [2, 3, 4], [6, 5, 9], [8, 2, 5], [7, 4, 6]]
        result = validation.pca(points)
        expected = np.linalg.eigvalsh(np.cov(np.array(points), rowvar=False))[::-1]
        for actual, reference in zip(result["eigenvalues"], expected):
            self.assertAlmostEqual(float(reference), actual, places=10)

    def test_checked_in_real_data_analysis_is_internally_consistent(self):
        root = Path(__file__).resolve().parent / "results/yongin-2026-10"
        report = json.loads((root / "validation.json").read_text(encoding="utf-8"))
        model = json.loads((root / "model.json").read_text(encoding="utf-8"))
        self.assertEqual(model["provenance"], report["provenance"])
        self.assertEqual(39, len(report["regions"]))
        self.assertEqual(50, report["stability"]["seed_ari"]["count"])
        self.assertEqual(100, len(report["stability"]["bootstrap_runs"]) + len(report["stability"]["bootstrap_failures"]))
        self.assertTrue(all(0 <= row["bootstrap_agreement"] <= 1 for row in report["regions"]))
        self.assertAlmostEqual(1, sum(report["pca"]["explained_variance_ratio"]))
        self.assertEqual({row["region"] for row in model["regions"]}, {row["region"] for row in report["regions"]})


if __name__ == "__main__":
    unittest.main()
