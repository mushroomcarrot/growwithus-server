"""Generate explicitly synthetic inputs and run independent offline experiments."""

import argparse
import csv
from pathlib import Path

from evaluation import write_report
import image_classifier
import inquiry_classifier
import region_clustering


SOURCE = "https://example.org/synthetic-not-public-data"


def save_csv(path, columns, rows):
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(columns)
        writer.writerows(rows)


def run(root, with_images=False):
    root = Path(root)
    root.mkdir(parents=True, exist_ok=True)
    regions = root / "synthetic-regions.csv"
    save_csv(regions, region_clustering.COLUMNS,
             [[f"synthetic_region_{i}", 1000, households, children, seniors]
              for i, (households, children, seniors) in enumerate(
                  [(500, 50, 400), (510, 55, 410), (300, 250, 50), (310, 240, 60), (400, 150, 180), (410, 160, 190)])])
    write_report(root / "synthetic-region-model.json",
                 region_clustering.train(regions, "2026-10-31", [SOURCE], "synthetic"), regions)
    inquiries = root / "synthetic-inquiries.csv"
    save_csv(inquiries, ["id", "group", "text", "label"],
             [[f"water{i}", f"water_thread{i}", f"관수 물주기 확인 {i:02}", "watering"] for i in range(4)] +
             [[f"pest{i}", f"pest_thread{i}", f"해충 벌레 확인 {i:02}", "pest"] for i in range(4)])
    write_report(root / "synthetic-inquiry-model.json",
                 inquiry_classifier.train(inquiries, SOURCE, "synthetic"), inquiries)
    if with_images:
        from PIL import Image
        rows = []
        for label, color in (("synthetic_green", (0, 210, 0)), ("synthetic_brown", (150, 80, 20))):
            for i in range(4):
                name = f"{label}{i}.png"
                Image.new("RGB", (32, 32), (color[0] + i, color[1], color[2])).save(root / name)
                rows.append([f"{label}{i}", f"{label}_capture{i}", name, label])
        manifest = root / "synthetic-images.csv"
        save_csv(manifest, ["id", "group", "path", "label"], rows)
        write_report(root / "synthetic-image-model.json",
                     image_classifier.train(manifest, SOURCE, "synthetic"), manifest)
    print("Synthetic experiments only. No Yongin observations or tomato disease benchmark.")
    print(f"Reports saved beneath {root}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--with-images", action="store_true")
    args = parser.parse_args()
    run(args.output, args.with_images)
