#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/setup_bot.sh [options]

Finishes the bot setup by verifying required env/secret files and starting all
runtime services (database, normalizer, IMDb stack, MCP, and the bot itself).

Options:
  --build        Rebuild service images before starting containers.
  -h, --help     Show this help message.

Environment overrides:
  TELEGRAM_BOT_TOKEN  Provide/override the Telegram secret non-interactively.
EOF
}

info() {
  echo "[setup-bot] $*"
}

fatal() {
  echo "[setup-bot] ERROR: $*" >&2
  exit 1
}

ensure_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fatal "Required command '$1' not found. Please install it and retry."
  fi
}

detect_compose() {
  if docker compose version >/dev/null 2>&1; then
    COMPOSE_CMD=(docker compose)
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD=(docker-compose)
  else
    fatal "Docker Compose v2 is required."
  fi
}

prompt_secret() {
  local prompt="$1"
  local var_name="$2"
  local default_value="$3"
  local value=""

  if [[ -n "${!var_name:-}" ]]; then
    value="${!var_name}"
  elif [[ -n "$default_value" ]]; then
    value="$default_value"
  fi

  if [[ -z "$value" ]]; then
    if [[ -t 0 ]]; then
      read -rsp "$prompt" value
      echo
    else
      fatal "Cannot prompt for $var_name in non-interactive mode (set $var_name env var)."
    fi
  fi

  printf '%s' "$value"
}

require_file() {
  local path="$1"
  local label="$2"
  if [[ ! -f "$path" ]]; then
    fatal "Missing ${label} at ${path}. Run scripts/setup_data.sh first."
  fi
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOT_ENV="$ROOT_DIR/apps/movieRecBot/.env"
MCP_ENV="$ROOT_DIR/apps/mcp-fastmcp/.env"
TELEGRAM_SECRET_PATH="$ROOT_DIR/secrets/TELEGRAM_BOT_TOKEN"

BUILD_IMAGES=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build)
      BUILD_IMAGES=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fatal "Unknown option: $1"
      ;;
  esac
done

ensure_cmd docker
detect_compose

require_file "$BOT_ENV" "bot .env file"
require_file "$MCP_ENV" "MCP .env file"

if [[ ! -f "$TELEGRAM_SECRET_PATH" ]]; then
  info "Telegram token secret not found; capturing it now."
  mkdir -p "$(dirname "$TELEGRAM_SECRET_PATH")"
  TELEGRAM_INPUT=$(prompt_secret "Enter Telegram bot token (input hidden): " TELEGRAM_BOT_TOKEN "")
  printf '%s' "$TELEGRAM_INPUT" > "$TELEGRAM_SECRET_PATH"
  info "Stored Telegram token in secrets/TELEGRAM_BOT_TOKEN."
fi

SERVICES=(imdb-postgres imdb-ollama imdb-vec imdb-mcp movie-recs-db normalizer movie-recs-bot)
COMPOSE_ARGS=(up -d)
if (( BUILD_IMAGES )); then
  COMPOSE_ARGS=(up --build -d)
fi

info "Starting services: ${SERVICES[*]}"
"${COMPOSE_CMD[@]}" "${COMPOSE_ARGS[@]}" "${SERVICES[@]}"

info "All services requested. Use 'docker compose logs -f movie-recs-bot' to monitor startup."
