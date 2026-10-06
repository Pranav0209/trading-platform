-- Migration: Create realised_pnl table for tracking locked-in profit/loss on SELL orders
--
-- Key Design:
-- 1. Each SELL order creates at most one P&L row (UNIQUE constraint on order_id)
-- 2. average_cost is captured BEFORE the SELL executes (from position table via executor-service)
-- 3. When a SELL closes a position (qty -> 0), the position row is deleted, so we must record P&L at fill time
-- 4. Backfill replays orders in time order per (account_id, symbol) with running weighted average
--    to compute what the average_cost would have been at each SELL
-- 5. Backfill is idempotent: ON CONFLICT (order_id) DO NOTHING handles replays safely

BEGIN;

-- Create the main pnl table
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

-- Index for querying realised P&L history by account and date (pagination)
-- Used by the portfolio API for filtering and sorting results
CREATE INDEX IF NOT EXISTS idx_pnl_account_realised_at 
    ON pnl(account_id, realised_at DESC);

-- Backfill realised P&L for all FILLED SELL orders
-- Algorithm: Replay orders per (account_id, symbol) in time order with running position state:
--   - BUY:  new_avg = (old_avg * old_qty + price * qty) / (old_qty + qty)
--   - SELL: record P&L using avg BEFORE sell, then reduce qty (avg unchanged unless qty becomes 0)
--   - When qty reaches 0, reset avg to 0 (position closed)
-- Idempotent: ON CONFLICT (order_id) DO NOTHING for safe replay
INSERT INTO pnl (order_id, account_id, symbol, quantity, sell_price, average_cost, realised_pnl, realised_at)
WITH RECURSIVE order_fills AS (
    -- Get all FILLED orders (BUY and SELL) ordered by execution time, then by creation time for determinism
    SELECT 
        id as order_id,
        account_id,
        symbol,
        side,
        quantity,
        executed_price,
        executed_on,
        created_at,
        ROW_NUMBER() OVER (PARTITION BY account_id, symbol ORDER BY executed_on ASC NULLS LAST, created_at ASC, id ASC) as fill_seq
    FROM orders
    WHERE status = 'FILLED'
        AND executed_price IS NOT NULL
        AND executed_on IS NOT NULL
),
position_tracker AS (
    -- Base case: initialize position state before any fills (held_qty=0, avg_cost=0)
    SELECT 
        of.account_id,
        of.symbol,
        0::BIGINT as last_fill_seq,
        0 as held_qty,
        0::NUMERIC as avg_cost
    FROM (SELECT DISTINCT account_id, symbol FROM order_fills) of
    
    UNION ALL
    
    -- Recursive case: apply each fill in order
    SELECT 
        of.account_id,
        of.symbol,
        of.fill_seq,
        -- Update held_qty: +qty for BUY, -qty for SELL
        CASE 
            WHEN of.side = 'BUY' THEN pt.held_qty + of.quantity
            WHEN of.side = 'SELL' THEN pt.held_qty - of.quantity
            ELSE pt.held_qty
        END as held_qty_after,
        -- Update avg_cost
        CASE
            WHEN of.side = 'BUY' THEN
                -- New avg = (old_avg * old_qty + buy_price * buy_qty) / (old_qty + buy_qty)
                (pt.avg_cost * pt.held_qty + of.executed_price * of.quantity) 
                    / (pt.held_qty + of.quantity)::NUMERIC
            WHEN of.side = 'SELL' THEN
                -- If SELL closes the position, reset avg_cost to 0; otherwise unchanged
                CASE 
                    WHEN pt.held_qty - of.quantity = 0 THEN 0::NUMERIC
                    ELSE pt.avg_cost
                END
            ELSE pt.avg_cost
        END as avg_cost_after
    FROM order_fills of
    JOIN position_tracker pt ON of.account_id = pt.account_id
        AND of.symbol = pt.symbol
        AND of.fill_seq = pt.last_fill_seq + 1
)
-- For each SELL, emit P&L row using the position state BEFORE the sell
SELECT 
    of.order_id,
    of.account_id,
    of.symbol,
    of.quantity,
    of.executed_price as sell_price,
    pt.avg_cost as average_cost,
    (of.executed_price - pt.avg_cost) * of.quantity as realised_pnl,
    of.executed_on as realised_at
FROM order_fills of
JOIN position_tracker pt ON of.account_id = pt.account_id
    AND of.symbol = pt.symbol
    AND of.fill_seq = pt.last_fill_seq + 1
WHERE of.side = 'SELL'
    AND pt.held_qty >= of.quantity
ON CONFLICT (order_id) DO NOTHING;

COMMIT;
