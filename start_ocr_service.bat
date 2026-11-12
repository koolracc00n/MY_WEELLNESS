@echo off
title My Wellness - Python AI & OCR Microservice (Port 8000)
cd /d "%~dp0"
if exist "ocr_service" cd "ocr_service"
if not exist ".venv\Scripts\python.exe" (
  echo Python environment is missing. Run setup_ocr_service.bat first.
  pause
  exit /b 1
)
echo ==============================================================
echo  MY WELLNESS - PYTHON AI & OCR MICROSERVICE
echo  Member 3 (AI & OCR Integration Developer)
echo  RapidOCR Engine + FastAPI Active on Port 8000
echo ==============================================================
".venv\Scripts\python.exe" -m uvicorn main:app --host 127.0.0.1 --port 8000 --reload
pause
