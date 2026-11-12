# My Wellness AI & OCR Service (Member 3)

The Python AI & OCR Microservice handles:
1. **Document & Image Processing**: Preprocessing of PDF, JPG, JPEG, PNG, and WebP laboratory reports.
2. **Optical Character Recognition**: Powered by RapidOCR (ONNXRuntime) and Tesseract with line/column reconstruction.
3. **Structured Parameter Extraction**: Accurately extracts biomarker names, numeric results, units, and printed reference ranges without fabricating or hallucinating data.
4. **AI-Powered Clinical Explanation**: Translates test values into simple, evidence-based wellness insights and lifestyle recommendations while strictly adhering to safety guardrails (no disease diagnosis, no medication prescription).
5. **AI Assistant Chat**: Interactive health question answering based on user laboratory parameters.

---

## Architecture & Integration Contract

### 1. OCR Extraction Endpoint
- **URL**: `POST /api/ocr/parse`
- **Content-Type**: `multipart/form-data`
- **Field Name**: `file` (Binary file: PDF, JPG, PNG, WebP)
- **Spring Boot Calling Service**: `Member 2` (`MyWellnessSingleFileBackend$LabReportRestController`)

#### Successful Response (`200 OK`):
```json
{
  "success": true,
  "message": "Successfully extracted 3 laboratory parameter(s) via OCR with AI wellness insights.",
  "data": {
    "reportName": "blood_panel.pdf",
    "parameters": [
      {
        "parameter": "Hemoglobin",
        "value": 14.2,
        "unit": "g/dL",
        "referenceRange": "13.0 - 17.0",
        "status": "NORMAL",
        "description": "An essential protein in your red blood cells that carries oxygen from your lungs throughout your entire body.",
        "interpretation": "Your hemoglobin level is within the healthy reference range, indicating adequate oxygen-carrying capacity in your blood.",
        "wellnessTips": [
          "Include iron-rich foods in your diet, such as spinach, lentils, and beans.",
          "Pair iron sources with vitamin C rich foods to boost absorption."
        ]
      }
    ],
    "aiSummary": {
      "reportName": "blood_panel.pdf",
      "totalParameters": 1,
      "normalCount": 1,
      "outOfRangeCount": 0,
      "highCount": 0,
      "lowCount": 0,
      "keyObservations": "All 1 recognized laboratory parameters are within their standard reference intervals.",
      "wellnessRecommendations": [
        "Maintain daily hydration (2.5L to 3L water per day).",
        "Prioritize 7-8 hours of quality sleep and regular physical movement."
      ],
      "disclaimer": "This AI health insight is for informational and educational wellness purposes only. It does not constitute a medical diagnosis..."
    }
  }
}
```

#### Unrecognized / Empty Document Response (`200 OK` or `422/415`):
```json
{
  "success": false,
  "message": "OCR extracted document text, but no valid clinical laboratory rows (parameter, value, reference interval) were recognized.",
  "data": {
    "reportName": "document.pdf",
    "parameters": [],
    "aiSummary": { ... }
  }
}
```

---

### 2. AI Assistant Endpoint
- **URL**: `POST /api/ai/chat`
- **Content-Type**: `application/json`
- **Payload**:
```json
{
  "question": "What should I eat to improve my hemoglobin?",
  "parameters": [
    {
      "parameter": "Hemoglobin",
      "value": 12.0,
      "unit": "g/dL",
      "referenceRange": "13.0 - 17.0",
      "status": "LOW"
    }
  ]
}
```
- **Response**:
```json
{
  "success": true,
  "message": "AI guidance generated successfully",
  "data": {
    "question": "What should I eat to improve my hemoglobin?",
    "answer": "**Hemoglobin**: An essential protein in your red blood cells...\n\n**Lifestyle Suggestions**:\n• Include iron-rich foods in your diet, such as spinach, lentils, beans, and seeds.\n\nDisclaimer: This AI health insight is for informational and educational wellness purposes only...",
    "disclaimer": "..."
  }
}
```

---

## How to Start the Python Service

### Option A: Using the Launcher Script
Double-click:
```cmd
start_ocr_service.bat
```

### Option B: From Command Line
```powershell
cd "C:\Users\ACER\Downloads\my well 2.0\ocr_service"
py -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```
Check health status in your browser:
`http://localhost:8000/health`
