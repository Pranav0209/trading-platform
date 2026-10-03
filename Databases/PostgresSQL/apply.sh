#!/usr/bin/env bash

set -euo pipefail

# resolve paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATIONS_DIR="$SCRIPT_DIR/migrations"
SEED_DIR="$SCRIPT_DIR/seed"
ENV_FILE="$SCRIPT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "ERROR: .env file not found at $ENV_FILE"
    exit 1
fi

set -a
source "$ENV_FILE"
set +a

# connection settings from .env
HOST="${POSTGRES_HOST:-localhost}"
PORT="${POSTGRES_PORT:-5432}"
USER="${POSTGRES_USER:-postgres}"
DATABASE="${TARGET_DATABASE:-${POSTGRES_DB:-trading}}"

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
    -f "$MIGRATIONS_DIR/zkuba_dbschema.sql" \
    -f "$SEED_DIR/zkuba_dbseed.sql"

echo "Database setup completed successfully."



