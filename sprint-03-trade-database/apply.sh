#!/usr/bin/env bash

set -euo pipefail

# Directory containing this script: rootdir/Database
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Directory containing database migrations
MIGRATIONS_DIR="$SCRIPT_DIR/migrations"

# Project root: rootdir
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load root .env
ENV_FILE="$REPO_ROOT/.env"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "ERROR: .env file not found at $ENV_FILE"
    exit 1
fi

set -a
source "$ENV_FILE"
set +a

# PostgreSQL configuration from .env
HOST="${POSTGRES_HOST:-localhost}"
PORT="${POSTGRES_PORT:-5432}"
USER="${POSTGRES_USER:-postgres}"
DATABASE="${TARGET_DATABASE:-${POSTGRES_DB:-}}"

# Validate database
if [[ -z "$DATABASE" ]]; then
    echo "ERROR: TARGET_DATABASE or POSTGRES_DB must be set in .env"
    exit 1
fi

echo "Connecting to PostgreSQL..."
echo "Host:     $HOST"
echo "Port:     $PORT"
echo "Database: $DATABASE"
echo "User:     $USER"

# -W / --password forces psql to prompt for the password
psql \
    --host="$HOST" \
    --port="$PORT" \
    --username="$USER" \
    --dbname="$DATABASE" \
    -v ON_ERROR_STOP=1 \
    -f "$MIGRATIONS_DIR/zkuba_dbschema.sql"

echo "Database setup completed successfully."



