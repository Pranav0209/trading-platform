-- Test script for P&L backfill logic
-- This script creates a clean test dataset, runs the backfill, and validates results

BEGIN;

-- Step 1: Clean up any existing test data
DELETE FROM pnl WHERE account_id = 999 OR account_id = 998 OR account_id = 997;
DELETE FROM positions WHERE account_id = 999 OR account_id = 998 OR account_id = 997;
DELETE FROM orders WHERE account_id = 999 OR account_id = 998 OR account_id = 997;
DELETE FROM accounts WHERE id IN (999, 998, 997);
DELETE FROM auth.users WHERE id IN (999, 998, 997);

-- Step 2: Create test accounts and users
INSERT INTO auth.users (id, username, email, password_hash, role, status)
VALUES 
    (999, 'testuser999', 'test999@example.com', 'hash', 'CUSTOMER', 'ACTIVE'),
    (998, 'testuser998', 'test998@example.com', 'hash', 'CUSTOMER', 'ACTIVE'),
    (997, 'testuser997', 'test997@example.com', 'hash', 'CUSTOMER', 'ACTIVE');

INSERT INTO accounts (id, account_number, user_id, cash_balance, currency, status)
VALUES 
    (999, 'TEST999', 999, 100000, 'USD', 'ACTIVE'),
    (998, 'TEST998', 998, 100000, 'USD', 'ACTIVE'),
    (997, 'TEST997', 997, 100000, 'USD', 'ACTIVE');

-- Step 3: Create test orders for Case A: buy 10 @100, buy 10 @120, sell 5 @130 -> P&L 100
-- (Using timestamps spaced 1 minute apart to ensure deterministic ordering)
INSERT INTO orders (id, account_id, symbol, side, order_type, quantity, limit_price, executed_price, executed_on, status, reject_reason, idempotency_key, created_at, updated_at)
VALUES 
    ('a0000000-0000-0000-0000-000000000001'::UUID, 999, 'AAPL', 'BUY', 'MARKET', 10, NULL, 100.0000, '2026-01-01 10:00:00+00', 'FILLED', NULL, 'test-a1', '2026-01-01 10:00:00+00', '2026-01-01 10:00:00+00'),
    ('a0000000-0000-0000-0000-000000000002'::UUID, 999, 'AAPL', 'BUY', 'MARKET', 10, NULL, 120.0000, '2026-01-01 10:01:00+00', 'FILLED', NULL, 'test-a2', '2026-01-01 10:01:00+00', '2026-01-01 10:01:00+00'),
    ('a0000000-0000-0000-0000-000000000003'::UUID, 999, 'AAPL', 'SELL', 'MARKET', 5, NULL, 130.0000, '2026-01-01 10:02:00+00', 'FILLED', NULL, 'test-a3', '2026-01-01 10:02:00+00', '2026-01-01 10:02:00+00');

-- Step 4: Create test orders for Case B: buy 10 @100, sell 10 @130 -> P&L 300, position closed
INSERT INTO orders (id, account_id, symbol, side, order_type, quantity, limit_price, executed_price, executed_on, status, reject_reason, idempotency_key, created_at, updated_at)
VALUES 
    ('b0000000-0000-0000-0000-000000000001'::UUID, 998, 'AAPL', 'BUY', 'MARKET', 10, NULL, 100.0000, '2026-02-01 10:00:00+00', 'FILLED', NULL, 'test-b1', '2026-02-01 10:00:00+00', '2026-02-01 10:00:00+00'),
    ('b0000000-0000-0000-0000-000000000002'::UUID, 998, 'AAPL', 'SELL', 'MARKET', 10, NULL, 130.0000, '2026-02-01 10:01:00+00', 'FILLED', NULL, 'test-b2', '2026-02-01 10:01:00+00', '2026-02-01 10:01:00+00');

