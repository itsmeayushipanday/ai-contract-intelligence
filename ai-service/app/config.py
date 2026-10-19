"""Settings, read from environment variables or the git-ignored .env file (never hardcoded)."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    service_name: str = "ai-service"
    version: str = "0.1.0"
    log_level: str = "INFO"

    # Limits for untrusted uploads. The size default matches the Java backend's 10 MB upload limit.
    max_file_size_bytes: int = 10 * 1024 * 1024
    max_pages: int = 500

    # The shared .env at the repo root also holds Java/Postgres settings, so unknown keys are ignored.
    # Paths work whether the service is started from ai-service/ or from the repo root.
    model_config = SettingsConfigDict(env_file=("../.env", ".env"), extra="ignore")


@lru_cache
def get_settings() -> Settings:
    """Created once and reused. Tests override it through FastAPI's dependency_overrides."""
    return Settings()
