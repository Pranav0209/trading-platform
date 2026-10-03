#!/usr/bin/env bash
set -euo pipefail

# Local integration test for auth-service.
# It starts the built NestJS app against a local Postgres instance, then proves
# the full auth flow works end to end: unauthenticated access is rejected, a new
# user can register and log in, /auth/me accepts the issued JWT, refresh rotates
# tokens, and the old refresh token is rejected after revocation.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

AUTH_PORT="${AUTH_PORT:-3000}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-trading}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-n3u3d4!}"
JWT_SECRET="${JWT_SECRET:-hello-world-this-is-a-dev-secret}"
DATABASE_URL="${DATABASE_URL:-postgresql://$DB_USER:$DB_PASSWORD@$DB_HOST:$DB_PORT/$DB_NAME}"
BOOTSTRAP_SCHEMA="${BOOTSTRAP_SCHEMA:-1}"

RUN_ID="$(date +%s)"
TEST_USERNAME="${TEST_USERNAME:-auth_local_$RUN_ID}"
TEST_EMAIL="${TEST_EMAIL:-auth_local_$RUN_ID@example.com}"
TEST_PASSWORD="${TEST_PASSWORD:-Mission123!}"
TEST_ACCOUNT_NUMBER="${TEST_ACCOUNT_NUMBER:-ACC-AUTH-LOCAL-$RUN_ID}"
AUTH_LOG="${AUTH_LOG:-$REPO_DIR/test/auth-local.log}"

AUTH_PID=""
TEST_USER_ID=""

json_field() {
  local field="$1"
  node -e "let raw=''; process.stdin.on('data', chunk => raw += chunk).on('end', () => { const value = JSON.parse(raw)['$field']; process.stdout.write(value == null ? '' : String(value)); });"
}

fail_with_logs() {
  local message="$1"
  echo "FAIL: $message"
  if [ -f "$AUTH_LOG" ]; then
    echo "---- auth-local.log ----"
    tail -n 40 "$AUTH_LOG" || true
    echo "------------------------"
  fi
  exit 1
}

