BEGIN;

CREATE SCHEMA IF NOT EXISTS auth;

DROP TABLE IF EXISTS pnl CASCADE;
DROP TABLE IF EXISTS smart_rules CASCADE;
DROP TABLE IF EXISTS watchlist_instruments CASCADE;
DROP TABLE IF EXISTS watchlist_items CASCADE;
DROP TABLE IF EXISTS watchlists CASCADE;
DROP TABLE IF EXISTS holdings CASCADE;
DROP TABLE IF EXISTS positions CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS instruments CASCADE;
DROP TABLE IF EXISTS accounts CASCADE;
DROP TABLE IF EXISTS auth.users CASCADE;

CREATE TABLE IF NOT EXISTS auth.users (
 id BIGSERIAL PRIMARY KEY, username VARCHAR(50) NOT NULL UNIQUE, email VARCHAR(255) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL, role VARCHAR(20) NOT NULL CHECK(role IN ('CUSTOMER','ADMIN')),
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','ACTIVE','SUSPENDED','CLOSED')),
 notification_channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL' CHECK(notification_channel IN ('EMAIL','PUSH')),
 otp_code VARCHAR(255), otp_expires_at TIMESTAMPTZ, pending_new_email VARCHAR(255),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);

CREATE TABLE IF NOT EXISTS auth.refresh_tokens (
 id BIGSERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
 token_hash VARCHAR(255) NOT NULL UNIQUE,
 expires_at TIMESTAMPTZ NOT NULL,
 revoked BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);

CREATE TABLE IF NOT EXISTS auth.login_attempts (
 id BIGSERIAL PRIMARY KEY,
 username VARCHAR(64) NOT NULL,
 "ipAddress" VARCHAR(45) NOT NULL,
 success BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);

CREATE INDEX IF NOT EXISTS idx_login_attempts_username ON auth.login_attempts(username, created_at);
CREATE INDEX IF NOT EXISTS idx_login_attempts_ip ON auth.login_attempts("ipAddress", created_at);

CREATE TABLE IF NOT EXISTS accounts (
 id BIGSERIAL PRIMARY KEY, account_number VARCHAR(32) NOT NULL UNIQUE, user_id BIGINT NOT NULL UNIQUE REFERENCES auth.users(id),
 cash_balance NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK(cash_balance>=0), currency VARCHAR(3) NOT NULL DEFAULT 'USD',
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','SUSPENDED','CLOSED')), version INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);

CREATE TABLE IF NOT EXISTS instruments (symbol VARCHAR(20) PRIMARY KEY, display_name VARCHAR(255) NOT NULL, asset_class VARCHAR(20) NOT NULL, currency VARCHAR(3) NOT NULL, tradable BOOLEAN NOT NULL DEFAULT TRUE);

CREATE TABLE IF NOT EXISTS watchlists (id BIGSERIAL PRIMARY KEY,user_id BIGINT NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,name VARCHAR(100) NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE(user_id,name));

CREATE TABLE IF NOT EXISTS watchlist_items (watchlist_id BIGINT NOT NULL REFERENCES watchlists(id) ON DELETE CASCADE,symbol VARCHAR(20) NOT NULL REFERENCES instruments(symbol),added_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(watchlist_id,symbol));

CREATE TABLE IF NOT EXISTS orders (id UUID PRIMARY KEY,account_id BIGINT NOT NULL REFERENCES accounts(id),symbol VARCHAR(20) NOT NULL REFERENCES instruments(symbol),side VARCHAR(4) NOT NULL CHECK(side IN ('BUY','SELL')),order_type VARCHAR(10) NOT NULL CHECK(order_type IN ('MARKET','LIMIT')),quantity INTEGER NOT NULL CHECK(quantity>0),limit_price NUMERIC(19,4),executed_price NUMERIC(19,4),executed_on TIMESTAMPTZ,status VARCHAR(20) NOT NULL CHECK(status IN ('NEW','FILLED','CANCELLED','REJECTED')),reject_reason VARCHAR(255),idempotency_key VARCHAR(100) NOT NULL UNIQUE,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,CHECK((order_type='LIMIT' AND limit_price IS NOT NULL) OR (order_type='MARKET' AND limit_price IS NULL)));

CREATE TABLE IF NOT EXISTS positions (id BIGSERIAL PRIMARY KEY,account_id BIGINT NOT NULL REFERENCES accounts(id),symbol VARCHAR(20) NOT NULL REFERENCES instruments(symbol),quantity INTEGER NOT NULL CHECK(quantity>=0),average_cost NUMERIC(19,4) NOT NULL CHECK(average_cost>=0),version INTEGER NOT NULL DEFAULT 0,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE(account_id,symbol));

ALTER TABLE positions ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_orders_account_created ON orders(account_id,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_positions_account ON positions(account_id);

CREATE TABLE IF NOT EXISTS pnl (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    account_id BIGINT NOT NULL REFERENCES accounts(id),
    symbol VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    sell_price NUMERIC(19,4) NOT NULL,
    average_cost NUMERIC(19,4) NOT NULL CHECK (average_cost >= 0),
    realised_pnl NUMERIC(19,4) NOT NULL,
    realised_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pnl_account_realised_at 
    ON pnl(account_id, realised_at DESC);

DROP TABLE IF EXISTS price_alerts;

-- Price alerts table
CREATE TABLE IF NOT EXISTS price_alerts (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    symbol VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    direction VARCHAR(5) NOT NULL CHECK(direction IN ('ABOVE', 'BELOW')),
    threshold NUMERIC(19,4) NOT NULL CHECK(threshold > 0),
    state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(state IN ('ACTIVE', 'TRIGGERED', 'DORMANT')),
    last_fired_at TIMESTAMPTZ,
    delivery_status VARCHAR(20) DEFAULT 'PENDING' CHECK(delivery_status IN ('PENDING', 'DELIVERED', 'FAILED')),
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for efficient alert evaluation and queries
-- Hot path: market-data consumer queries (account_id, symbol, state) for active alerts
CREATE INDEX IF NOT EXISTS idx_price_alerts_account_state ON price_alerts(account_id, state);
CREATE INDEX IF NOT EXISTS idx_price_alerts_account_symbol ON price_alerts(account_id, symbol);

COMMIT;
