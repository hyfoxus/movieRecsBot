#!/bin/sh
set -e

if [ -f /run/secrets/open_api_key ]; then
  export OPENAI_API_KEY="$(cat /run/secrets/open_api_key)"
fi

if [ -f /run/secrets/telegram_bot_token ]; then
  export TELEGRAM_BOT_TOKEN="$(cat /run/secrets/telegram_bot_token)"
fi

if [ -f /run/secrets/postgres_password ]; then
  secret_pwd="$(cat /run/secrets/postgres_password)"
  export POSTGRES_PASSWORD="$secret_pwd"
  export SPRING_DATASOURCE_PASSWORD="$secret_pwd"
fi

exec java $JAVA_OPTS -jar /app/app.jar
