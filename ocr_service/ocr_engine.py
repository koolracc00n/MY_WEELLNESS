"""
OCR Engine Module for My Wellness OCR Service.
Integrates RapidOCR (ONNXRuntime) and Tesseract with line reconstruction,
supporting both digital (embedded text) and scanned PDFs, as well as images.
"""

import io
import logging
import os
import re
from typing import List, Optional, Tuple
import numpy as np
from PIL import Image
import pymupdf as fitz

from preprocessing import preprocess_image, load_image_from_bytes, render_pdf_page_to_image

logger = logging.getLogger("ocr_service.ocr_engine")

# Initialize RapidOCR engine
_RAPID_OCR_INSTANCE = None
try:
    from rapidocr_onnxruntime import RapidOCR
    _RAPID_OCR_INSTANCE = RapidOCR()
    logger.info("RapidOCR (ONNXRuntime) initialized successfully.")
except Exception as exc:
    logger.warning("RapidOCR could not be initialized: %s", exc)

# Initialize Tesseract if configured
_TESSERACT_AVAILABLE = False
try:
    import pytesseract
    if os.getenv("TESSERACT_CMD"):
        pytesseract.pytesseract.tesseract_cmd = os.environ["TESSERACT_CMD"]
    # Check if tesseract is callable
    try:
        pytesseract.get_tesseract_version()
        _TESSERACT_AVAILABLE = True
        logger.info("Tesseract OCR is available.")
    except Exception:
        _TESSERACT_AVAILABLE = False
        logger.info("Tesseract binary not found on PATH or TESSERACT_CMD.")
except ImportError:
    pass


def is_ocr_available() -> bool:
    """Returns True if at least one OCR engine (RapidOCR or Tesseract) is ready."""
    return _RAPID_OCR_INSTANCE is not None or _TESSERACT_AVAILABLE


def get_available_engines() -> List[str]:
    engines = []
    if _RAPID_OCR_INSTANCE is not None:
        engines.append("RapidOCR (ONNXRuntime)")
    if _TESSERACT_AVAILABLE:
        engines.append("Tesseract")
    return engines


def _ocr_with_rapidocr(image: Image.Image) -> str:
    """
    Performs OCR using RapidOCR and clusters detected words/boxes into horizontal lines.
    """
    if _RAPID_OCR_INSTANCE is None:
        return ""

    img_np = np.array(image)
    result, _ = _RAPID_OCR_INSTANCE(img_np)
    if not result:
        return ""

    # Each entry in result: [box_points, text, confidence]
    # box_points: [[x1,y1], [x2,y2], [x3,y3], [x4,y4]]
    boxes = []
    for item in result:
        if not item or len(item) < 2:
            continue
        pts = item[0]
        text = str(item[1]).strip()
        confidence = float(item[2]) if len(item) > 2 else 1.0

        if not text or confidence < 0.25:
            continue

        min_y = min(p[1] for p in pts)
        max_y = max(p[1] for p in pts)
        min_x = min(p[0] for p in pts)
        height = max_y - min_y
        center_y = (min_y + max_y) / 2.0

        boxes.append({
            "text": text,
            "min_x": min_x,
            "min_y": min_y,
            "max_y": max_y,
            "center_y": center_y,
            "height": max(height, 8.0)
        })

    if not boxes:
        return ""

    # Sort boxes top-to-bottom
    boxes.sort(key=lambda b: b["center_y"])

    # Cluster into lines based on vertical overlap
    lines = []
    current_line = []
    current_line_y = None
    current_line_h = None

    for box in boxes:
        if not current_line:
            current_line.append(box)
            current_line_y = box["center_y"]
            current_line_h = box["height"]
        else:
            y_diff = abs(box["center_y"] - current_line_y)
            threshold = max(current_line_h * 0.65, 12.0)
            if y_diff <= threshold:
                current_line.append(box)
                # Update rolling center
                current_line_y = sum(b["center_y"] for b in current_line) / len(current_line)
                current_line_h = max(b["height"] for b in current_line)
            else:
                # Finish line: sort left-to-right
                current_line.sort(key=lambda b: b["min_x"])
                line_text = "  ".join(b["text"] for b in current_line)
                lines.append(line_text)
                current_line = [box]
                current_line_y = box["center_y"]
                current_line_h = box["height"]

    if current_line:
        current_line.sort(key=lambda b: b["min_x"])
        lines.append("  ".join(b["text"] for b in current_line))

    return "\n".join(lines)


def _ocr_with_tesseract(image: Image.Image) -> str:
    """
    Fallback OCR using pytesseract if installed.
    """
    if not _TESSERACT_AVAILABLE:
        return ""
    try:
        import pytesseract
        return pytesseract.image_to_string(image, config="--psm 6")
    except Exception as exc:
        logger.warning("Tesseract execution failed: %s", exc)
        return ""


def extract_text_from_image(content: bytes) -> str:
    """
    Decodes an image from bytes, applies preprocessing, and extracts text.
    """
    image = load_image_from_bytes(content)
    processed = preprocess_image(image)

    # Try RapidOCR first
    if _RAPID_OCR_INSTANCE is not None:
        text = _ocr_with_rapidocr(processed)
        if text.strip():
            return text

    # Fallback to Tesseract
    if _TESSERACT_AVAILABLE:
        text = _ocr_with_tesseract(processed)
        if text.strip():
            return text

    if not is_ocr_available():
        raise RuntimeError(
            "No OCR engine is currently available. Please ensure rapidocr_onnxruntime is installed "
            "or Tesseract OCR is configured."
        )

    return ""


def extract_text_from_pdf(content: bytes) -> str:
    """
    Extracts text from PDF documents.
    1. First attempts digital text extraction from PyMuPDF.
    2. If a page contains minimal or no text (scanned page), renders page to image and runs OCR.
    """
    try:
        document = fitz.open(stream=content, filetype="pdf")
    except Exception as exc:
        raise ValueError(f"Uploaded file is not a readable or valid PDF: {exc}") from exc

    extracted_pages = []
    with document:
        total_pages = len(document)
        logger.info("Processing PDF document with %d page(s)", total_pages)

        for page_num in range(total_pages):
            page = document[page_num]
            # Try native embedded text extraction
            text = page.get_text("text").strip()

            # If embedded text is sparse (e.g. scanned image placed in PDF), rasterize and run OCR
            if len(text) < 40 and is_ocr_available():
                logger.info("Page %d has insufficient native text (%d chars); running OCR rasterization.", page_num + 1, len(text))
                page_img = render_pdf_page_to_image(page, dpi=200)
                ocr_text = _ocr_with_rapidocr(page_img) if _RAPID_OCR_INSTANCE else ""
                if not ocr_text.strip() and _TESSERACT_AVAILABLE:
                    ocr_text = _ocr_with_tesseract(page_img)
                if ocr_text.strip():
                    text = ocr_text

            if text.strip():
                extracted_pages.append(text)

    return "\n\n".join(extracted_pages)
