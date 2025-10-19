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
movieRecBot/           Spring Boot application
├─ src/                Java sources
├─ docker-compose.yml  Local stack (bot + PostgreSQL)
├─ docker/entrypoint.shSecret loader used in the runtime image
├─ secrets/            Directory for Docker secrets (you create the files)
├─ .env.sample         Template for non-secret environment settings
└─ README.md           This guide
```

---

## 1. Clone the Repository

```bash
git clone https://github.com/hyfoxus/movieRecsBot.git
cd movieRecsBot/movieRecBot
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
   | `TELEGRAM_BOT_USERNAME`    | The public username of your Telegram bot (without the leading `@`).       |
   | `TELEGRAM_WEBHOOK_URL`     | Public HTTPS endpoint that Telegram should call (e.g. https://bot.example.com). |
   | `TELEGRAM_BOT_WEBHOOK_PATH`| Path your server exposes for Telegram updates (default `/tg/webhook`).    |
   | `SPRING_PROFILES_ACTIVE`   | Recommended `postgres` when running with Docker Compose.                  |

   Keep the secret-related keys (`OPENAI_API_KEY`, `TELEGRAM_BOT_TOKEN`) empty in `.env`—they will be supplied via Docker secrets instead.

---

## 3. Create Docker Secrets

Docker Compose expects three files inside `movieRecBot/secrets/`. Each file must contain **only** the secret value with no extra whitespace.

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

From the `movieRecBot` directory:

```bash
docker compose -f movieRecBot/docker-compose.yml up -d --build
```
