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
    (9, 'frank_active_usr', 'frank@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'CUSTOMER', 'ACTIVE', 'PUSH'),
    (10, 'lion', 'lion@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'CUSTOMER', 'ACTIVE', 'EMAIL'),
    (11, 'tiger', 'tiger@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'CUSTOMER', 'ACTIVE', 'EMAIL'),
    (12, 'elephant', 'elephant@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'CUSTOMER', 'ACTIVE', 'EMAIL'),
    (13, 'panda', 'panda@example.com', '$2a$10$8vjGinIehruRYojYZxJ0B.tJhEus.xaoPfoi.qXl5ZrilfoER8BCy', 'CUSTOMER', 'ACTIVE', 'EMAIL')
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
    (9, 'ACC-USD-FRANK09', 9, 4000.00, 'USD', 'SUSPENDED', 0),
    (10, 'ACC-INR-LION10', 10, 925000.00, 'INR', 'ACTIVE', 0),
    (11, 'ACC-INR-TIGER11', 11, 880000.00, 'INR', 'ACTIVE', 0),
    (12, 'ACC-INR-ELEPHANT12', 12, 1045000.00, 'INR', 'ACTIVE', 0),
    (13, 'ACC-INR-PANDA13', 13, 990000.00, 'INR', 'ACTIVE', 0)
ON CONFLICT (id) DO NOTHING;

-- Reset sequence for accounts table
SELECT setval('accounts_id_seq', (SELECT MAX(id) FROM accounts));

-- Seed User Positions
INSERT INTO positions (id, account_id, symbol, quantity, average_cost, version) VALUES
    (1, 2, 'AAPL', 15, 220.5000, 0),
    (2, 2, 'NVDA', 25, 165.0000, 0),
    (3, 3, 'MSFT', 10, 495.2500, 0),
    (100, 2, 'TSLA', 10, 200.0000, 0),
    (101, 10, 'AAPL', 60, 299.2400, 0),
    (102, 10, 'GOOGL', 35, 373.2500, 0),
    (103, 10, 'INFY.NS', 90, 1143.6000, 0),
    (104, 10, 'META', 50, 600.2100, 0),
    (105, 10, 'MSFT', 50, 393.8300, 0),
    (106, 10, 'NVDA', 80, 207.4100, 0),
    (107, 10, 'RELIANCE.NS', 40, 1328.8000, 0),
    (108, 11, 'AAPL', 50, 299.2400, 0),
    (109, 11, 'GOOGL', 30, 373.2500, 0),
    (110, 11, 'INFY.NS', 80, 1143.6000, 0),
    (111, 11, 'META', 40, 600.2100, 0),
    (112, 11, 'MSFT', 45, 393.8300, 0),
    (113, 11, 'NVDA', 70, 207.4100, 0),
    (114, 11, 'RELIANCE.NS', 35, 1328.8000, 0),
    (115, 12, 'AAPL', 65, 299.2400, 0),
    (116, 12, 'GOOGL', 40, 373.2500, 0),
    (117, 12, 'INFY.NS', 95, 1143.6000, 0),
    (118, 12, 'META', 45, 600.2100, 0),
    (119, 12, 'MSFT', 55, 393.8300, 0),
    (120, 12, 'NVDA', 85, 207.4100, 0),
    (121, 12, 'RELIANCE.NS', 45, 1328.8000, 0),
    (122, 13, 'AAPL', 70, 299.2400, 0),
    (123, 13, 'GOOGL', 45, 373.2500, 0),
    (124, 13, 'INFY.NS', 100, 1143.6000, 0),
    (125, 13, 'META', 55, 600.2100, 0),
    (126, 13, 'MSFT', 60, 393.8300, 0),
    (127, 13, 'NVDA', 90, 207.4100, 0),
    (128, 13, 'RELIANCE.NS', 50, 1328.8000, 0)
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
    ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', 3, 'MSFT', 'BUY', 'MARKET', 10, NULL, 495.2500, CURRENT_TIMESTAMP, 'FILLED', 'seed-order-jane-msft-1'),
    ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380a44', 2, 'AAPL', 'BUY', 'MARKET', 10, NULL, 150.0000, CURRENT_TIMESTAMP - INTERVAL '30 days', 'FILLED', 'seed-order-john-aapl-past-1'),
    ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55', 2, 'AAPL', 'SELL', 'MARKET', 10, NULL, 180.0000, CURRENT_TIMESTAMP - INTERVAL '25 days', 'FILLED', 'seed-order-john-aapl-past-2'),
    ('f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a66', 2, 'TSLA', 'BUY', 'MARKET', 20, NULL, 200.0000, CURRENT_TIMESTAMP - INTERVAL '20 days', 'FILLED', 'seed-order-john-tsla-past-1'),
    ('a1eebc99-9c0b-4ef8-bb6d-6bb9bd380a77', 2, 'TSLA', 'SELL', 'MARKET', 10, NULL, 250.0000, CURRENT_TIMESTAMP - INTERVAL '15 days', 'FILLED', 'seed-order-john-tsla-past-2'),
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a88', 2, 'MSFT', 'BUY', 'MARKET', 5, NULL, 300.0000, CURRENT_TIMESTAMP - INTERVAL '10 days', 'FILLED', 'seed-order-john-msft-past-1'),
    ('c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a99', 2, 'MSFT', 'SELL', 'MARKET', 5, NULL, 320.0000, CURRENT_TIMESTAMP - INTERVAL '5 days', 'FILLED', 'seed-order-john-msft-past-2'),
    ('10000000-0000-4000-8000-000000001001', 10, 'AAPL', 'BUY', 'MARKET', 90, NULL, 299.2400, '2026-06-16T10:00:00Z', 'FILLED', 'seed-order-lion-aapl-buy-1'),
    ('10000000-0000-4000-8000-000000001002', 10, 'GOOGL', 'BUY', 'MARKET', 55, NULL, 373.2500, '2026-06-16T10:05:00Z', 'FILLED', 'seed-order-lion-googl-buy-1'),
    ('10000000-0000-4000-8000-000000001003', 10, 'INFY.NS', 'BUY', 'MARKET', 140, NULL, 1143.6000, '2026-06-16T10:10:00Z', 'FILLED', 'seed-order-lion-infy-buy-1'),
    ('10000000-0000-4000-8000-000000001004', 10, 'META', 'BUY', 'MARKET', 80, NULL, 600.2100, '2026-06-16T10:15:00Z', 'FILLED', 'seed-order-lion-meta-buy-1'),
    ('10000000-0000-4000-8000-000000001005', 10, 'MSFT', 'BUY', 'MARKET', 70, NULL, 393.8300, '2026-06-16T10:20:00Z', 'FILLED', 'seed-order-lion-msft-buy-1'),
    ('10000000-0000-4000-8000-000000001006', 10, 'NVDA', 'BUY', 'MARKET', 120, NULL, 207.4100, '2026-06-16T10:25:00Z', 'FILLED', 'seed-order-lion-nvda-buy-1'),
    ('10000000-0000-4000-8000-000000001007', 10, 'RELIANCE.NS', 'BUY', 'MARKET', 65, NULL, 1328.8000, '2026-06-16T10:30:00Z', 'FILLED', 'seed-order-lion-reliance-buy-1'),
    ('10000000-0000-4000-8000-000000001008', 10, 'AAPL', 'SELL', 'MARKET', 30, NULL, 332.4100, '2026-09-16T10:00:00Z', 'FILLED', 'seed-order-lion-aapl-sell-1'),
    ('10000000-0000-4000-8000-000000001009', 10, 'GOOGL', 'SELL', 'MARKET', 20, NULL, 342.8700, '2026-09-16T10:05:00Z', 'FILLED', 'seed-order-lion-googl-sell-1'),
    ('10000000-0000-4000-8000-000000001010', 10, 'INFY.NS', 'SELL', 'MARKET', 50, NULL, 1060.0000, '2026-09-16T10:10:00Z', 'FILLED', 'seed-order-lion-infy-sell-1'),
    ('10000000-0000-4000-8000-000000001011', 10, 'META', 'SELL', 'MARKET', 30, NULL, 673.3100, '2026-09-16T10:15:00Z', 'FILLED', 'seed-order-lion-meta-sell-1'),
    ('10000000-0000-4000-8000-000000001012', 10, 'MSFT', 'SELL', 'MARKET', 20, NULL, 490.3000, '2026-09-16T10:20:00Z', 'FILLED', 'seed-order-lion-msft-sell-1'),
    ('10000000-0000-4000-8000-000000001013', 10, 'NVDA', 'SELL', 'MARKET', 40, NULL, 213.9000, '2026-09-16T10:25:00Z', 'FILLED', 'seed-order-lion-nvda-sell-1'),
    ('10000000-0000-4000-8000-000000001014', 10, 'RELIANCE.NS', 'SELL', 'MARKET', 25, NULL, 1240.0000, '2026-09-16T10:30:00Z', 'FILLED', 'seed-order-lion-reliance-sell-1'),
    ('11000000-0000-4000-8000-000000001101', 11, 'AAPL', 'BUY', 'MARKET', 80, NULL, 299.2400, '2026-06-16T11:00:00Z', 'FILLED', 'seed-order-tiger-aapl-buy-1'),
    ('11000000-0000-4000-8000-000000001102', 11, 'GOOGL', 'BUY', 'MARKET', 45, NULL, 373.2500, '2026-06-16T11:05:00Z', 'FILLED', 'seed-order-tiger-googl-buy-1'),
    ('11000000-0000-4000-8000-000000001103', 11, 'INFY.NS', 'BUY', 'MARKET', 130, NULL, 1143.6000, '2026-06-16T11:10:00Z', 'FILLED', 'seed-order-tiger-infy-buy-1'),
    ('11000000-0000-4000-8000-000000001104', 11, 'META', 'BUY', 'MARKET', 70, NULL, 600.2100, '2026-06-16T11:15:00Z', 'FILLED', 'seed-order-tiger-meta-buy-1'),
    ('11000000-0000-4000-8000-000000001105', 11, 'MSFT', 'BUY', 'MARKET', 65, NULL, 393.8300, '2026-06-16T11:20:00Z', 'FILLED', 'seed-order-tiger-msft-buy-1'),
    ('11000000-0000-4000-8000-000000001106', 11, 'NVDA', 'BUY', 'MARKET', 110, NULL, 207.4100, '2026-06-16T11:25:00Z', 'FILLED', 'seed-order-tiger-nvda-buy-1'),
    ('11000000-0000-4000-8000-000000001107', 11, 'RELIANCE.NS', 'BUY', 'MARKET', 55, NULL, 1328.8000, '2026-06-16T11:30:00Z', 'FILLED', 'seed-order-tiger-reliance-buy-1'),
    ('11000000-0000-4000-8000-000000001108', 11, 'AAPL', 'SELL', 'MARKET', 30, NULL, 332.4100, '2026-09-16T11:00:00Z', 'FILLED', 'seed-order-tiger-aapl-sell-1'),
    ('11000000-0000-4000-8000-000000001109', 11, 'GOOGL', 'SELL', 'MARKET', 15, NULL, 342.8700, '2026-09-16T11:05:00Z', 'FILLED', 'seed-order-tiger-googl-sell-1'),
    ('11000000-0000-4000-8000-000000001110', 11, 'INFY.NS', 'SELL', 'MARKET', 50, NULL, 1060.0000, '2026-09-16T11:10:00Z', 'FILLED', 'seed-order-tiger-infy-sell-1'),
    ('11000000-0000-4000-8000-000000001111', 11, 'META', 'SELL', 'MARKET', 30, NULL, 673.3100, '2026-09-16T11:15:00Z', 'FILLED', 'seed-order-tiger-meta-sell-1'),
    ('11000000-0000-4000-8000-000000001112', 11, 'MSFT', 'SELL', 'MARKET', 20, NULL, 490.3000, '2026-09-16T11:20:00Z', 'FILLED', 'seed-order-tiger-msft-sell-1'),
    ('11000000-0000-4000-8000-000000001113', 11, 'NVDA', 'SELL', 'MARKET', 40, NULL, 213.9000, '2026-09-16T11:25:00Z', 'FILLED', 'seed-order-tiger-nvda-sell-1'),
    ('11000000-0000-4000-8000-000000001114', 11, 'RELIANCE.NS', 'SELL', 'MARKET', 20, NULL, 1240.0000, '2026-09-16T11:30:00Z', 'FILLED', 'seed-order-tiger-reliance-sell-1'),
    ('12000000-0000-4000-8000-000000001201', 12, 'AAPL', 'BUY', 'MARKET', 95, NULL, 299.2400, '2026-06-16T12:00:00Z', 'FILLED', 'seed-order-elephant-aapl-buy-1'),
    ('12000000-0000-4000-8000-000000001202', 12, 'GOOGL', 'BUY', 'MARKET', 60, NULL, 373.2500, '2026-06-16T12:05:00Z', 'FILLED', 'seed-order-elephant-googl-buy-1'),
    ('12000000-0000-4000-8000-000000001203', 12, 'INFY.NS', 'BUY', 'MARKET', 145, NULL, 1143.6000, '2026-06-16T12:10:00Z', 'FILLED', 'seed-order-elephant-infy-buy-1'),
    ('12000000-0000-4000-8000-000000001204', 12, 'META', 'BUY', 'MARKET', 75, NULL, 600.2100, '2026-06-16T12:15:00Z', 'FILLED', 'seed-order-elephant-meta-buy-1'),
    ('12000000-0000-4000-8000-000000001205', 12, 'MSFT', 'BUY', 'MARKET', 80, NULL, 393.8300, '2026-06-16T12:20:00Z', 'FILLED', 'seed-order-elephant-msft-buy-1'),
    ('12000000-0000-4000-8000-000000001206', 12, 'NVDA', 'BUY', 'MARKET', 130, NULL, 207.4100, '2026-06-16T12:25:00Z', 'FILLED', 'seed-order-elephant-nvda-buy-1'),
    ('12000000-0000-4000-8000-000000001207', 12, 'RELIANCE.NS', 'BUY', 'MARKET', 70, NULL, 1328.8000, '2026-06-16T12:30:00Z', 'FILLED', 'seed-order-elephant-reliance-buy-1'),
    ('12000000-0000-4000-8000-000000001208', 12, 'AAPL', 'SELL', 'MARKET', 30, NULL, 332.4100, '2026-09-16T12:00:00Z', 'FILLED', 'seed-order-elephant-aapl-sell-1'),
    ('12000000-0000-4000-8000-000000001209', 12, 'GOOGL', 'SELL', 'MARKET', 20, NULL, 342.8700, '2026-09-16T12:05:00Z', 'FILLED', 'seed-order-elephant-googl-sell-1'),
    ('12000000-0000-4000-8000-000000001210', 12, 'INFY.NS', 'SELL', 'MARKET', 50, NULL, 1060.0000, '2026-09-16T12:10:00Z', 'FILLED', 'seed-order-elephant-infy-sell-1'),
    ('12000000-0000-4000-8000-000000001211', 12, 'META', 'SELL', 'MARKET', 30, NULL, 673.3100, '2026-09-16T12:15:00Z', 'FILLED', 'seed-order-elephant-meta-sell-1'),
    ('12000000-0000-4000-8000-000000001212', 12, 'MSFT', 'SELL', 'MARKET', 25, NULL, 490.3000, '2026-09-16T12:20:00Z', 'FILLED', 'seed-order-elephant-msft-sell-1'),
    ('12000000-0000-4000-8000-000000001213', 12, 'NVDA', 'SELL', 'MARKET', 45, NULL, 213.9000, '2026-09-16T12:25:00Z', 'FILLED', 'seed-order-elephant-nvda-sell-1'),
    ('12000000-0000-4000-8000-000000001214', 12, 'RELIANCE.NS', 'SELL', 'MARKET', 25, NULL, 1240.0000, '2026-09-16T12:30:00Z', 'FILLED', 'seed-order-elephant-reliance-sell-1'),
    ('13000000-0000-4000-8000-000000001301', 13, 'AAPL', 'BUY', 'MARKET', 100, NULL, 299.2400, '2026-06-16T13:00:00Z', 'FILLED', 'seed-order-panda-aapl-buy-1'),
    ('13000000-0000-4000-8000-000000001302', 13, 'GOOGL', 'BUY', 'MARKET', 65, NULL, 373.2500, '2026-06-16T13:05:00Z', 'FILLED', 'seed-order-panda-googl-buy-1'),
    ('13000000-0000-4000-8000-000000001303', 13, 'INFY.NS', 'BUY', 'MARKET', 150, NULL, 1143.6000, '2026-06-16T13:10:00Z', 'FILLED', 'seed-order-panda-infy-buy-1'),
    ('13000000-0000-4000-8000-000000001304', 13, 'META', 'BUY', 'MARKET', 85, NULL, 600.2100, '2026-06-16T13:15:00Z', 'FILLED', 'seed-order-panda-meta-buy-1'),
    ('13000000-0000-4000-8000-000000001305', 13, 'MSFT', 'BUY', 'MARKET', 90, NULL, 393.8300, '2026-06-16T13:20:00Z', 'FILLED', 'seed-order-panda-msft-buy-1'),
    ('13000000-0000-4000-8000-000000001306', 13, 'NVDA', 'BUY', 'MARKET', 140, NULL, 207.4100, '2026-06-16T13:25:00Z', 'FILLED', 'seed-order-panda-nvda-buy-1'),
    ('13000000-0000-4000-8000-000000001307', 13, 'RELIANCE.NS', 'BUY', 'MARKET', 75, NULL, 1328.8000, '2026-06-16T13:30:00Z', 'FILLED', 'seed-order-panda-reliance-buy-1'),
    ('13000000-0000-4000-8000-000000001308', 13, 'AAPL', 'SELL', 'MARKET', 30, NULL, 332.4100, '2026-09-16T13:00:00Z', 'FILLED', 'seed-order-panda-aapl-sell-1'),
    ('13000000-0000-4000-8000-000000001309', 13, 'GOOGL', 'SELL', 'MARKET', 20, NULL, 342.8700, '2026-09-16T13:05:00Z', 'FILLED', 'seed-order-panda-googl-sell-1'),
    ('13000000-0000-4000-8000-000000001310', 13, 'INFY.NS', 'SELL', 'MARKET', 50, NULL, 1060.0000, '2026-09-16T13:10:00Z', 'FILLED', 'seed-order-panda-infy-sell-1'),
    ('13000000-0000-4000-8000-000000001311', 13, 'META', 'SELL', 'MARKET', 30, NULL, 673.3100, '2026-09-16T13:15:00Z', 'FILLED', 'seed-order-panda-meta-sell-1'),
    ('13000000-0000-4000-8000-000000001312', 13, 'MSFT', 'SELL', 'MARKET', 30, NULL, 490.3000, '2026-09-16T13:20:00Z', 'FILLED', 'seed-order-panda-msft-sell-1'),
    ('13000000-0000-4000-8000-000000001313', 13, 'NVDA', 'SELL', 'MARKET', 50, NULL, 213.9000, '2026-09-16T13:25:00Z', 'FILLED', 'seed-order-panda-nvda-sell-1'),
    ('13000000-0000-4000-8000-000000001314', 13, 'RELIANCE.NS', 'SELL', 'MARKET', 25, NULL, 1240.0000, '2026-09-16T13:30:00Z', 'FILLED', 'seed-order-panda-reliance-sell-1')
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