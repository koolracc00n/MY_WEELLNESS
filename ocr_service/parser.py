"""
Laboratory Report Parsing Module for My Wellness OCR Service.
Extracts actual parameter names, numeric values, measurement units,
and reference intervals from unstructured OCR text.
Computes high/low/normal status accurately according to reference ranges.
Does not generate or invent fake laboratory values.
"""

import logging
import re
from typing import Dict, List, Optional, Set, Tuple

logger = logging.getLogger("ocr_service.parser")

# Standard numeric regex pattern
NUM_FLOAT = r"-?\d+(?:[.,]\d+)?"

# Range matching: "13.0 - 17.0", "70-99", "1.5 to 4.5", "150,000 - 450,000"
RANGE_PATTERN = re.compile(
    rf"^\s*(?P<low>{NUM_FLOAT})\s*(?:-|–|—|to)\s*(?P<high>{NUM_FLOAT})\s*$",
    re.IGNORECASE
)

# Single bound matching: "< 200", "<= 100", "> 40", ">= 60", "Up to 40"
SINGLE_BOUND_PATTERN = re.compile(
    rf"^\s*(?:(?:Up\s*to|<=\s*|<|>=\s*|>)\s*)?(?P<operator><=|<|>=|>|Up\s*to)?\s*(?P<bound>{NUM_FLOAT})\s*$",
    re.IGNORECASE
)

# Line pattern 1: Tabular row with parameter, value, optional unit, optional reference, optional status
LINE_PATTERN = re.compile(
    rf"^(?P<parameter>[A-Za-z][A-Za-z0-9\s/()._%+–—#-]{{1,65}}?)"
    rf"(?:\s*[:|]\s*|\s{{2,}}|\t)"
    rf"(?P<value>[<>]?\s*{NUM_FLOAT})"
    rf"(?:\s+(?P<unit>[A-Za-zµμ%/][A-Za-zµμ%/0-9.^*_-]{{0,15}}))?"
    rf"(?:\s+(?P<reference>(?:(?:[<>]=?|Up\s*to)\s*)?{NUM_FLOAT}(?:\s*(?:-|–|—|to)\s*{NUM_FLOAT})?))?"
    rf"(?:\s+(?P<status>NORMAL|HIGH|LOW|ABNORMAL|DESIRABLE|OPTIMAL|H|L))?\s*$",
    re.IGNORECASE
)

# Alternative line pattern without strict multi-space delimiter
COMPACT_LINE_PATTERN = re.compile(
    rf"^(?P<parameter>[A-Za-z][A-Za-z0-9\s/()._%+–—#-]{{1,50}}?)\s+"
    rf"(?P<value>[<>]?\s*{NUM_FLOAT})"
    rf"(?:\s+(?P<unit>[A-Za-zµμ%/][A-Za-zµμ%/0-9.^*_-]{{0,15}}))?"
    rf"(?:\s+(?P<reference>(?:(?:[<>]=?|Up\s*to)\s*)?{NUM_FLOAT}\s*(?:-|–|—|to)\s*{NUM_FLOAT}|[<>]=?\s*{NUM_FLOAT}))?"
    rf"(?:\s+(?P<status>NORMAL|HIGH|LOW|ABNORMAL|H|L))?\s*$",
    re.IGNORECASE
)

# OCR often removes the space between a unit and its reference interval, e.g.
# "g/dL13.0-17.0". Recover the range from a unit captured too greedily.
EMBEDDED_REFERENCE_PATTERN = re.compile(
    rf"^(?P<unit>.*?[A-Za-zµμ%/])(?P<reference>(?:(?:[<>]=?|Up\s*to)\s*)?{NUM_FLOAT}(?:\s*(?:-|–|—|to)\s*{NUM_FLOAT})?)$",
    re.IGNORECASE,
)

# Non-biomarker header keywords to ignore
IGNORED_LINE_KEYWORDS = {
    "page", "patient", "sample", "doctor", "dr.", "signature", "hospital", "laboratory",
    "clinic", "tel:", "phone", "email", "address", "date", "time", "barcode", "invoice",
    "bill", "registration", "specimen", "collection", "reported", "authorized", "technician",
    "pathologist", "method:", "methodology", "department", "test name", "investigation",
    "biological reference", "ref. interval", "units", "result", "normal range", "parameters"
}


def clean_number(text: str) -> float:
    """Converts a string representation of a float or integer to a float."""
    sanitized = text.strip().replace(",", ".")
    sanitized = re.sub(r"^[<>=]+\s*", "", sanitized)
    return float(sanitized)


