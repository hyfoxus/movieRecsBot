#!/usr/bin/env python3
"""
Interactive helper that creates/updates all runtime .env files used by the monorepo.

Run:
    python scripts/bootstrap_env.py            # prompts for values
    python scripts/bootstrap_env.py --use-defaults   # accept existing values/defaults without prompting
"""

from __future__ import annotations

import argparse
import getpass
from pathlib import Path
import textwrap
from typing import Dict, List

ROOT = Path(__file__).resolve().parent.parent


class EnvEntry(dict):
    key: str
    prompt: str
    default: str
    required: bool
    secret: bool
    share: str


ENV_SPECS: List[dict] = [
    {
        "path": ROOT / "apps/movieRecBot/.env",
        "header": """
            Runtime variables for the Spring Boot Telegram bot.
            Copy of apps/movieRecBot/.env.sample plus your secrets.
        """,
        "entries": [
            {
                "key": "TELEGRAM_WEBHOOK_URL",
                "prompt": "Public HTTPS URL that Telegram will hit",
                "default": "",
                "required": True,
            },
            {
                "key": "TELEGRAM_BOT_WEBHOOK_PATH",
                "prompt": "Webhook path relative to the server root",
                "default": "/tg/webhook",
            },
            {
                "key": "SPRING_PROFILES_ACTIVE",
                "prompt": "Spring profile",
                "default": "postgres",
            },
            {
                "key": "POSTGRES_DB",
                "prompt": "PostgreSQL database name",
                "default": "botdb",
            },
            {
                "key": "POSTGRES_USER",
                "prompt": "PostgreSQL username",
                "default": "botuser",
            },
            {
                "key": "POSTGRES_PASSWORD",
                "prompt": "PostgreSQL password",
                "default": "movie_recs",
            },
            {
                "key": "POSTGRES_PORT",
                "prompt": "PostgreSQL port exposed on host",
                "default": "5432",
            },
            {
                "key": "BOT_HTTP_PORT",
                "prompt": "Bot HTTP port exposed on host",
                "default": "8080",
            },
            {
                "key": "OPENAI_API_KEY",
                "prompt": "OpenAI API key (used by the bot and MCP)",
                "default": "",
                "required": True,
                "secret": True,
                "share": "openai_api_key",
            },
            {
                "key": "OPENAI_BASE_URL",
                "prompt": "OpenAI-compatible base URL",
                "default": "https://api.openai.com",
                "share": "openai_base_url",
            },
            {
                "key": "OPENAI_MODEL",
                "prompt": "Chat model name",
                "default": "gpt-4o-mini",
            },
            {
                "key": "MCP_BASE_URL",
                "prompt": "Internal URL the bot uses to reach the MCP server",
                "default": "http://imdb-mcp:8082",
            },
        ],
    },
    {
        "path": ROOT / "apps/mcp-fastmcp/.env",
        "header": """
            FastMCP server configuration (embeddings + movie search).
            Required for the imdb-mcp service in docker-compose.
        """,
        "entries": [
            {
                "key": "DATABASE_URL",
                "prompt": "PostgreSQL connection string for the IMDb vector DB",
                "default": "postgresql+psycopg://imdb:changeme@imdb-postgres:5432/imdb",
                "required": True,
            },
            {
                "key": "OLLAMA_BASE_URL",
                "prompt": "Ollama base URL reachable from imdb-mcp",
                "default": "http://imdb-ollama:11434",
            },
            {
                "key": "OLLAMA_EMBED_MODEL",
                "prompt": "Ollama embedding model name",
                "default": "nomic-embed-text",
            },
            {
                "key": "MCP_MAX_K",
                "prompt": "Maximum number of search results to return",
                "default": "15",
            },
        ],
    },
]


def load_env(path: Path) -> Dict[str, str]:
    data: Dict[str, str] = {}
    if not path.exists():
        return data
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key.strip()] = value
    return data


def prompt_value(spec: dict, default: str, args) -> str:
    if args.use_defaults:
        return default

    prompt = spec.get("prompt", spec["key"])
    suffix = f" [{default}]" if default else ""
    question = f"{prompt}{suffix}: "

    while True:
        if spec.get("secret"):
            value = getpass.getpass(question)
        else:
            value = input(question)
        value = value.strip()
        if not value:
            value = default
        if spec.get("required") and not value:
            print("Value is required. Please provide a value.")
            continue
        return value


def write_env(path: Path, header: str, entries: List[dict], values: Dict[str, str]) -> None:
    lines = [
        "# This file was generated by scripts/bootstrap_env.py",
    ]
    header = textwrap.dedent(header).strip()
    if header:
        for line in header.splitlines():
            lines.append(f"# {line}")
    lines.append("")
    for entry in entries:
        key = entry["key"]
        lines.append(f"{key}={values.get(key, '')}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines).rstrip() + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="Create/update .env files across the repo.")
    parser.add_argument(
        "--use-defaults",
        action="store_true",
        help="Skip prompts and reuse existing values or defaults.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show the resulting files without writing them.",
    )
    args = parser.parse_args()

    shared_answers: Dict[str, str] = {}
    rendered_files = []

    for spec in ENV_SPECS:
        path: Path = spec["path"]
        existing = load_env(path)
        values: Dict[str, str] = {}
        for entry in spec["entries"]:
            share_key = entry.get("share")

            default = existing.get(entry["key"])
            if not default and share_key:
                default = shared_answers.get(share_key, "")
            if not default:
                default = entry.get("default", "")

            value = prompt_value(entry, default, args)

            if share_key and value:
                shared_answers[share_key] = value

            values[entry["key"]] = value

        rendered_files.append((path, spec.get("header", ""), spec["entries"], values))

    for path, header, entries, values in rendered_files:
        if args.dry_run:
            print(f"\n--- {path} (preview) ---")
            for line in textwrap.dedent(header or "").strip().splitlines():
                print(f"# {line}")
            for entry in entries:
                key = entry["key"]
                print(f"{key}={values.get(key, '')}")
        else:
            write_env(path, header, entries, values)
            print(f"Wrote {path.relative_to(ROOT)}")

    if args.dry_run:
        print("\nDry run complete — nothing was written.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
