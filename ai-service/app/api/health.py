from fastapi import APIRouter
from pydantic import BaseModel

from app.config import get_settings

router = APIRouter()


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str


@router.get("/health", response_model=HealthResponse, tags=["health"])
def health() -> HealthResponse:
    """Liveness check: the Java backend (and later Docker) uses this to know the service is up."""
    settings = get_settings()
    return HealthResponse(status="ok", service=settings.service_name, version=settings.version)
