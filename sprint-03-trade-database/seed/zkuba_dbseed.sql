BEGIN;

-- Seed Additional Tradable Instruments
INSERT INTO instruments (symbol, display_name, asset_class, currency, tradable) VALUES
    ('GOOGL', 'Alphabet Inc.', 'EQUITY', 'USD', TRUE),
    ('AMZN', 'Amazon.com Inc.', 'EQUITY', 'USD', TRUE),
    ('META', 'Meta Platforms Inc.', 'EQUITY', 'USD', TRUE),
    ('ACME', 'Acme Corporation', 'EQUITY', 'USD', TRUE),
    ('INFY.NS', 'Infosys Limited', 'EQUITY', 'INR', TRUE)
ON CONFLICT (symbol) DO NOTHING;

-- Seed Sample Users (Password for all seeded users is: password123)
-- BCrypt hash for 'password123': $2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy
INSERT INTO users (id, username, email, password_hash, role, status) VALUES
    (1, 'admin', 'admin@trading.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'ROLE_ADMIN', 'ACTIVE'),
    (2, 'john_doe', 'john@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'ROLE_USER', 'ACTIVE'),
    (3, 'jane_smith', 'jane@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'ROLE_USER', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- Reset sequence for users table so new registrations start after seeded IDs
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));

-- Seed Trading Accounts
INSERT INTO accounts (id, account_number, user_id, cash_balance, currency, status, version) VALUES
    (1, 'ACC-USD-ADMIN', 1, 1000000.00, 'USD', 'ACTIVE', 0),
    (2, 'ACC-USD-JOHN01', 2, 45000.00, 'USD', 'ACTIVE', 0),
    (3, 'ACC-USD-JANE02', 3, 28000.00, 'USD', 'ACTIVE', 0)
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
INSERT INTO orders (id, account_id, symbol, side, order_type, quantity, limit_price, executed_price, status, idempotency_key) VALUES
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 2, 'AAPL', 'BUY', 'MARKET', 15, NULL, 220.5000, 'FILLED', 'seed-order-john-aapl-1'),
    ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 2, 'NVDA', 'BUY', 'MARKET', 25, NULL, 165.0000, 'FILLED', 'seed-order-john-nvda-1'),
    ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', 3, 'MSFT', 'BUY', 'MARKET', 10, NULL, 495.2500, 'FILLED', 'seed-order-jane-msft-1')
ON CONFLICT (id) DO NOTHING;

COMMIT;