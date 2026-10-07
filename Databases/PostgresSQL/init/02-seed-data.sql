BEGIN;

-- Seed Additional Tradable Instruments
INSERT INTO instruments (symbol, display_name, asset_class, currency, tradable) VALUES
    ('GOOGL', 'Alphabet Inc.', 'EQUITY', 'USD', TRUE),
    ('AMZN', 'Amazon.com Inc.', 'EQUITY', 'USD', TRUE),
    ('META', 'Meta Platforms Inc.', 'EQUITY', 'USD', TRUE),
    ('ACME', 'Acme Corporation', 'EQUITY', 'USD', TRUE),
    ('INFY.NS', 'Infosys Limited', 'EQUITY', 'INR', TRUE),
    ('AAPL', 'Apple Inc.', 'EQUITY', 'USD', TRUE),
    ('MSFT', 'Microsoft Corporation', 'EQUITY', 'USD', TRUE),
    ('TSLA', 'Tesla Inc.', 'EQUITY', 'USD', TRUE),
    ('NVDA', 'NVIDIA Corporation', 'EQUITY', 'USD', TRUE),
    ('NFLX', 'Netflix Inc.', 'EQUITY', 'USD', TRUE),
    ('INTC', 'Intel Corporation', 'EQUITY', 'USD', TRUE),
    ('AMD', 'Advanced Micro Devices Inc.', 'EQUITY', 'USD', TRUE),
    ('DIS', 'The Walt Disney Company', 'EQUITY', 'USD', TRUE),
    ('SBUX', 'Starbucks Corporation', 'EQUITY', 'USD', TRUE),
    ('NKE', 'NIKE Inc.', 'EQUITY', 'USD', TRUE),
    ('RELIANCE.NS', 'Reliance Industries Limited', 'EQUITY', 'INR', TRUE),
    ('TCS.NS', 'Tata Consultancy Services Limited', 'EQUITY', 'INR', TRUE),
    ('HDFCBANK.NS', 'HDFC Bank Limited', 'EQUITY', 'INR', TRUE),
    ('ICICIBANK.NS', 'ICICI Bank Limited', 'EQUITY', 'INR', TRUE),
    ('SBIN.NS', 'State Bank of India', 'EQUITY', 'INR', TRUE),
    ('BHARTIARTL.NS', 'Bharti Airtel Limited', 'EQUITY', 'INR', TRUE),
    ('ITC.NS', 'ITC Limited', 'EQUITY', 'INR', TRUE),
    ('LT.NS', 'Larsen & Toubro Limited', 'EQUITY', 'INR', TRUE),
    ('HINDUNILVR.NS', 'Hindustan Unilever Limited', 'EQUITY', 'INR', TRUE),
    ('BAJFINANCE.NS', 'Bajaj Finance Limited', 'EQUITY', 'INR', TRUE)
ON CONFLICT (symbol) DO NOTHING;

