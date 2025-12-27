# Movie MCP Server (Spring Boot)

Spring Boot service that exposes the IMDb vector database through Model Context Protocol endpoints. It uses Spring AI + Ollama for embeddings and reproduces the `movie.search` tool and `imdb://movie/{tconst}` resource that the Telegram bot consumes.

## Features

- `movie.search` tool embeds the natural language query with Spring AI/Ollama, applies optional filters, and runs pgvector KNN search.
- `imdb://movie/{tconst}` resource fetches metadata plus the top actors for direct grounding.
- `/\.well-known/mcp.json` manifest advertises the tool/resource schema so MCP clients can auto-discover capabilities.
- Uses the same PostgreSQL schema as the Java services (`movie`, `movie_principal`, `person`).

## Local Development

```bash
cd apps/mcp-fastmcp
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="\
    -DSPRING_DATASOURCE_URL=jdbc:postgresql://localhost:25432/imdb \
    -DSPRING_DATASOURCE_USERNAME=imdb \
    -DSPRING_DATASOURCE_PASSWORD=changeme \
    -DSPRING_AI_OLLAMA_BASE_URL=http://localhost:11434 \
    -DSPRING_AI_OLLAMA_EMBEDDING_MODEL=nomic-embed-text"
```

## Configuration

Environment variables / `application.yml` keys:

| Name | Description | Default |
|------|-------------|---------|
| `SPRING_DATASOURCE_URL` | JDBC URL for the IMDb pgvector database. | `jdbc:postgresql://localhost:25432/imdb` |
| `SPRING_DATASOURCE_USERNAME` | Database user. | `imdb` |
| `SPRING_DATASOURCE_PASSWORD` | Database password. | `changeme` |
| `SPRING_AI_OLLAMA_BASE_URL` | Ollama endpoint reachable by the service. | `http://localhost:11434` |
| `SPRING_AI_OLLAMA_EMBEDDING_MODEL` | Embedding model to call through Spring AI. | `nomic-embed-text` |
| `APP_MCP_MAX_RESULTS` | Maximum results exposed by `movie.search`. | `15` |
| `APP_MCP_NAME` / `APP_MCP_VERSION` / `APP_MCP_DESCRIPTION` | Metadata propagated to the manifest. | `Movie Recommendations MCP` etc. |

Copy `.env.sample` to `.env` if you want `docker-compose` and helper scripts to reuse a single config file:

```bash
cp apps/mcp-fastmcp/.env.sample apps/mcp-fastmcp/.env
```

## Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `GET`  | `/.well-known/mcp.json` | MCP manifest + schema |
| `POST` | `/mcp/v1/tools` | Invoke `movie.search` |
| `POST` | `/mcp/v1/resources/query` | Resolve `imdb://movie/{tconst}` pointers |

## Docker

The provided `Dockerfile` builds a slim JRE image. `docker-compose.yml` wires this service to the IMDb Postgres + Ollama stack under the `imdb-mcp` service name. Build with `docker compose build imdb-mcp` or use `scripts/setup_bot.sh --build`.
