# My Wellness: local development setup

This project contains a static web UI, a Spring Boot API, and a FastAPI service that reads lab reports. The OCR service extracts text and laboratory values from uploaded PDF/JPG/PNG/WebP files. It can use a local Qwen model through Ollama to add plain-language report guidance and answer questions about extracted results.

The service does not diagnose disease or prescribe treatment. OCR can misread a report, so users should check extracted values against the original and discuss abnormal results with a licensed healthcare professional.

## Requirements

- Java 17 or newer
- Maven 3.8+
- Python 3.11 (recommended for the OCR dependencies)
- MongoDB running on `localhost:27017`
- Ollama with the Qwen model pulled (optional; without it, built-in guidance is used)
- Tesseract OCR is optional; RapidOCR is the primary OCR engine

## One-time setup

### Python OCR service

Linux/macOS:

```sh
cd ocr_service
python3.11 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

Windows PowerShell:

```powershell
cd ocr_service
py -3.11 -m venv .venv
.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

### Qwen through Ollama (optional)

Install and start Ollama, then pull the default model:

```sh
ollama pull qwen2.5:7b
```

The service uses `http://127.0.0.1:11434` and `qwen2.5:7b` by default. Set `OLLAMA_URL` or `QWEN_MODEL` before starting the OCR service to use another local Ollama endpoint or model. The app processes report images with OCR first; Qwen receives the extracted values and does not directly interpret the original image.

## Start the app

Start MongoDB and Ollama first. In separate terminals, from the project root:

```sh
# Terminal 1: OCR and AI API
cd ocr_service
.venv/bin/python -m uvicorn main:app --host 127.0.0.1 --port 8000

# Terminal 2: Spring Boot backend
mvn spring-boot:run

# Terminal 3: serve the frontend (open http://localhost:5500/proj.html)
python3 -m http.server 5500 --bind 127.0.0.1
```

On Windows, use the existing `start_ocr_service.bat` and `start_backend.bat` after setup, and serve the project folder with `py -m http.server 5500`. Open `http://localhost:5500/proj.html` rather than opening the HTML file directly. The `start_all.bat` launcher starts the Java, OCR, and web services; MongoDB and Ollama still need to be running.

Check `http://localhost:8000/health` for OCR engine and Qwen availability. The backend is on port 8080 and the OCR service is on port 8000.

For local development, the backend generates a random JWT signing key when `JWT_SECRET` is unset. Tokens will need to be refreshed after a backend restart. Set a private `JWT_SECRET` environment variable if you want a stable key across restarts.

## Configuration

Python model settings:

- `OLLAMA_URL` (default `http://127.0.0.1:11434`)
- `QWEN_MODEL` (default `qwen2.5:7b`)
- `QWEN_TIMEOUT_SECONDS` (default `45`)

Spring Boot's OCR endpoint is configured by `ocr.service.url` in `src/main/resources/application.properties`.

## Main endpoints

- `POST /api/ocr/parse`: multipart upload with field `file`
- `POST /api/ai/chat`: JSON question and extracted parameters
- `GET /health`: OCR engine and local Qwen availability

