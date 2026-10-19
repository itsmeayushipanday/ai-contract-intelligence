import logging

from fastapi import FastAPI

from app.api import extract, health
from app.config import get_settings
from app.errors import register_error_handlers


def create_app() -> FastAPI:
    """App factory: builds a fresh app, which keeps tests simple and avoids import-time side effects."""
    settings = get_settings()
    logging.basicConfig(level=settings.log_level.upper(), format="%(asctime)s %(levelname)s %(name)s: %(message)s")

    app = FastAPI(
        title="AI Contract Intelligence - AI Service",
        version=settings.version,
        description="PDF processing and AI features. Called by the Java backend, not by end users.",
    )
    register_error_handlers(app)
    app.include_router(health.router)
    app.include_router(extract.router)
    return app


# uvicorn starts the app through this name: `uvicorn app.main:app`
app = create_app()
