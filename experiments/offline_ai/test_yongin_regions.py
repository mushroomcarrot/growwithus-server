"""Synthetic workbook schema checks; no fixtures claim to be real observations."""

import importlib.util
import unittest

import prepare_yongin_regions as prepare


@unittest.skipUnless(importlib.util.find_spec("openpyxl"), "Optional workbook conversion requires openpyxl")
class WorkbookConversionTest(unittest.TestCase):
    def books(self):
        from openpyxl import Workbook
        population = Workbook()
        population.active["I3"] = "2026. 10. 31. 현재"
        sheet = population.create_sheet("읍면동별인구구성비 (내국인)")
        for column, label in enumerate(["구분", "인구", "남", "여", "세대수"], 1):
            sheet.cell(4, column, label)
        rows = [("용인시", 600, 300, 300, 240)]
        for district in ("처인구", "기흥구", "수지구"):
            rows.append((district, 200, 100, 100, 80))
            for i in (1, 2):
                rows.append((district[0] + f"합성{i}동", 100, 50, 50, 40))
        for row_index, row in enumerate(rows, 5):
            for column, value in enumerate(row, 1):
                sheet.cell(row_index, column, value)
        ages = Workbook()
        age_sheet = ages.active
        age_sheet["A1"] = "읍면동 연령별 인구현황(내국인)"
        age_sheet["B2"] = "2026. 10. 31. 기준"
        ordered = [rows[0]] + [row for row in rows if row[0] in ("처인구", "기흥구", "수지구")]
        ordered += [row for row in rows if row[0].endswith("동")]
        for column, row in enumerate(ordered, 2):
            age_sheet.cell(3, column, row[0])
            age_sheet.cell(4, column, row[1])
            multiplier = row[1] // 100
            for age in range(101):
                age_sheet.cell(5 + age, 1, f"{age}세" if age < 100 else "100세 이상")
                age_sheet.cell(5 + age, column, multiplier if age < 100 else 0)
        # Stale unnamed data and a redundant summary must never enter training.
        age_sheet.cell(4, 12, 999)
        age_sheet.cell(108, 1, "65세 이상")
        age_sheet.cell(108, 2, 999)
        return population, ages

    def extract(self, population, ages):
        regions, totals = prepare.population_rows(population, "2026-10-31")
        cohorts, ignored = prepare.age_counts(ages.active, regions, totals, "2026-10-31")
        return regions, totals, cohorts, ignored

    def test_reconciles_city_districts_and_ignores_unnamed_or_summary_cells(self):
        population, ages = self.books()
        regions, totals, cohorts, ignored = self.extract(population, ages)
        self.assertEqual(6, len(regions))
        self.assertEqual(600, totals["용인시"]["population"])
        self.assertEqual(90, cohorts["용인시"]["age_0_14"])
        self.assertEqual(210, cohorts["용인시"]["age_65_plus"])
        self.assertEqual(["L"], ignored)

    def test_mismatched_reference_date_fails(self):
        for change in ("population", "ages"):
            population, ages = self.books()
            if change == "population":
                population.active["I3"] = "2026. 9. 30. 현재"
            else:
                ages.active["B2"] = "2026. 9. 30. 기준"
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.extract(population, ages)

    def test_sex_total_mismatch_fails(self):
        population, ages = self.books()
        population.worksheets[1]["C7"] = 49
        with self.assertRaises(ValueError):
            self.extract(population, ages)

    def test_household_aggregate_mismatch_fails(self):
        population, ages = self.books()
        population.worksheets[1]["E7"] = 39
        with self.assertRaises(ValueError):
            self.extract(population, ages)

    def test_missing_single_year_age_fails(self):
        population, ages = self.books()
        ages.active["A20"] = "missing"
        with self.assertRaises(ValueError):
            self.extract(population, ages)

    def test_single_age_value_change_fails(self):
        population, ages = self.books()
        ages.active["F5"] = 2
        with self.assertRaises(ValueError):
            self.extract(population, ages)

    def test_foreign_resident_sheet_fails(self):
        population, ages = self.books()
        ages.active["A1"] = "연령별 인구현황(외국인)"
        with self.assertRaises(ValueError):
            self.extract(population, ages)

    def test_integer_validation_preserves_numeric_strings_and_rejects_missing(self):
        self.assertEqual(190, prepare.count("190"))
        for value in (None, True, -1, 1.5, "unknown", float("nan")):
            with self.subTest(value=value), self.assertRaises(ValueError):
                prepare.count(value)


if __name__ == "__main__":
    unittest.main()
