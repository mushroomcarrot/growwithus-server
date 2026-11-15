"""Reproduce checked-in results from official aggregate observations."""

import json
from pathlib import Path
import unittest

import region_clustering

ROOT = Path(__file__).resolve().parent / "results/yongin-2026-10"


class PublicRegionalResultTest(unittest.TestCase):
    def test_saved_actual_data_model_is_reproducible(self):
        saved = json.loads((ROOT / "model.json").read_text(encoding="utf-8"))
        repeated = region_clustering.train(ROOT / "regions.csv", "2026-10-31",
                                           saved["provenance"]["source_urls"], "public-aggregate")
        for field in ("model", "regions", "metrics"):
            self.assertEqual(saved[field], repeated[field])
        self.assertEqual(saved["provenance"]["input_sha256"], repeated["provenance"]["input_sha256"])

    def test_aggregate_counts_and_source_fingerprints(self):
        saved = json.loads((ROOT / "model.json").read_text(encoding="utf-8"))
        rows = region_clustering.read_regions(ROOT / "regions.csv")
        self.assertEqual(39, len(rows))
        for field, expected in (("population", 1089320), ("households", 450814),
                                ("age_0_14", 127932), ("age_65_plus", 194001)):
            self.assertEqual(expected, sum(row[field] for row in rows))
        self.assertEqual("public-aggregate", saved["provenance"]["data_kind"])
        self.assertTrue(saved["validation"]["city_and_district_sums_match"])
        for source in saved["provenance"]["source_files"]:
            self.assertRegex(source["sha256"], r"^[0-9a-f]{64}$")
            self.assertTrue(source["url"].startswith("https://www.yongin.go.kr/"))


if __name__ == "__main__":
    unittest.main()
