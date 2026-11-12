@echo off
title My Wellness - Start All Services
echo =======================================================
echo Starting My Wellness Services...
echo =======================================================
start "My Wellness Backend (8080)" "%~dp0start_backend.bat"
start "My Wellness OCR Service (8000)" "%~dp0start_ocr_service.bat"
start "My Wellness Web UI (5500)" /D "%~dp0" py -m http.server 5500 --bind 127.0.0.1
echo Backend, OCR, and web UI are starting in separate windows.
echo MongoDB and Ollama must already be running.
timeout /t 3 >nul
start "" "http://localhost:5500/proj.html"
