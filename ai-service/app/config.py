"""Settings, read from environment variables or the git-ignored .env file (never hardcoded)."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    service_name: str = "ai-service"
    version: str = "0.1.0"
    log_level: str = "INFO"

    # The shared .env at the repo root also holds Java/Postgres settings, so unknown keys are ignored.
    # Paths work whether the service is started from ai-service/ or from the repo root.
    model_config = SettingsConfigDict(env_file=("../.env", ".env"), extra="ignore")


@lru_cache
def get_settings() -> Settings:
    """Created once and reused. Tests can clear the cache to pick up different settings."""
    return Settings()
