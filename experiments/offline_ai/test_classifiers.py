"""Synthetic algorithm checks, not a public dataset quality benchmark."""

import csv
import importlib.util
import math
from pathlib import Path
import tempfile
import unittest

import evaluation
import image_classifier as images
import inquiry_classifier as inquiries


def write_csv(root, name, columns, rows):
    path = Path(root) / name
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(columns)
        writer.writerows(rows)
    return path


class EvaluationTest(unittest.TestCase):
    def test_split_is_reproducible_and_keeps_groups_together(self):
        rows = [{"id": f"{label}{group}{i}", "group": f"{label}{group}", "label": label}
                for label in ("a", "b") for group in range(4) for i in range(2)]
        training, testing = evaluation.split_groups(rows)
        self.assertEqual((training, testing), evaluation.split_groups(list(reversed(rows))))
        self.assertFalse({row["group"] for row in training} & {row["group"] for row in testing})
        self.assertEqual({"a", "b"}, {row["label"] for row in testing})
        self.assertEqual(12, len(training))
        self.assertEqual(4, len(testing))

    def test_invalid_groups_and_duplicate_ids_fail(self):
        valid = [{"id": f"{label}{group}", "group": f"{label}{group}", "label": label}
                 for label in ("a", "b") for group in range(2)]
        for rows in (valid[:2], valid + [valid[0]],
                     valid + [{"id": "extra", "group": "a0", "label": "b"}],
                     [dict(row, group="") for row in valid]):
            with self.subTest(rows=rows), self.assertRaises(ValueError):
                evaluation.split_groups(rows)

    def test_metrics_match_hand_calculation_and_count_abstention(self):
        result = evaluation.metrics(["a", "a", "b", "b"], ["a", "b", "b", None])
        self.assertEqual(0.5, result["accuracy"])
        self.assertAlmostEqual((2 / 3 + 0.5) / 2, result["macro_f1"])
        self.assertEqual(1, result["abstained"])
        self.assertEqual(1, result["confusion_matrix"]["b"]["__abstain__"])
        with self.assertRaises(ValueError):
            evaluation.metrics(["a"], ["unknown"])

    def test_report_cannot_overwrite_input(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "input.csv"
            path.write_text("unchanged")
            with self.assertRaises(ValueError):
                evaluation.write_report(path, {}, path)
            self.assertEqual("unchanged", path.read_text())


class InquiryTest(unittest.TestCase):
    def training_rows(self):
        return [{"label": "watering", "text": "물을 주고 관수했습니다 물주기"},
                {"label": "watering", "text": "관수하고 물을 충분히 줬어요"},
                {"label": "pest", "text": "잎에 벌레가 보여 해충 확인했습니다"},
                {"label": "pest", "text": "해충과 벌레를 제거했습니다"}]

    def test_korean_classification_and_scores(self):
        model = inquiries.fit(self.training_rows())
        self.assertEqual("watering", inquiries.predict(model, "물을 주고 관수했어요")["label"])
        result = inquiries.predict(model, "벌레와 해충을 제거했어요")
        self.assertEqual("pest", result["label"])
        self.assertAlmostEqual(1, sum(result["scores"].values()))
        self.assertEqual(model, inquiries.fit(self.training_rows()))

    def test_unknown_text_abstains_and_empty_text_is_rejected(self):
        model = inquiries.fit(self.training_rows())
        self.assertIsNone(inquiries.predict(model, "xyzabcdef")["label"])
        for text in ("", " " * 5, "가" * 10001):
            with self.assertRaises(ValueError):
                inquiries.predict(model, text)

    def test_holdout_vocabulary_is_never_used_for_training(self):
        model = inquiries.fit(self.training_rows())
        self.assertFalse(set(inquiries.tokens("홀드아웃전용어")) & set(model["vocabulary"]))
        for alpha in (0, -1, math.nan):
            with self.assertRaises(ValueError):
                inquiries.fit(self.training_rows(), alpha)

    def test_duplicate_text_across_groups_fails(self):
        with tempfile.TemporaryDirectory() as root:
            path = write_csv(root, "data.csv", ["id", "group", "text", "label"],
                             [["a", "g1", "물주기 해주세요", "water"], ["b", "g2", "물주기 해주세요!", "water"]])
            with self.assertRaises(ValueError):
                inquiries.read_inquiries(path)

    def test_csv_training_reports_separate_holdout_and_baseline(self):
        with tempfile.TemporaryDirectory() as root:
            rows = [[f"water{i}", f"wg{i}", f"관수 물주기 {100 + i}", "water"] for i in range(4)]
            rows += [[f"pest{i}", f"pg{i}", f"해충 벌레 {200 + i}", "pest"] for i in range(4)]
            path = write_csv(root, "data.csv", ["id", "group", "text", "label"], rows)
            report = inquiries.train(path, "https://example.org/synthetic", "synthetic")
            self.assertEqual("synthetic", report["provenance"]["data_kind"])
            self.assertEqual(2, report["evaluation"]["metrics"]["sample_count"])
            self.assertEqual(0.5, report["evaluation"]["majority_baseline"]["accuracy"])
            self.assertFalse(set(report["evaluation"]["training_ids"]) & set(report["evaluation"]["testing_ids"]))
            by_id = {row[0]: row[2] for row in rows}
            trained_tokens = set().union(*(set(inquiries.tokens(by_id[row_id])) for row_id in report["evaluation"]["training_ids"]))
            heldout_tokens = set().union(*(set(inquiries.tokens(by_id[row_id])) for row_id in report["evaluation"]["testing_ids"]))
            holdout_only = heldout_tokens - trained_tokens
            self.assertTrue(holdout_only)
            self.assertFalse(holdout_only & set(report["model"]["vocabulary"]))


class ImageAlgorithmTest(unittest.TestCase):
    def test_histogram_is_normalized_and_tracks_channels(self):
        vector = images.histogram([(255, 0, 0), (0, 255, 0)])
        self.assertEqual(24, len(vector))
        for channel in range(3):
            self.assertAlmostEqual(1, sum(vector[channel * 8:(channel + 1) * 8]))
        self.assertEqual(0.5, vector[7])
        self.assertEqual(1, vector[16])

    def test_nearest_centroid_training_and_prediction(self):
        green = images.histogram([(0, 220, 0)])
        brown = images.histogram([(150, 80, 20)])
        rows = [{"label": "green", "features": green}, {"label": "brown", "features": brown}]
        model = images.fit(rows)
        self.assertEqual("green", images.predict(model, images.histogram([(5, 225, 4)]))["label"])
        self.assertEqual("brown", images.predict(model, brown)["label"])
        self.assertEqual("distance-not-probability", images.predict(model, brown)["score_kind"])

    def test_invalid_image_features_fail(self):
        for pixels in ([], [(256, 0, 0)], [(1, 2)], [(1.1, 2, 3)]):
            with self.assertRaises(ValueError):
                images.histogram(pixels)
        for vector in ([0] * 24, [math.nan] * 24, [0] * 23):
            with self.assertRaises(ValueError):
                images.fit([{"label": "a", "features": vector}, {"label": "b", "features": vector}])


@unittest.skipUnless(importlib.util.find_spec("PIL"), "Pillow is an optional local image-decoding dependency")
class ImageDecodingTest(unittest.TestCase):
    def test_png_jpeg_webp_decode_and_labeled_training(self):
        from PIL import Image
        with tempfile.TemporaryDirectory() as root:
            rows = []
            for label, base in (("green", (0, 210, 0)), ("brown", (150, 80, 20))):
                for i, suffix in enumerate(("png", "jpg", "webp", "png")):
                    name = f"{label}{i}.{suffix}"
                    Image.new("RGB", (12, 12), (base[0] + i, base[1], base[2])).save(Path(root) / name)
                    self.assertEqual(24, len(images.features(Path(root) / name)))
                    rows.append([f"{label}{i}", f"{label}group{i}", name, label])
            path = write_csv(root, "images.csv", ["id", "group", "path", "label"], rows)
            report = images.train(path, "https://example.org/synthetic", "synthetic")
            self.assertEqual("synthetic", report["provenance"]["data_kind"])
            self.assertEqual(8, len(report["provenance"]["images"]))
            self.assertEqual(1, report["evaluation"]["metrics"]["accuracy"])

    def test_corrupt_animated_and_escaping_files_fail(self):
        from PIL import Image
        with tempfile.TemporaryDirectory() as root:
            corrupt = Path(root) / "bad.png"
            corrupt.write_text("not an image")
            with self.assertRaises(OSError):
                images.features(corrupt)
            animated = Path(root) / "animated.png"
            Image.new("RGB", (2, 2), "red").save(animated, save_all=True,
                                                append_images=[Image.new("RGB", (2, 2), "green")])
            with self.assertRaises(ValueError):
                images.features(animated)
            path = write_csv(root, "images.csv", ["id", "group", "path", "label"], [["1", "g", "../escape.png", "a"]])
            with self.assertRaises(ValueError):
                images.read_images(path)

    def test_same_photo_in_different_groups_is_rejected(self):
        from PIL import Image
        with tempfile.TemporaryDirectory() as root:
            Image.new("RGB", (2, 2), "green").save(Path(root) / "a.png")
            path = write_csv(root, "images.csv", ["id", "group", "path", "label"],
                             [["1", "g1", "a.png", "green"], ["2", "g2", "a.png", "green"]])
            with self.assertRaises(ValueError):
                images.read_images(path)


if __name__ == "__main__":
    unittest.main()
