#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/setup_data.sh [options]

Bootstraps the IMDb data stack: generates .env files, stores the Telegram token
as a Docker secret, starts the imdb-* services, and triggers the bootstrap job
that downloads IMDb data and backfills embeddings.

Options:
  --use-defaults        Reuse existing values/non-interactive defaults for .env files.
  --skip-bootstrap      Start containers but skip calling the bootstrap HTTP endpoint.
  --no-rebuild-index    Trigger bootstrap without rebuilding the HNSW index.
  -h, --help            Show this help message.

Environment overrides:
  TELEGRAM_BOT_TOKEN    Pre-populate the Telegram secret non-interactively.
  IMDB_BOOTSTRAP_TOKEN  Token expected by imdb-vec when calling the bootstrap API.
  IMDB_HTTP_PORT        Host port that exposes imdb-vec (default: 8088).
EOF
}

info() {
  echo "[setup-data] $*"
}

fatal() {
  echo "[setup-data] ERROR: $*" >&2
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

wait_for_http() {
  local url="$1"
  local retries="${2:-60}"
  local delay="${3:-5}"
  local attempt=1

  until curl -fsS "$url" >/dev/null 2>&1; do
    if (( attempt >= retries )); then
      return 1
    fi
    sleep "$delay"
    ((attempt++))
  done
  return 0
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_SCRIPT="$ROOT_DIR/scripts/bootstrap_env.py"
TELEGRAM_SECRET_PATH="$ROOT_DIR/secrets/TELEGRAM_BOT_TOKEN"

USE_DEFAULTS=0
SKIP_BOOTSTRAP=0
REBUILD_INDEX=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --use-defaults)
      USE_DEFAULTS=1
      shift
      ;;
    --skip-bootstrap)
      SKIP_BOOTSTRAP=1
      shift
      ;;
    --no-rebuild-index)
      REBUILD_INDEX=0
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

ensure_cmd python3
ensure_cmd curl
ensure_cmd docker
detect_compose

info "Generating environment files via bootstrap_env.py"
ENV_ARGS=()
if (( USE_DEFAULTS )); then
  ENV_ARGS+=(--use-defaults)
fi
python3 "$ENV_SCRIPT" "${ENV_ARGS[@]}"

mkdir -p "$(dirname "$TELEGRAM_SECRET_PATH")"
if [[ -f "$TELEGRAM_SECRET_PATH" ]]; then
  info "Telegram token secret already exists at secrets/TELEGRAM_BOT_TOKEN."
  if [[ -t 0 ]]; then
    read -rp "Do you want to replace it? [y/N]: " answer
    if [[ "$answer" =~ ^[Yy]$ ]]; then
      TELEGRAM_INPUT=$(prompt_secret "Enter Telegram bot token (input hidden): " TELEGRAM_BOT_TOKEN "")
      printf '%s' "$TELEGRAM_INPUT" > "$TELEGRAM_SECRET_PATH"
      info "Updated secrets/TELEGRAM_BOT_TOKEN."
    else
      info "Keeping existing Telegram token."
    fi
  else
    info "Non-interactive mode detected; keeping existing Telegram token."
  fi
else
  TELEGRAM_INPUT=$(prompt_secret "Enter Telegram bot token (input hidden): " TELEGRAM_BOT_TOKEN "")
  printf '%s' "$TELEGRAM_INPUT" > "$TELEGRAM_SECRET_PATH"
  info "Stored Telegram token in secrets/TELEGRAM_BOT_TOKEN."
fi

info "Starting IMDb data services (postgres, ollama, vec)..."
"${COMPOSE_CMD[@]}" up -d imdb-postgres imdb-ollama imdb-vec

IMDB_PORT="${IMDB_HTTP_PORT:-8088}"
HEALTH_URL="http://localhost:${IMDB_PORT}/actuator/health"
info "Waiting for imdb-vec to become healthy at ${HEALTH_URL} (this can take a minute)..."
if wait_for_http "$HEALTH_URL" 90 5; then
  info "imdb-vec is responding."
else
  fatal "imdb-vec did not become healthy in time. Check 'docker compose logs imdb-vec'."
fi

if (( SKIP_BOOTSTRAP )); then
  info "Bootstrap trigger skipped (per --skip-bootstrap)."
  exit 0
fi

BOOTSTRAP_URL="http://localhost:${IMDB_PORT}/api/admin/bootstrap?rebuildIndex=${REBUILD_INDEX}"
BOOTSTRAP_TOKEN="${IMDB_BOOTSTRAP_TOKEN:-bootstrap-token}"
info "Triggering IMDb bootstrap via ${BOOTSTRAP_URL}"
if [[ -n "$BOOTSTRAP_TOKEN" ]]; then
  curl -fsS -X POST -H "X-Bootstrap-Token: ${BOOTSTRAP_TOKEN}" "$BOOTSTRAP_URL" >/dev/null
else
  curl -fsS -X POST "$BOOTSTRAP_URL" >/dev/null
fi
info "Bootstrap accepted. Monitor progress with 'docker compose logs -f imdb-vec'."
