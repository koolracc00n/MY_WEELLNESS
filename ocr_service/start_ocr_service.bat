@echo off
title My Wellness - Python AI & OCR Microservice (Port 8000)
cd /d "%~dp0"
echo ==============================================================
echo  MY WELLNESS - PYTHON AI & OCR MICROSERVICE
echo  Member 3 (AI & OCR Integration Developer)
echo  RapidOCR Engine + FastAPI Active on Port 8000
echo ==============================================================
py -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
pause