def evaluate_status(value_num: float, reference_text: str, reported_status: Optional[str]) -> str:
    """
    Evaluates whether a value is NORMAL, HIGH, LOW, or UNKNOWN based on report flags
    or printed biological reference intervals.
    """
    if reported_status:
        normalized = reported_status.upper().strip()
        if normalized in ("H", "HIGH", "ABNORMAL"):
            return "HIGH" if normalized != "ABNORMAL" else "ABNORMAL"
        if normalized in ("L", "LOW"):
            return "LOW"
        if normalized in ("NORMAL", "DESIRABLE", "OPTIMAL"):
            return "NORMAL"

    if not reference_text:
        return "UNKNOWN"

    ref_clean = reference_text.strip()

    # Case 1: Range "low - high"
    range_match = RANGE_PATTERN.match(ref_clean)
    if range_match:
        try:
            low = clean_number(range_match.group("low"))
            high = clean_number(range_match.group("high"))
            if low > high:
                low, high = high, low
            if value_num < low:
                return "LOW"
            if value_num > high:
                return "HIGH"
            return "NORMAL"
        except (ValueError, TypeError):
            pass

    # Case 2: Single bound "< bound", "<= bound", "Up to bound", "> bound"
    bound_match = SINGLE_BOUND_PATTERN.match(ref_clean)
    if bound_match:
        try:
            operator = (bound_match.group("operator") or "<").lower().strip()
            bound = clean_number(bound_match.group("bound"))

            if operator in ("<", "<=", "up to"):
                return "NORMAL" if value_num <= bound else "HIGH"
            elif operator in (">", ">="):
                return "NORMAL" if value_num >= bound else "LOW"
        except (ValueError, TypeError):
            pass

    return "UNKNOWN"


def is_noise_line(line: str) -> bool:
    """Checks whether an OCR line is metadata, header, or disclaimers."""
    clean = line.lower().strip()
    if not clean or len(clean) < 3:
        return True

    # Check for known metadata tokens
    for kw in IGNORED_LINE_KEYWORDS:
        if clean.startswith(kw) or clean == kw:
            return True

    # Check if header line (contains both "test" and "result" or "parameter" and "unit")
    if ("test" in clean or "parameter" in clean) and ("result" in clean or "value" in clean):
        return True

    return False


def parse_lab_parameters(ocr_text: str) -> List[Dict]:
    """
    Parses full report text line by line and extracts structured lab parameter records.
    Returns:
    List of dicts:
    [
        {
            "parameter": "Hemoglobin",
            "value": 14.2,
            "unit": "g/dL",
            "referenceRange": "13.0 - 17.0",
            "status": "NORMAL"
        },
        ...
    ]
    """
    if not ocr_text or not ocr_text.strip():
        logger.warning("Empty OCR text provided to parser.")
        return []

    parameters = []
    seen_parameters: Set[str] = set()

    lines = ocr_text.splitlines()
    for raw_line in lines:
        line = raw_line.strip()
        # Remove pipe, colon, or box-drawing characters at line boundaries
        line = re.sub(r"^[|\-—–:;\s]+|[|\-—–:;\s]+$", "", line).strip()

        if is_noise_line(line):
            continue

        match = LINE_PATTERN.match(line) or COMPACT_LINE_PATTERN.match(line)
        if not match:
            # Try splitting by multiple whitespace or vertical bar |
            tokens = [t.strip() for t in re.split(r"\s{2,}|\t|\|", line) if t.strip()]
            if len(tokens) >= 2:
                param_cand = tokens[0]
                val_cand = tokens[1]
                unit_cand = tokens[2] if len(tokens) > 2 else ""
                ref_cand = tokens[3] if len(tokens) > 3 else ""
                status_cand = tokens[4] if len(tokens) > 4 else None

                # Check if val_cand is a number
                val_num_match = re.search(rf"^{NUM_FLOAT}$", re.sub(r"^[<>=]", "", val_cand))
                if val_num_match and re.match(r"^[A-Za-z]", param_cand):
                    try:
                        val_num = clean_number(val_cand)
                        param_name = param_cand.strip(" .:-_")
                        if len(param_name) >= 2 and not is_noise_line(param_name):
                            status_val = evaluate_status(val_num, ref_cand, status_cand)
                            key = param_name.casefold()
                            if key not in seen_parameters:
                                seen_parameters.add(key)
                                parameters.append({
                                    "parameter": param_name,
                                    "value": val_num,
                                    "unit": unit_cand.strip(),
                                    "referenceRange": ref_cand.strip(),
                                    "status": status_val
                                })
                                continue
                    except (ValueError, TypeError):
                        pass
            continue

        param_raw = match.group("parameter").strip(" .:-_")
        val_raw = match.group("value").strip()
        unit_raw = (match.group("unit") or "").strip()
        ref_raw = (match.group("reference") or "").strip()
        status_raw = match.group("status")

        if not ref_raw and unit_raw:
            embedded_reference = EMBEDDED_REFERENCE_PATTERN.match(unit_raw)
            if embedded_reference:
                unit_raw = embedded_reference.group("unit").strip()
                ref_raw = embedded_reference.group("reference").strip()

        if is_noise_line(param_raw):
            continue

        try:
            value_num = clean_number(val_raw)
        except (ValueError, TypeError):
            continue

        # Ignore if parameter has no unit and no reference range and value is single digit integer (likely page number)
        if not unit_raw and not ref_raw and not status_raw and len(param_raw) < 4:
            continue

        status_result = evaluate_status(value_num, ref_raw, status_raw)

        param_key = param_raw.casefold()
        if param_key in seen_parameters:
            continue
        seen_parameters.add(param_key)

        parameters.append({
            "parameter": param_raw,
            "value": value_num,
            "unit": unit_raw,
            "referenceRange": ref_raw,
            "status": status_result
        })

    logger.info("Successfully parsed %d parameter(s) from report text.", len(parameters))
    return parameters