-- Seed Sample Users (Password for all seeded users is: password123)
-- BCrypt hash for 'password123': $2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy
INSERT INTO auth.users (id, username, email, password_hash, role, status, notification_channel) VALUES
    (1, 'admin', 'admin@trading.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'ADMIN', 'ACTIVE', 'PUSH'),
    (2, 'john_doe', 'john@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'CUSTOMER', 'ACTIVE', 'PUSH'),
    (3, 'jane_smith', 'jane@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'CUSTOMER', 'ACTIVE', 'PUSH'),
    (4, 'bob_suspended', 'bob@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'CUSTOMER', 'SUSPENDED', 'PUSH'),
    (5, 'alice_closed', 'alice@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'CUSTOMER', 'CLOSED', 'PUSH'),
    (6, 'charlie_closed_usr', 'charlie@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'CUSTOMER', 'CLOSED', 'PUSH'),
    (7, 'dave_suspended_usr', 'dave@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'CUSTOMER', 'SUSPENDED', 'PUSH'),
    (8, 'eve_active_usr', 'eve@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'CUSTOMER', 'ACTIVE', 'PUSH'),
    (9, 'frank_active_usr', 'frank@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'CUSTOMER', 'ACTIVE', 'PUSH')
ON CONFLICT (id) DO NOTHING;

-- Reset sequence for users table so new registrations start after seeded IDs
SELECT setval('auth.users_id_seq', (SELECT MAX(id) FROM auth.users));

-- Seed Trading Accounts
INSERT INTO accounts (id, account_number, user_id, cash_balance, currency, status, version) VALUES
    (1, 'ACC-USD-ADMIN', 1, 1000000.00, 'USD', 'ACTIVE', 0),
    (2, 'ACC-USD-JOHN01', 2, 45000.00, 'USD', 'ACTIVE', 0),
    (3, 'ACC-USD-JANE02', 3, 28000.00, 'USD', 'ACTIVE', 0),
    (4, 'ACC-USD-BOB03', 4, 15000.00, 'USD', 'SUSPENDED', 0),
    (5, 'ACC-USD-ALICE04', 5, 0.00, 'USD', 'CLOSED', 0),
    (6, 'ACC-USD-CHARLIE06', 6, 1000.00, 'USD', 'ACTIVE', 0),
    (7, 'ACC-USD-DAVE07', 7, 2000.00, 'USD', 'ACTIVE', 0),
    (8, 'ACC-USD-EVE08', 8, 3000.00, 'USD', 'CLOSED', 0),
    (9, 'ACC-USD-FRANK09', 9, 4000.00, 'USD', 'SUSPENDED', 0)
ON CONFLICT (id) DO NOTHING;

-- Reset sequence for accounts table
SELECT setval('accounts_id_seq', (SELECT MAX(id) FROM accounts));

-- Seed User Positions
INSERT INTO positions (id, account_id, symbol, quantity, average_cost, version) VALUES
    (1, 2, 'AAPL', 15, 220.5000, 0),
    (2, 2, 'NVDA', 25, 165.0000, 0),
    (3, 3, 'MSFT', 10, 495.2500, 0)
ON CONFLICT (account_id, symbol) DO NOTHING;

-- Reset sequence for positions table
SELECT setval('positions_id_seq', (SELECT MAX(id) FROM positions));

-- Seed User Watchlists
INSERT INTO watchlists (id, user_id, name) VALUES
    (1, 2, 'Tech Leaders'),
    (2, 3, 'AI & Growth')
ON CONFLICT (user_id, name) DO NOTHING;

-- Reset sequence for watchlists table
SELECT setval('watchlists_id_seq', (SELECT MAX(id) FROM watchlists));

-- Seed Watchlist Items
INSERT INTO watchlist_items (watchlist_id, symbol) VALUES
    (1, 'AAPL'),
    (1, 'MSFT'),
    (1, 'NVDA'),
    (2, 'NVDA'),
    (2, 'TSLA'),
    (2, 'AMZN')
ON CONFLICT (watchlist_id, symbol) DO NOTHING;

-- Seed Sample Filled Orders
INSERT INTO orders (id, account_id, symbol, side, order_type, quantity, limit_price, executed_price, executed_on, status, idempotency_key) VALUES
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 2, 'AAPL', 'BUY', 'MARKET', 15, NULL, 220.5000, CURRENT_TIMESTAMP, 'FILLED', 'seed-order-john-aapl-1'),
    ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 2, 'NVDA', 'BUY', 'MARKET', 25, NULL, 165.0000, CURRENT_TIMESTAMP, 'FILLED', 'seed-order-john-nvda-1'),
    ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', 3, 'MSFT', 'BUY', 'MARKET', 10, NULL, 495.2500, CURRENT_TIMESTAMP, 'FILLED', 'seed-order-jane-msft-1')
ON CONFLICT (id) DO NOTHING;
-- Backfill realised P&L for all FILLED SELL orders
INSERT INTO pnl (order_id, account_id, symbol, quantity, sell_price, average_cost, realised_pnl, realised_at)
WITH RECURSIVE order_fills AS (
    SELECT 
        id as order_id, account_id, symbol, side, quantity, executed_price, executed_on, created_at,
        ROW_NUMBER() OVER (PARTITION BY account_id, symbol ORDER BY executed_on ASC NULLS LAST, created_at ASC, id ASC) as fill_seq
    FROM orders
    WHERE status = 'FILLED' AND executed_price IS NOT NULL AND executed_on IS NOT NULL
),
position_tracker AS (
    SELECT 
        of.account_id, of.symbol, 0::BIGINT as last_fill_seq, 0 as held_qty, 0::NUMERIC as avg_cost
    FROM (SELECT DISTINCT account_id, symbol FROM order_fills) of
    UNION ALL
    SELECT 
        of.account_id, of.symbol, of.fill_seq,
        CASE 
            WHEN of.side = 'BUY' THEN pt.held_qty + of.quantity
            WHEN of.side = 'SELL' THEN pt.held_qty - of.quantity
            ELSE pt.held_qty
        END as held_qty_after,
        CASE
            WHEN of.side = 'BUY' THEN
                (pt.avg_cost * pt.held_qty + of.executed_price * of.quantity) / (pt.held_qty + of.quantity)::NUMERIC
            WHEN of.side = 'SELL' THEN
                CASE WHEN pt.held_qty - of.quantity = 0 THEN 0::NUMERIC ELSE pt.avg_cost END
            ELSE pt.avg_cost
        END as avg_cost_after
    FROM order_fills of
    JOIN position_tracker pt ON of.account_id = pt.account_id AND of.symbol = pt.symbol AND of.fill_seq = pt.last_fill_seq + 1
)
SELECT 
    of.order_id, of.account_id, of.symbol, of.quantity, of.executed_price as sell_price, pt.avg_cost as average_cost,
    (of.executed_price - pt.avg_cost) * of.quantity as realised_pnl, of.executed_on as realised_at
FROM order_fills of
JOIN position_tracker pt ON of.account_id = pt.account_id AND of.symbol = pt.symbol AND of.fill_seq = pt.last_fill_seq + 1
WHERE of.side = 'SELL' AND pt.held_qty >= of.quantity
ON CONFLICT (order_id) DO NOTHING;

COMMIT;