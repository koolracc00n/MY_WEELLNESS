@echo off
title My Wellness - OCR Service Setup
cd /d "%~dp0ocr_service"
py -3.11 -m venv .venv
if errorlevel 1 (
  echo Python 3.11 was not found. Install Python 3.11 and try again.
  pause
  exit /b 1
)
.venv\Scripts\python.exe -m pip install --upgrade pip
.venv\Scripts\python.exe -m pip install -r requirements.txt
if errorlevel 1 (
  echo Dependency installation failed. Check your internet connection and Python version.
  pause
  exit /b 1
)
echo OCR service setup complete.
pause