-- Step 5: Create test orders for Case C: buy 10 @100, sell 10 @130, buy 10 @200, sell 10 @210 -> P&L 100 (on final sell)
INSERT INTO orders (id, account_id, symbol, side, order_type, quantity, limit_price, executed_price, executed_on, status, reject_reason, idempotency_key, created_at, updated_at)
VALUES 
    ('c0000000-0000-0000-0000-000000000001'::UUID, 997, 'AAPL', 'BUY', 'MARKET', 10, NULL, 100.0000, '2026-03-01 10:00:00+00', 'FILLED', NULL, 'test-c1', '2026-03-01 10:00:00+00', '2026-03-01 10:00:00+00'),
    ('c0000000-0000-0000-0000-000000000002'::UUID, 997, 'AAPL', 'SELL', 'MARKET', 10, NULL, 130.0000, '2026-03-01 10:01:00+00', 'FILLED', NULL, 'test-c2', '2026-03-01 10:01:00+00', '2026-03-01 10:01:00+00'),
    ('c0000000-0000-0000-0000-000000000003'::UUID, 997, 'AAPL', 'BUY', 'MARKET', 10, NULL, 200.0000, '2026-03-01 10:02:00+00', 'FILLED', NULL, 'test-c3', '2026-03-01 10:02:00+00', '2026-03-01 10:02:00+00'),
    ('c0000000-0000-0000-0000-000000000004'::UUID, 997, 'AAPL', 'SELL', 'MARKET', 10, NULL, 210.0000, '2026-03-01 10:03:00+00', 'FILLED', NULL, 'test-c4', '2026-03-01 10:03:00+00', '2026-03-01 10:03:00+00');

COMMIT;

-- Step 6: Run the backfill (paste the backfill SQL from 03-realised-pnl-table.sql)
-- (This is done separately by running the migration file)

-- Step 7: Validate results
SELECT 'CASE A VALIDATION' as test_case;
SELECT 
    order_id,
    account_id,
    symbol,
    quantity,
    sell_price,
    average_cost,
    realised_pnl,
    realised_at,
    CASE
        WHEN order_id = 'a0000000-0000-0000-0000-000000000003'::UUID THEN
            CASE
                WHEN realised_pnl = 100.0000 THEN '✓ PASS'
                ELSE '✗ FAIL: Expected P&L 100.00, got ' || realised_pnl::TEXT
            END
        ELSE '? UNEXPECTED'
    END as validation
FROM pnl
WHERE account_id = 999
ORDER BY realised_at;

SELECT '';
SELECT 'CASE B VALIDATION' as test_case;
SELECT 
    order_id,
    account_id,
    symbol,
    quantity,
    sell_price,
    average_cost,
    realised_pnl,
    realised_at,
    CASE
        WHEN order_id = 'b0000000-0000-0000-0000-000000000002'::UUID THEN
            CASE
                WHEN realised_pnl = 300.0000 THEN '✓ PASS'
                ELSE '✗ FAIL: Expected P&L 300.00, got ' || realised_pnl::TEXT
            END
        ELSE '? UNEXPECTED'
    END as validation
FROM pnl
WHERE account_id = 998
ORDER BY realised_at;

SELECT '';
SELECT 'CASE C VALIDATION' as test_case;
SELECT 
    order_id,
    account_id,
    symbol,
    quantity,
    sell_price,
    average_cost,
    realised_pnl,
    realised_at,
    CASE
        WHEN order_id = 'c0000000-0000-0000-0000-000000000002'::UUID THEN
            CASE
                WHEN realised_pnl = 300.0000 THEN '✓ PASS (first sell)'
                ELSE '✗ FAIL: First sell expected P&L 300.00, got ' || realised_pnl::TEXT
            END
        WHEN order_id = 'c0000000-0000-0000-0000-000000000004'::UUID THEN
            CASE
                WHEN realised_pnl = 100.0000 THEN '✓ PASS (second sell)'
                ELSE '✗ FAIL: Second sell expected P&L 100.00, got ' || realised_pnl::TEXT
            END
        ELSE '? UNEXPECTED'
    END as validation
FROM pnl
WHERE account_id = 997
ORDER BY realised_at;

SELECT '';
SELECT 'SUMMARY' as summary;
SELECT 
    (SELECT COUNT(*) FROM pnl WHERE account_id IN (999, 998, 997)) as total_pnl_rows,
    (SELECT COUNT(*) FROM pnl WHERE account_id = 999) as case_a_rows,
    (SELECT COUNT(*) FROM pnl WHERE account_id = 998) as case_b_rows,
    (SELECT COUNT(*) FROM pnl WHERE account_id = 997) as case_c_rows;
