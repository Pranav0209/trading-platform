#!/usr/bin/env bash

set -euo pipefail

# resolve paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATIONS_DIR="$SCRIPT_DIR/init"
ENV_FILE="$SCRIPT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "ERROR: .env file not found at $ENV_FILE"
    exit 1
fi

set -a
source "$ENV_FILE"
set +a

# connection settings from .env
HOST="${POSTGRES_HOST}"
PORT="${POSTGRES_PORT}"
USER="${POSTGRES_USER}"
DATABASE="${TARGET_DATABASE:-${POSTGRES_DB}}"

# validate database
if [[ -z "$DATABASE" ]]; then
    echo "ERROR: TARGET_DATABASE or POSTGRES_DB must be set in .env"
    exit 1
fi

echo "Connecting to PostgreSQL..."
echo "Host:     $HOST"
echo "Port:     $PORT"
echo "Database: $DATABASE"
echo "User:     $USER"

# apply schema then seed data
psql \
    --host="$HOST" \
    --port="$PORT" \
    --username="$USER" \
    --dbname="$DATABASE" \
    -v ON_ERROR_STOP=1 \
    -f "$MIGRATIONS_DIR/01-schema.sql" \
    -f "$MIGRATIONS_DIR/02-seed-data.sql"

echo "Database setup completed successfully."



