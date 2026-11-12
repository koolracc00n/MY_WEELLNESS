import unittest

from parser import evaluate_status, parse_lab_parameters


class LabParserTests(unittest.TestCase):
    def test_recovers_reference_range_joined_to_unit_by_ocr(self):
        rows = parse_lab_parameters(
            "Hemoglobin  14.2  g/dL13.0-17.0\n"
            "Serum Creatinine  0.95  mg/dL0.70-1.30"
        )

        self.assertEqual(len(rows), 2)
        self.assertEqual(rows[0]["unit"], "g/dL")
        self.assertEqual(rows[0]["referenceRange"], "13.0-17.0")
        self.assertEqual(rows[0]["status"], "NORMAL")
        self.assertEqual(rows[1]["unit"], "mg/dL")
        self.assertEqual(rows[1]["referenceRange"], "0.70-1.30")
        self.assertEqual(rows[1]["status"], "NORMAL")

    def test_missing_reference_is_unknown_not_normal(self):
        rows = parse_lab_parameters("Hemoglobin  14.2  g/dL")

        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["status"], "UNKNOWN")
        self.assertEqual(evaluate_status(14.2, "", None), "UNKNOWN")

    def test_evaluates_out_of_range_value(self):
        self.assertEqual(evaluate_status(108, "70-99", None), "HIGH")


if __name__ == "__main__":
    unittest.main()
