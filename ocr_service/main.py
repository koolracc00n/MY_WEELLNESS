"""
My Wellness - Python OCR & AI Integration Service.
Member 3: AI & OCR Integration Developer.

Provides FastAPI endpoints for:
- PDF and image laboratory report OCR extraction (RapidOCR & Tesseract).
- Laboratory parameter and reference range parsing.
- AI-based clinical parameter explanation and wellness recommendations.
- AI Assistant chat for health report inquiries.
"""

import logging
import os
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import FastAPI, File, HTTPException, UploadFile, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from ocr_engine import (
    extract_text_from_image,
    extract_text_from_pdf,
    get_available_engines,
    is_ocr_available,
)
from parser import parse_lab_parameters
from ai_advisor import (
    DISCLAIMER_TEXT,
    answer_wellness_question,
    generate_parameter_explanation,
    generate_report_ai_summary,
    qwen_status,
)

# Configure structured logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s]: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("ocr_service")

MAX_FILE_SIZE = 10 * 1024 * 1024  # 10 MB
SUPPORTED_EXTENSIONS = {".pdf", ".jpg", ".jpeg", ".png", ".webp"}

app = FastAPI(
    title="My Wellness AI & OCR Service",
    description="Extracts actual laboratory parameters and provides evidence-based wellness explanations.",
    version="2.0.0"
)

# Enable CORS for local development and Spring Boot integration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class ParameterExplanationRequest(BaseModel):
    parameters: List[Dict[str, Any]] = Field(default_factory=list)
    reportName: Optional[str] = "Laboratory Report"


class AiChatRequest(BaseModel):
    question: str = "What should I focus on based on my report?"
    parameters: List[Dict[str, Any]] = Field(default_factory=list)
    userId: Optional[str] = None


@app.get("/health")
@app.get("/")
async def health_check():
    """Health check endpoint displaying available OCR engines and status."""
    engines = get_available_engines()
    return {
        "status": "UP",
        "service": "My Wellness AI & OCR Service",
        "ocrAvailable": is_ocr_available(),
        "availableEngines": engines,
        "qwen": qwen_status(),
        "supportedExtensions": sorted(list(SUPPORTED_EXTENSIONS)),
        "maxFileSizeMB": MAX_FILE_SIZE // (1024 * 1024)
    }


@app.post("/api/ocr/parse")
async def parse_report(file: UploadFile = File(...)):
    """
    Primary OCR & Lab Report Extraction Endpoint.
    Coordinates with Spring Boot backend (Member 2).
    - Accepts: multipart/form-data with file field 'file'.
    - Performs OCR and extracts actual laboratory parameters.
    - Enhances parameters with AI-based plain English explanations and reference range evaluations.
    - Does NOT invent or hardcode missing values.
    """
    report_name = Path(file.filename or "unknown_report").name
    extension = Path(report_name).suffix.lower()

    logger.info("Received report upload: '%s' (ext: '%s')", report_name, extension)

    if extension not in SUPPORTED_EXTENSIONS:
        logger.warning("Unsupported file extension '%s' for '%s'", extension, report_name)
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=f"Unsupported file format '{extension}'. Please upload a PDF, JPG, PNG, or WebP document."
        )

    # Read and validate file content
    content = await file.read(MAX_FILE_SIZE + 1)
    if not content:
        logger.warning("Uploaded file '%s' was empty.", report_name)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="The uploaded report file is empty."
        )
    if len(content) > MAX_FILE_SIZE:
        logger.warning("File '%s' exceeds 10MB limit (%d bytes).", report_name, len(content))
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="Report file size exceeds the 10 MB limit."
        )

    # Extract raw text from PDF or image
    try:
        if extension == ".pdf":
            extracted_text = extract_text_from_pdf(content)
        else:
            extracted_text = extract_text_from_image(content)
    except ValueError as exc:
        logger.error("Failed to decode file '%s': %s", report_name, exc)
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))
    except Exception as exc:
        logger.exception("OCR extraction error on '%s': %s", report_name, exc)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"OCR engine encountered an error while processing the document: {exc}"
        )

    if not extracted_text.strip():
        logger.warning("No readable text could be extracted from '%s'.", report_name)
        return {
            "success": False,
            "message": "OCR completed, but no text could be recognized. Please upload a clearer or higher resolution report.",
            "data": {
                "reportName": report_name,
                "parameters": [],
                "aiSummary": {
                    "totalParameters": 0,
                    "normalCount": 0,
                    "outOfRangeCount": 0,
                    "keyObservations": "Document was unreadable or contained no recognizable text.",
                    "wellnessRecommendations": [],
                    "disclaimer": DISCLAIMER_TEXT
                }
            }
        }

    # Parse laboratory parameters from extracted text
    raw_parameters = parse_lab_parameters(extracted_text)

    if not raw_parameters:
        logger.info("Text was extracted from '%s', but no laboratory result rows were recognized.", report_name)
        return {
            "success": False,
            "message": "OCR extracted document text, but no valid clinical laboratory rows (parameter, value, reference interval) were recognized.",
            "data": {
                "reportName": report_name,
                "parameters": [],
                "aiSummary": {
                    "totalParameters": 0,
                    "normalCount": 0,
                    "outOfRangeCount": 0,
                    "keyObservations": "No structured laboratory result rows detected.",
                    "wellnessRecommendations": [],
                    "disclaimer": DISCLAIMER_TEXT
                }
            }
        }

    # AI Integration: enrich each parameter with plain-English explanation
    enriched_parameters = [generate_parameter_explanation(p) for p in raw_parameters]

    # Generate overarching AI report summary
    ai_summary = generate_report_ai_summary(enriched_parameters, report_name)

    logger.info("Successfully extracted and explained %d parameter(s) from '%s'", len(enriched_parameters), report_name)

    return {
        "success": True,
        "message": f"Successfully extracted {len(enriched_parameters)} laboratory parameter(s) via OCR with AI wellness insights.",
        "data": {
            "reportName": report_name,
            "parameters": enriched_parameters,
            "aiSummary": ai_summary
        }
    }


@app.post("/api/ai/explain")
async def explain_parameters(request: ParameterExplanationRequest):
    """
    AI Parameter Explanation Endpoint.
    Accepts laboratory parameters and returns plain-English interpretations
    and wellness lifestyle suggestions.
    """
    if not request.parameters:
        return {
            "success": False,
            "message": "No parameters provided for explanation.",
            "data": []
        }

    explanations = [generate_parameter_explanation(p) for p in request.parameters]
    summary = generate_report_ai_summary(explanations, request.reportName or "Report")

    return {
        "success": True,
        "message": "AI parameter explanations generated successfully.",
        "data": {
            "parameters": explanations,
            "summary": summary
        }
    }


@app.post("/api/ai/chat")
async def ai_chat(request: AiChatRequest):
    """
    AI Health Assistant Chat Endpoint.
    Answers wellness and lab report questions in simple language,
    respecting clinical reference ranges and medical disclaimers.
    """
    logger.info("Received AI assistant question: '%s'", request.question)
    answer = answer_wellness_question(request.question, request.parameters)

    return {
        "success": True,
        "message": "AI guidance generated successfully",
        "data": {
            "question": request.question,
            "answer": answer,
            "disclaimer": DISCLAIMER_TEXT
        }
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
