-- Database migration for Watchlist Price Alerts
-- Created for Sprint 10
-- 
-- This migration adds support for price alerts on watchlist instruments.
-- Design:
-- 1. Alerts table stores alert configuration (symbol, threshold, direction)
-- 2. State is ACTIVE (monitoring) -> TRIGGERED (fired, waiting for reset) -> back to ACTIVE
-- 3. Delivery tracking: PENDING -> DELIVERED or FAILED
-- 4. Alert limit: 100 per account (soft warning, hard enforcement at 409)
-- 5. Consumer is indexed on (account_id, state) for efficient scanning of active alerts

BEGIN;

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

-- Indexes for API queries
CREATE INDEX IF NOT EXISTS idx_price_alerts_account ON price_alerts(account_id);
CREATE INDEX IF NOT EXISTS idx_price_alerts_created ON price_alerts(created_at DESC);

-- Constraint: maximum 100 active + triggered alerts per account
-- This is enforced in the application layer with a check before INSERT
-- Database constraint would be: create a check trigger or use a materialized view
-- For now, relying on application-level validation with COUNT query

COMMIT;
