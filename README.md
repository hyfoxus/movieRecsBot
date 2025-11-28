# MovieRecBot

Telegram bot for movie recommendations powered by Spring Boot, PostgreSQL, Spring AI, and OpenAI’s GPT‑4o mini model. The bot keeps lightweight user profiles, queues long-running recommendation requests, and delivers replies asynchronously through Telegram webhooks.

---

## Prerequisites

- **Docker** 24+ and **Docker Compose** v2 (recommended workflow)
- **Git** to clone the repository
- Optional (local JVM run): **Java 24** and **Maven 3.9+**

> The Docker setup bundles everything (application, PostgreSQL, and worker queue) and is the easiest way to get started.

---

## Repository Layout

```
movieRecsBot/                Monorepo root
├─ apps/movieRecBot/         Spring Boot application (bot service)
│  ├─ src/                   Java sources & resources
│  └─ .env.sample            Template for bot-specific environment variables
├─ apps/mcp-fastmcp/.env.sample Template for the FastMCP server env vars
├─ docker-compose.yml        Local stack (bot + PostgreSQL + MCP dependencies)
├─ docker/entrypoint.sh      Secret loader used inside the runtime image
├─ scripts/bootstrap_env.py  Helper to generate all .env files at once
├─ secrets/                  Directory for Docker secrets (you create the files)
└─ README.md                 This guide
```

---

## Quick Setup Scripts

Prefer an automated bootstrap? Use the helper scripts inside `scripts/`:

- `scripts/setup_data.sh` – prompts for critical secrets, generates `.env` files, starts the IMDb data containers, and kicks off the download/embedding bootstrap (pass `--use-defaults` or `--skip-bootstrap` as needed).
- `scripts/setup_bot.sh` – verifies the bot/MCP env files and starts the remaining services (`movie-recs-db`, `normalizer`, `imdb-mcp`, `movie-recs-bot`). Add `--build` to force image rebuilds.
- `scripts/setup_everything.sh` – wrapper that runs the two scripts above in sequence for a full-stack setup experience.

You can still run each step manually via Docker commands if you prefer fine-grained control; the scripts simply streamline the happy-path onboarding.

---

## 1. Clone the Repository

```bash
git clone https://github.com/hyfoxus/movieRecsBot.git
cd movieRecsBot
```

---

## 2. Configure Environment Variables

The repo now ships with a helper that populates every `.env` file (bot + MCP) in one go:

```bash
python scripts/bootstrap_env.py
```

You will be prompted once for the values that matter (Telegram webhook URL, OpenAI key, database creds, Ollama endpoint, etc.). The script writes the answers into `apps/movieRecBot/.env` (bot) and `apps/mcp-fastmcp/.env` (MCP) so every service gets what it needs without repeated editing.  
Re-run the script at any time; existing values are used as defaults. Use `--use-defaults` for non-interactive environments.

Prefer editing manually? Copy the provided samples:

```bash
cp apps/movieRecBot/.env.sample apps/movieRecBot/.env
cp apps/mcp-fastmcp/.env.sample apps/mcp-fastmcp/.env
```

### Key variables (bot)

