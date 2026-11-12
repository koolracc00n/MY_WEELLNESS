"""
Image and PDF Preprocessing Module for My Wellness OCR Service.
Handles image enhancement, contrast adjustment, noise reduction,
and high-resolution rendering of scanned PDF pages.
"""

import io
import logging
from typing import List, Tuple
from PIL import Image, ImageEnhance, ImageFilter, ImageOps, UnidentifiedImageError
import pymupdf as fitz

logger = logging.getLogger("ocr_service.preprocessing")


def preprocess_image(image: Image.Image) -> Image.Image:
    """
    Applies image preprocessing to improve OCR accuracy on scanned/photographed reports:
    1. EXIF orientation correction
    2. Conversion to RGB/Grayscale
    3. Contrast enhancement
    4. Subtle sharpening to enhance character edges
    """
    try:
        # Correct rotation based on EXIF tags if present
        image = ImageOps.exif_transpose(image)
    except Exception as exc:
        logger.debug("EXIF transposition skipped: %s", exc)

    # Convert to RGB if RGBA or indexed palette
    if image.mode in ("RGBA", "LA", "P"):
        background = Image.new("RGB", image.size, (255, 255, 255))
        if image.mode == "P":
            image = image.convert("RGBA")
        background.paste(image, mask=image.split()[-1] if "A" in image.mode else None)
        image = background
    elif image.mode != "RGB":
        image = image.convert("RGB")

    # Resize if image is excessively small
    width, height = image.size
    if width < 1000 or height < 1000:
        scale = max(1000 / width, 1000 / height)
        if scale > 1.0 and scale <= 3.0:
            new_size = (int(width * scale), int(height * scale))
            image = image.resize(new_size, Image.Resampling.LANCZOS)

    # Contrast enhancement for legible text
    try:
        enhancer = ImageEnhance.Contrast(image)
        image = enhancer.enhance(1.4)
    except Exception as exc:
        logger.debug("Contrast enhancement skipped: %s", exc)

    return image


def load_image_from_bytes(content: bytes) -> Image.Image:
    """
    Decodes raw bytes into a PIL Image with verification.
    """
    try:
        with Image.open(io.BytesIO(content)) as raw_img:
            raw_img.verify()
        with Image.open(io.BytesIO(content)) as raw_img:
            return raw_img.copy()
    except UnidentifiedImageError as exc:
        raise ValueError("The uploaded file is not a valid or readable image.") from exc
    except Exception as exc:
        raise ValueError(f"Failed to decode image: {exc}") from exc


def render_pdf_page_to_image(page: fitz.Page, dpi: int = 200) -> Image.Image:
    """
    Renders a PyMuPDF PDF page into a high-resolution PIL Image for OCR.
    """
    zoom = dpi / 72.0
    matrix = fitz.Matrix(zoom, zoom)
    pix = page.get_pixmap(matrix=matrix, alpha=False)
    image = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
    return preprocess_image(image)