cleanup() {
  echo "== Teardown =="

  if [ -n "$AUTH_PID" ]; then
    kill "$AUTH_PID" 2>/dev/null || true
    wait "$AUTH_PID" 2>/dev/null || true
  fi

  if [ -n "$TEST_USER_ID" ]; then
    PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
      -v ON_ERROR_STOP=1 \
      -c "DELETE FROM auth.refresh_tokens WHERE user_id = $TEST_USER_ID; DELETE FROM accounts WHERE user_id = $TEST_USER_ID; DELETE FROM auth.users WHERE id = $TEST_USER_ID;" \
      >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "== Stage: Preflight (local Postgres reachable?) =="
if ! PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1;" >/dev/null 2>&1; then
  fail_with_logs "cannot reach Postgres at $DB_HOST:$DB_PORT/$DB_NAME as $DB_USER"
fi
echo "PASS: Postgres reachable at $DB_HOST:$DB_PORT/$DB_NAME"

echo "== Stage: Preflight (required schema available?) =="
if ! PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -tAc "SELECT to_regclass('auth.users') IS NOT NULL AND to_regclass('auth.refresh_tokens') IS NOT NULL AND to_regclass('accounts') IS NOT NULL;" \
  | tr -d '[:space:]' | grep -q '^t$'; then
  if [ "$BOOTSTRAP_SCHEMA" != "1" ]; then
    fail_with_logs "required Sprint 3 tables are missing. Set BOOTSTRAP_SCHEMA=1 or apply Databases/PostgresSQL first"
  fi

  echo "Required tables missing; bootstrapping minimal auth schema in $DB_NAME"
  PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER','ADMIN')),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth.refresh_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  token_hash VARCHAR(255) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth.login_attempts (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  "ipAddress" VARCHAR(45) NOT NULL,
  success BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_username ON auth.login_attempts(username, created_at);
CREATE INDEX IF NOT EXISTS idx_login_attempts_ip ON auth.login_attempts("ipAddress", created_at);

CREATE TABLE IF NOT EXISTS accounts (
  id BIGSERIAL PRIMARY KEY,
  account_number VARCHAR(32) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL UNIQUE REFERENCES auth.users(id),
  cash_balance NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (cash_balance >= 0),
  currency VARCHAR(3) NOT NULL DEFAULT 'USD',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
  version INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
SQL
fi
echo "PASS: Sprint 3 auth tables detected"

echo "== Stage: Build + start the auth service (localhost:$AUTH_PORT) =="
cd "$REPO_DIR"
if [ ! -d node_modules ]; then
  npm ci >/dev/null 2>&1 || npm install >/dev/null 2>&1
fi
npm run build >/dev/null
: > "$AUTH_LOG"
PORT="$AUTH_PORT" DATABASE_URL="$DATABASE_URL" JWT_SECRET="$JWT_SECRET" node dist/main.js >"$AUTH_LOG" 2>&1 &
AUTH_PID=$!

echo "== Stage: Wait for auth service to be ready =="
ready=""
for _ in $(seq 1 30); do
  if ! kill -0 "$AUTH_PID" 2>/dev/null; then
    fail_with_logs "auth service exited before becoming ready"
  fi

  code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$AUTH_PORT/docs-json" || true)
  if [ "$code" = "200" ]; then
    ready="yes"
    break
  fi
  sleep 2
done

if [ "$ready" != "yes" ]; then
  fail_with_logs "auth service did not become ready on port $AUTH_PORT"
fi
echo "PASS: auth service ready on localhost:$AUTH_PORT"

echo "== Stage: Smoke test - unauthenticated /auth/me is rejected =="
CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$AUTH_PORT/auth/me")
if [ "$CODE" != "401" ]; then
  fail_with_logs "expected 401 from /auth/me without a token, got $CODE"
fi
echo "PASS: unauthenticated request rejected (401)"

echo "== Stage: Register a fresh user =="
REGISTER_RESPONSE=$(curl -s -X POST "http://localhost:$AUTH_PORT/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USERNAME\",\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}")
REGISTERED_ID=$(printf '%s' "$REGISTER_RESPONSE" | json_field id)
if [ -z "$REGISTERED_ID" ]; then
  fail_with_logs "registration failed. Response: $REGISTER_RESPONSE"
fi
TEST_USER_ID="$REGISTERED_ID"
echo "PASS: registered user $TEST_USERNAME with id $TEST_USER_ID"

echo "== Stage: Attach an account row for the new user =="
PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -v ON_ERROR_STOP=1 \
  -c "INSERT INTO accounts (account_number, user_id, cash_balance, currency, status, version) VALUES ('$TEST_ACCOUNT_NUMBER', $TEST_USER_ID, 0, 'USD', 'ACTIVE', 0) ON CONFLICT (user_id) DO NOTHING;" \
  >/dev/null
echo "PASS: account row ensured for user $TEST_USER_ID"

echo "== Stage: Login and obtain tokens =="
LOGIN_RESPONSE=$(curl -s -X POST "http://localhost:$AUTH_PORT/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USERNAME\",\"password\":\"$TEST_PASSWORD\"}")
ACCESS_TOKEN=$(printf '%s' "$LOGIN_RESPONSE" | json_field accessToken)
REFRESH_TOKEN=$(printf '%s' "$LOGIN_RESPONSE" | json_field refreshToken)
if [ -z "$ACCESS_TOKEN" ] || [ -z "$REFRESH_TOKEN" ]; then
  fail_with_logs "login failed. Response: $LOGIN_RESPONSE"
fi
echo "PASS: login issued access and refresh tokens"

echo "== Stage: Use the issued JWT against /auth/me =="
ME_RESPONSE=$(curl -s -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:$AUTH_PORT/auth/me")
ME_USERNAME=$(printf '%s' "$ME_RESPONSE" | json_field username)
ME_ACCOUNT_ID=$(printf '%s' "$ME_RESPONSE" | json_field accountId)
if [ "$ME_USERNAME" != "$TEST_USERNAME" ]; then
  fail_with_logs "/auth/me returned the wrong user. Response: $ME_RESPONSE"
fi
if [ -z "$ME_ACCOUNT_ID" ]; then
  fail_with_logs "/auth/me did not include an accountId. Response: $ME_RESPONSE"
fi
echo "PASS: /auth/me accepted the JWT and returned accountId $ME_ACCOUNT_ID"

echo "== Stage: Refresh rotates tokens =="
REFRESH_RESPONSE=$(curl -s -X POST "http://localhost:$AUTH_PORT/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
NEW_ACCESS_TOKEN=$(printf '%s' "$REFRESH_RESPONSE" | json_field accessToken)
NEW_REFRESH_TOKEN=$(printf '%s' "$REFRESH_RESPONSE" | json_field refreshToken)
if [ -z "$NEW_ACCESS_TOKEN" ] || [ -z "$NEW_REFRESH_TOKEN" ]; then
  fail_with_logs "refresh failed. Response: $REFRESH_RESPONSE"
fi
if [ "$NEW_REFRESH_TOKEN" = "$REFRESH_TOKEN" ]; then
  fail_with_logs "refresh token was not rotated"
fi
echo "PASS: refresh rotated both tokens"

echo "== Stage: Reusing the old refresh token is rejected =="
REUSE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://localhost:$AUTH_PORT/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
if [ "$REUSE_CODE" != "401" ]; then
  fail_with_logs "expected 401 when reusing a revoked refresh token, got $REUSE_CODE"
fi
echo "PASS: revoked refresh token rejected (401)"

echo "== Stage: Confirm rows landed in Postgres =="
TOKEN_STATE=$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -tAc "SELECT COUNT(*) || ':' || SUM(CASE WHEN revoked THEN 1 ELSE 0 END) FROM auth.refresh_tokens WHERE user_id = $TEST_USER_ID;")
if [ "${TOKEN_STATE// /}" != "2:2" ]; then
  fail_with_logs "expected two revoked refresh-token rows after replaying a revoked token, found ${TOKEN_STATE:-0:0}"
fi
echo "PASS: database state matches the final auth flow"

echo "== ALL STAGES PASSED =="