| Variable                   | Description                                                                |
|----------------------------|----------------------------------------------------------------------------|
| `TELEGRAM_WEBHOOK_URL`     | Public HTTPS endpoint Telegram will call (e.g. https://bot.example.com).  |
| `TELEGRAM_BOT_WEBHOOK_PATH`| Path the bot exposes for Telegram updates (default `/tg/webhook`).        |
| `SPRING_PROFILES_ACTIVE`   | Typically `postgres` when running via Docker Compose.                      |
| `POSTGRES_DB/USER/PASSWORD`| Credentials shared by the DB container and the bot.                        |
| `BOT_HTTP_PORT`            | Host port where the bot listens locally (default `8080`).                  |
| `OPENAI_API_KEY`           | OpenAI (or compatible) API key for chat completions.                       |
| `OPENAI_BASE_URL`          | Override if proxying requests (default `https://api.openai.com`).          |
| `OPENAI_MODEL`             | Chat model identifier (default `gpt-4o-mini`).                             |
| `MCP_BASE_URL`             | Internal URL the bot uses to talk to the MCP server (default `http://imdb-mcp:8082`). |

### Key variables (MCP server)

| Variable             | Description                                                                 |
|----------------------|-----------------------------------------------------------------------------|
| `DATABASE_URL`       | SQLAlchemy-style URI for the IMDb vector database (pgvector).               |
| `OLLAMA_BASE_URL`    | URL for the Ollama instance that serves embeddings (default `http://imdb-ollama:11434`). |
| `OLLAMA_EMBED_MODEL` | Embedding model identifier pulled into Ollama (default `nomic-embed-text`). |
| `MCP_MAX_K`          | Maximum number of movie rows returned to the bot per query.                 |

`.env` files are ignored by Git, so it is safe to keep local DB passwords there. Treat the OpenAI key as sensitive—prefer CI/CD secret stores or environment injection when deploying.

---

## 3. Create Docker Secrets

Only the Telegram token is provided via Docker secrets. Create the file inside `secrets/` with **only** the secret value and no trailing newline.

```bash
mkdir -p secrets
printf '%s' 'your-telegram-bot-token'    > secrets/TELEGRAM_BOT_TOKEN
```

Recommendations:
- Store the files securely (password manager, encrypted volume, etc.).
- Add `secrets/` to your `.gitignore` (already done in this repo) so you never commit real credentials.
- If you rotate the token, update the file and restart the stack.

Database credentials now live in `apps/movieRecBot/.env` (see step 2); update that file whenever you change the password.

---

## 4. Run with Docker Compose

From the repository root (`movieRecsBot` directory):

```bash
docker compose up --build
```

What happens:
- `db` service launches PostgreSQL 16 with data persisted in the `dbdata` volume.
- `bot` service builds the Spring Boot application, loads the Telegram token from `/run/secrets/TELEGRAM_BOT_TOKEN`, and starts with the `postgres` profile.
- The webhook controller listens on port `8080`. Expose or tunnel this port publicly so Telegram can deliver updates.

Common commands:

| Action                     | Command                                |
|----------------------------|----------------------------------------|
| View logs                  | `docker compose logs -f`               |
| Stop services              | `docker compose down`                  |
| Stop & remove volumes (DB) | `docker compose down -v`               |
| Rebuild after code change  | `docker compose up --build`            |

> The first run will create database tables automatically because Hibernate is configured to update the schema.

---

## 5. Running Locally without Docker (Optional)

1. Start a PostgreSQL instance and export matching environment variables:
   ```bash
   export DB_HOST=localhost
   export DB_PORT=5432
   export DB_NAME=movie_recs
   export DB_USERNAME=movie_recs
   export DB_PASSWORD=your-password
   export OPENAI_API_KEY=sk-your-token
   export OPENAI_BASE_URL=https://api.openai.com
   export OPENAI_MODEL=gpt-4o-mini
   export TELEGRAM_BOT_TOKEN=...
   export SPRING_PROFILES_ACTIVE=postgres
   ```
2. Build and run the app:
   ```bash
   ./mvnw spring-boot:run
   ```

Ensure Telegram can reach `http://localhost:8080/<TELEGRAM_BOT_WEBHOOK_PATH>` by exposing it publicly (reverse proxy, cloud ingress, or a tunnelling tool like ngrok).

---

## 6. Testing & Maintenance

- Run tests:
  ```bash
  ./mvnw test
  ```
- Database backups: use `docker exec -t <db-container> pg_dump ...` or your preferred tooling.
- Queue status: inside Telegram, run `/status` to see in-flight jobs. Each job is identified by a random short ID (e.g. `DK72MZ`).

---

## Troubleshooting

| Symptom                                      | Possible Fix                                                                                         |
|----------------------------------------------|-------------------------------------------------------------------------------------------------------|
| Bot replies “Очередь пуста.” but no response | Check `docker compose logs bot` for errors communicating with Telegram or the OpenAI API.            |
| Database connection failures                 | Confirm `POSTGRES_PASSWORD` in `apps/movieRecBot/.env` matches the password stored inside PostgreSQL (alter it or recreate the volume if needed). |
| Webhook warnings on startup                  | Ensure `TELEGRAM_WEBHOOK_URL` (and public endpoint) is set; rerun if the URL changes.                 |
| Stuck tasks in `/status`                     | Restart the stack—pending jobs resume on boot and completed jobs are purged automatically.           |

---

## Security Notes

- Store the Telegram token as a Docker secret; keep the OpenAI API key outside version control (secret store, env vars, vault).
- Rotate API keys regularly and update the corresponding secret material.
- When deploying to production, use your orchestrator’s secret store (Docker Swarm, Kubernetes, or CI/CD vault) instead of plaintext files.

---

## IMDb Data Enrichment

The sibling `apps/imdb-vec` service now hydrates its `movie` catalog exclusively from `title.basics` and `title.ratings`.
Those two TSVs are small enough to download quickly yet still provide the essentials (titles, years, genres, runtime,
adult flag, ratings, votes) that power the recommendation pipeline. Since we dropped the massive `akas`/`episode`
datasets, their columns were removed from the schema to keep everything lean and consistent across services.
