#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/setup_everything.sh [options]

Convenience wrapper that runs both setup_data.sh and setup_bot.sh so the full
stack (IMDb data + Telegram bot) is configured in one go.

Options forwarded to setup_data.sh:
  --skip-bootstrap
  --no-rebuild-index

Options forwarded to setup_bot.sh:
  --build

General options:
  -h, --help      Show this help message.
EOF
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_SCRIPT="$ROOT_DIR/scripts/setup_data.sh"
BOT_SCRIPT="$ROOT_DIR/scripts/setup_bot.sh"

DATA_ARGS=()
BOT_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-bootstrap|--no-rebuild-index)
      DATA_ARGS+=("$1")
      shift
      ;;
    --build)
      BOT_ARGS+=("$1")
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[setup-all] ERROR: Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

echo "[setup-all] Running setup_data.sh ${DATA_ARGS[*]}"
"$DATA_SCRIPT" "${DATA_ARGS[@]}"

echo "[setup-all] Running setup_bot.sh ${BOT_ARGS[*]}"
"$BOT_SCRIPT" "${BOT_ARGS[@]}"

echo "[setup-all] Complete. The docker stack should now be running."
