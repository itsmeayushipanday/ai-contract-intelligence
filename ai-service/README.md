# AI Service

Python (FastAPI) service for PDF processing and AI features. It is called by the Java backend, not by end users.

## Setup (once)
```
cd ai-service
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
```
(Calling `.venv\Scripts\python.exe` directly avoids the PowerShell script-activation restriction.)

## Run
```
cd ai-service
.venv\Scripts\python.exe -m uvicorn app.main:app --port 8000 --reload
```
- Health: http://localhost:8000/health
- Interactive docs: http://localhost:8000/docs

## Test
```
cd ai-service
.venv\Scripts\python.exe -m pytest
```

## Layout
- `app/main.py`: builds the FastAPI app (`create_app`)
- `app/config.py`: settings from environment variables / the root `.env`
- `app/api/`: HTTP routes
- `tests/`: pytest tests
