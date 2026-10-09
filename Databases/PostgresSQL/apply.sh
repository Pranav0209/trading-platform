#!/usr/bin/env bash

set -euo pipefail

# resolve paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
SCHEMA_FILE="$SCRIPT_DIR/schema.sql"
SEED_FILE="$SCRIPT_DIR/seed-data.sql"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "ERROR: .env file not found at $ENV_FILE"
    exit 1
fi

if [[ ! -f "$SCHEMA_FILE" ]]; then
    echo "ERROR: schema file not found at $SCHEMA_FILE"
    exit 1
fi

if [[ ! -f "$SEED_FILE" ]]; then
    echo "ERROR: seed data file not found at $SEED_FILE"
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

# apply schema then seed data, then price alerts
psql \
    --host="$HOST" \
    --port="$PORT" \
    --username="$USER" \
    --dbname="$DATABASE" \
    -v ON_ERROR_STOP=1 \
    -f "$SCHEMA_FILE" \
    -f "$SEED_FILE"

echo "Database setup completed successfully."



