#!/bin/sh
set -e

if [ -f /run/secrets/telegram_bot_token ]; then
  export TELEGRAM_BOT_TOKEN="$(cat /run/secrets/telegram_bot_token)"
fi

exec java $JAVA_OPTS -jar /app/app.jar
