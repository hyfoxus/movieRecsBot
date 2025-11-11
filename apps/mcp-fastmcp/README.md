# Movie MCP Server (Python)

FastMCP + FastAPI service that exposes the IMDb vector database through Model Context Protocol endpoints.

## Features

- `movie.search` tool embeds natural language queries with OpenAI, runs pgvector KNN search, and returns structured context blocks.
- `imdb://movie/{tconst}` resource fetches the matching movie row and ships metadata to the LLM.
- SQLAlchemy models reuse the same `movie` table schema as the Java services.

## Running Locally

```bash
cd apps/mcp-fastmcp
python3 -m venv .venv
source .venv/bin/activate
pip install -e .
DATABASE_URL=postgresql+psycopg://imdb:changeme@localhost:5432/imdb \
OPENAI_API_KEY=sk-... \
uvicorn mcpmovie.main:app --reload --port 8082
```

## Configuration

Environment variables:

| Name | Description |
|------|-------------|
| `DATABASE_URL` | SQLAlchemy URL for the IMDb pgvector database (e.g. `postgresql+psycopg://user:pass@host:5432/imdb`). |
| `OPENAI_API_KEY` | API key with access to the embedding model. |
| `OPENAI_EMBED_MODEL` | Optional override for embeddings (default `text-embedding-3-small`). |
| `MCP_MAX_K` | Upper bound for search results (default `15`). |

The top-level `docker-compose.yml` defines an `imdb-mcp` Python service wired to the existing Postgres container.
