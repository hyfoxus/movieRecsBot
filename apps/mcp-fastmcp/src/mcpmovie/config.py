import os
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    database_url: str
    ollama_base_url: str = "http://imdb-ollama:11434"
    ollama_embed_model: str = "nomic-embed-text"
    max_results: int = 15
    server_name: str = "Movie Recommendations MCP"
    server_version: str = "1.0.0"
    server_description: str = (
        "Provides pgvector-backed movie search and metadata for movieRecBot."
    )

    model_config = SettingsConfigDict(
        env_file=os.getenv("MCP_ENV_FILE", ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
