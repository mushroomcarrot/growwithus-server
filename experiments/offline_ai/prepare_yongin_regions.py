"""Validate Yongin population workbooks and train the isolated regional model."""

import argparse
import csv
from datetime import date
import hashlib
import json
import math
from pathlib import Path
import re

import region_clustering

SOURCES = {
    "population": "https://www.yongin.go.kr/component/file/ND_fileDownload.do?q_fileId=693c5488-9c9f-4974-82c1-d306e3b0561a&q_fileSn=525802",
    "ages": "https://www.yongin.go.kr/component/file/ND_fileDownload.do?q_fileId=211a4a72-b3b9-4c58-8425-19c954b05174&q_fileSn=525802",
}
REFERENCE_DATE = "2026-10-31"


def normalize(value):
    return re.sub(r"\s+", "", str(value or ""))


def count(value):
    if isinstance(value, str) and re.fullmatch(r"[0-9]+", value.strip()):
        value = int(value)
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0 or int(value) != value:
        raise ValueError(f"Expected a nonnegative integer count, got {value!r}")
    return int(value)


def check_date(value, expected):
    match = re.search(r"(\d{4})\s*\.\s*(\d{1,2})\s*\.\s*(\d{1,2})", str(value))
    if not match or date(*(int(x) for x in match.groups())).isoformat() != expected:
        raise ValueError("Workbook reference date does not match the requested date")


def population_rows(workbook, reference_date):
    check_date(workbook.worksheets[0]["I3"].value, reference_date)
    sheet = workbook["읍면동별인구구성비 (내국인)"]
    headers = [normalize(sheet.cell(4, column).value) for column in range(1, 6)]
    if headers != ["구분", "인구", "남", "여", "세대수"]:
        raise ValueError("Domestic population sheet schema changed")
    totals, regions = {}, {}
    district = None
    for values in sheet.iter_rows(min_row=5, values_only=True):
        name = normalize(values[0])
        if not name:
            continue
        population, male, female, households = [count(value) for value in values[1:5]]
        if male + female != population or not population or not households:
            raise ValueError(f"Population, sex or household total mismatch: {name}")
        if name == "용인시" or name in ("처인구", "기흥구", "수지구"):
            if name in totals:
                raise ValueError("Duplicate aggregate region")
            totals[name] = {"population": population, "households": households}
            district = None if name == "용인시" else name
            continue
        if district is None or not name.endswith(("읍", "면", "동")) or name in regions:
            raise ValueError(f"Unrecognized or duplicate administrative region: {name}")
        regions[name] = {"region": district + "/" + name, "district": district,
                         "population": population, "households": households}
    if set(totals) != {"용인시", "처인구", "기흥구", "수지구"}:
        raise ValueError("Missing city or district aggregates")
    for name, aggregate in totals.items():
        selected = [row for row in regions.values() if name == "용인시" or row["district"] == name]
        for field in ("population", "households"):
            if sum(row[field] for row in selected) != aggregate[field]:
                raise ValueError(f"Administrative aggregate mismatch: {name}/{field}")
    return regions, totals


def age_counts(sheet, regions, totals, reference_date):
    check_date(sheet["B2"].value, reference_date)
    if "내국인" not in str(sheet["A1"].value):
        raise ValueError("Age sheet must describe domestic residents")
    columns, ignored = {}, []
    for column in range(2, sheet.max_column + 1):
        name = normalize(sheet.cell(3, column).value)
        if not name:
            if sheet.cell(4, column).value is not None:
                ignored.append(sheet.cell(3, column).column_letter)
            continue
        if name not in regions and name not in totals or name in columns:
            raise ValueError(f"Unrecognized or duplicate age-sheet region: {name}")
        columns[name] = column
    if set(columns) != set(regions) | set(totals):
        raise ValueError("Population and age-sheet administrative regions differ")
    ages = {}
    for index in range(5, sheet.max_row + 1):
        label = normalize(sheet.cell(index, 1).value)
        match = re.fullmatch(r"([0-9]+)세(이상)?", label)
        if match:
            age = int(match.group(1))
            if age not in range(101) or (age == 100) != bool(match.group(2)) or age in ages:
                raise ValueError("Age rows must be 0..99 years plus a single 100+ row")
            ages[age] = index
            if age == 100:
                break
    if set(ages) != set(range(101)):
        raise ValueError("Missing single-year age rows")
    cohorts = {}
    for name, column in columns.items():
        values = [count(sheet.cell(ages[age], column).value) for age in range(101)]
        population = (regions.get(name) or totals[name])["population"]
        if sum(values) != population or count(sheet.cell(4, column).value) != population:
            raise ValueError(f"Single-year age total mismatch: {name}")
        cohorts[name] = {"age_0_14": sum(values[:15]), "age_65_plus": sum(values[65:])}
    for name in totals:
        selected = [row for row in regions if name == "용인시" or regions[row]["district"] == name]
        for field in ("age_0_14", "age_65_plus"):
            if sum(cohorts[row][field] for row in selected) != cohorts[name][field]:
                raise ValueError(f"Age cohort district sum mismatch: {name}/{field}")
    return cohorts, ignored


def prepare(population_path, ages_path, destination):
    from openpyxl import load_workbook
    population_path, ages_path, destination = map(Path, (population_path, ages_path, destination))
    population_book = load_workbook(population_path, data_only=True)
    ages_book = load_workbook(ages_path, data_only=True)
    try:
        regions, totals = population_rows(population_book, REFERENCE_DATE)
        cohorts, ignored = age_counts(ages_book.worksheets[0], regions, totals, REFERENCE_DATE)
    finally:
        population_book.close()
        ages_book.close()
    rows = [dict(row, **cohorts[name]) for name, row in regions.items()]
    destination.mkdir(parents=True, exist_ok=True)
    csv_path = destination / "yongin-regions-2026-10.csv"
    with csv_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=region_clustering.COLUMNS, extrasaction="ignore", lineterminator="\n")
        writer.writeheader()
        writer.writerows(sorted(rows, key=lambda row: row["region"]))
    report = region_clustering.train(csv_path, REFERENCE_DATE, list(SOURCES.values()), "public-aggregate")
    report["provenance"]["source_files"] = [
        {"kind": kind, "url": SOURCES[kind], "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        for kind, path in (("population", population_path), ("ages", ages_path))]
    report["validation"] = {"resident_definition": "domestic-registered-residents", "region_count": len(regions),
                             "city_population": totals["용인시"]["population"], "city_households": totals["용인시"]["households"],
                             "city_children_0_14": cohorts["용인시"]["age_0_14"],
                             "city_seniors_65_plus": cohorts["용인시"]["age_65_plus"],
                             "single_year_age_rows": 101, "city_and_district_sums_match": True,
                             "ignored_unnamed_age_columns": ignored,
                             "age_summary_rows_used": False}
    comparisons = []
    for k in range(2, 6):
        candidate = region_clustering.train(csv_path, REFERENCE_DATE, list(SOURCES.values()), "public-aggregate", k)
        comparisons.append(dict(k=k, **candidate["metrics"]))
    report["cluster_count_comparison"] = comparisons
    report["limitations"].append("The published k=3 result is configured; the k comparison does not establish service usefulness.")
    model_path = destination / "yongin-region-model-2026-10.json"
    model_path.write_text(json.dumps(report, ensure_ascii=False, allow_nan=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"validation": report["validation"], "metrics": report["metrics"], "clusters": report["clusters"]}, ensure_ascii=False, indent=2))
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("population", type=Path)
    parser.add_argument("ages", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    prepare(args.population, args.ages, args.output)
