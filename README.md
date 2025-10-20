# MovieRecBot

Telegram bot for movie recommendations powered by Spring Boot, PostgreSQL, and Spring AI. The bot keeps lightweight user profiles, queues long-running recommendation requests, and delivers replies asynchronously through Telegram webhooks.

---

## Prerequisites

- **Docker** 24+ and **Docker Compose** v2 (recommended workflow)
- **Git** to clone the repository
- Optional (local JVM run): **Java 24** and **Maven 3.9+**

> The Docker setup bundles everything (application, PostgreSQL, and worker queue) and is the easiest way to get started.

---

## Repository Layout

```
movieRecsBot/          Spring Boot application root
├─ src/                Java sources & resources
├─ docker-compose.yml  Local stack (bot + PostgreSQL)
├─ docker/entrypoint.shSecret loader used inside the runtime image
├─ secrets/            Directory for Docker secrets (you create the files)
├─ .env.sample         Template for non-secret environment settings
└─ README.md           This guide
```

---

## 1. Clone the Repository

```bash
git clone https://github.com/hyfoxus/movieRecsBot.git
cd movieRecsBot
```

---

## 2. Configure Environment Variables

1. Copy the environment template:
   ```bash
   cp .env.sample .env
   ```
2. Edit `.env` and fill in the non-sensitive values:

   | Variable                   | Description                                                                |
   |----------------------------|----------------------------------------------------------------------------|
   | `TELEGRAM_WEBHOOK_URL`     | Public HTTPS endpoint that Telegram should call (e.g. https://bot.example.com). |
   | `TELEGRAM_BOT_WEBHOOK_PATH`| Path your server exposes for Telegram updates (default `/tg/webhook`).    |
   | `SPRING_PROFILES_ACTIVE`   | Recommended `postgres` when running with Docker Compose.                  |

   API keys and database passwords are injected via Docker secrets, so keep them out of `.env`.

---

## 3. Create Docker Secrets

Docker Compose expects three files inside `secrets/`. Each file must contain **only** the secret value with no extra whitespace.

```bash
mkdir -p secrets
printf '%s' 'your-openai-api-key'        > secrets/OPEN_API_KEY
printf '%s' 'your-telegram-bot-token'    > secrets/TELEGRAM_BOT_TOKEN
printf '%s' 'strong-postgres-password'   > secrets/POSTGRES_PASSWORD
```

Recommendations:
- Store the files securely (password manager, encrypted volume, etc.).
- Add `secrets/` to your `.gitignore` (already done in this repo) so you never commit real credentials.
- If you rotate any secret, update the corresponding file and restart the stack.

---

## 4. Run with Docker Compose

From the repository root (`movieRecsBot` directory):

```bash
docker compose up --build
```

What happens:
- `db` service launches PostgreSQL 16 with data persisted in the `dbdata` volume.
- `bot` service builds the Spring Boot application, loads secrets from `/run/secrets/*`, and starts with the `postgres` profile.
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
   export OPENAI_API_KEY=... # avoid committing
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
| Database connection failures                 | Confirm the password in `secrets/POSTGRES_PASSWORD` matches the one supplied to the DB service.      |
| Webhook warnings on startup                  | Ensure `TELEGRAM_WEBHOOK_URL` (and public endpoint) is set; rerun if the URL changes.                 |
| Stuck tasks in `/status`                     | Restart the stack—pending jobs resume on boot and completed jobs are purged automatically.           |

---

## Security Notes

- Secrets live outside Git and are mounted into containers via Docker secrets.
- Rotate API keys regularly and update the corresponding secret files.
- When deploying to production, use your orchestrator’s secret store (Docker Swarm, Kubernetes, or CI/CD vault) instead of plaintext files.

---

Happy hacking and enjoy the movie recommendations!
