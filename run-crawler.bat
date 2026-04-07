@echo off
echo ========================================================
echo   Resumade Jasoseol Python Crawler Server Starting...
echo ========================================================

cd Crawler
if not exist "venv\Scripts\activate.bat" (
    echo [ERROR] Virtual environment not found! Please run 'python -m venv venv' inside Crawler folder.
    pause
    exit /b
)

call venv\Scripts\activate.bat
echo [INFO] Virtual environment activated!
echo [INFO] Starting FastAPI Uvicorn Server on port 8000...

uvicorn main:app --reload --port 8000

pause
