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
    (101, 10, 'INFY.NS', 80, 1480.0000, 0),
    (102, 10, 'RELIANCE.NS', 50, 2725.0000, 0),
    (103, 10, 'TCS.NS', 60, 4010.0000, 0),
    (104, 10, 'HDFCBANK.NS', 100, 1588.0000, 0),
    (105, 10, 'ICICIBANK.NS', 100, 1095.0000, 0),
    (106, 10, 'SBIN.NS', 120, 760.0000, 0),
    (107, 11, 'INFY.NS', 100, 1515.0000, 0),
    (108, 11, 'RELIANCE.NS', 30, 2810.0000, 0),
    (109, 11, 'TCS.NS', 50, 3895.0000, 0),
    (110, 11, 'HDFCBANK.NS', 60, 1620.0000, 0),
    (111, 11, 'ICICIBANK.NS', 80, 1042.0000, 0),
    (112, 11, 'SBIN.NS', 100, 742.0000, 0),
    (113, 12, 'INFY.NS', 60, 1462.0000, 0),
    (114, 12, 'RELIANCE.NS', 60, 2748.0000, 0),
    (115, 12, 'TCS.NS', 60, 3955.0000, 0),
    (116, 12, 'HDFCBANK.NS', 100, 1540.0000, 0),
    (117, 12, 'ICICIBANK.NS', 100, 1068.0000, 0),
    (118, 12, 'SBIN.NS', 100, 735.0000, 0),
    (119, 13, 'INFY.NS', 150, 1498.0000, 0),
    (120, 13, 'RELIANCE.NS', 60, 2782.0000, 0),
    (121, 13, 'TCS.NS', 50, 4052.0000, 0),
    (122, 13, 'HDFCBANK.NS', 100, 1572.0000, 0),
    (123, 13, 'ICICIBANK.NS', 100, 1078.0000, 0),
    (124, 13, 'SBIN.NS', 120, 748.0000, 0)
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
    ('10000000-0000-4000-8000-0000000000d1', 10, 'INFY.NS', 'BUY', 'MARKET', 30, NULL, 1442.0000, '2025-11-06T10:00:00Z', 'FILLED', 'seed-order-lion-infy-pre-buy-1'),
    ('10000000-0000-4000-8000-0000000000d2', 10, 'INFY.NS', 'SELL', 'MARKET', 30, NULL, 1491.5000, '2025-11-26T10:10:00Z', 'FILLED', 'seed-order-lion-infy-pre-sell-1'),
    ('10000000-0000-4000-8000-0000000000d3', 10, 'RELIANCE.NS', 'BUY', 'MARKET', 18, NULL, 2668.0000, '2025-11-12T10:15:00Z', 'FILLED', 'seed-order-lion-reliance-pre-buy-1'),
    ('10000000-0000-4000-8000-0000000000d4', 10, 'RELIANCE.NS', 'SELL', 'MARKET', 18, NULL, 2749.2500, '2025-12-02T10:20:00Z', 'FILLED', 'seed-order-lion-reliance-pre-sell-1'),
    ('10000000-0000-4000-8000-0000000000d5', 10, 'TCS.NS', 'BUY', 'MARKET', 24, NULL, 3815.0000, '2025-11-18T09:55:00Z', 'FILLED', 'seed-order-lion-tcs-pre-buy-1'),
    ('10000000-0000-4000-8000-0000000000d6', 10, 'TCS.NS', 'SELL', 'MARKET', 24, NULL, 3872.0000, '2025-12-09T10:05:00Z', 'FILLED', 'seed-order-lion-tcs-pre-sell-1'),
    ('10000000-0000-4000-8000-0000000000d7', 10, 'HDFCBANK.NS', 'BUY', 'MARKET', 45, NULL, 1498.0000, '2025-12-03T10:20:00Z', 'FILLED', 'seed-order-lion-hdfc-pre-buy-1'),
    ('10000000-0000-4000-8000-0000000000d8', 10, 'HDFCBANK.NS', 'SELL', 'MARKET', 45, NULL, 1546.7500, '2025-12-23T10:25:00Z', 'FILLED', 'seed-order-lion-hdfc-pre-sell-1'),
    ('10000000-0000-4000-8000-0000000000d9', 10, 'ICICIBANK.NS', 'BUY', 'MARKET', 60, NULL, 998.0000, '2025-12-11T09:40:00Z', 'FILLED', 'seed-order-lion-icici-pre-buy-1'),
    ('10000000-0000-4000-8000-0000000000da', 10, 'ICICIBANK.NS', 'SELL', 'MARKET', 60, NULL, 1034.5000, '2025-12-30T09:45:00Z', 'FILLED', 'seed-order-lion-icici-pre-sell-1'),
    ('10000000-0000-4000-8000-0000000000db', 10, 'SBIN.NS', 'BUY', 'MARKET', 70, NULL, 701.0000, '2025-12-17T11:00:00Z', 'FILLED', 'seed-order-lion-sbin-pre-buy-1'),
    ('10000000-0000-4000-8000-0000000000dc', 10, 'SBIN.NS', 'SELL', 'MARKET', 70, NULL, 732.0000, '2026-01-03T11:10:00Z', 'FILLED', 'seed-order-lion-sbin-pre-sell-1'),
    ('10000000-0000-4000-8000-000000000101', 10, 'INFY.NS', 'BUY', 'MARKET', 120, NULL, 1480.0000, '2026-01-15T10:05:00Z', 'FILLED', 'seed-order-lion-infy-buy-1'),
    ('10000000-0000-4000-8000-000000000102', 10, 'RELIANCE.NS', 'BUY', 'MARKET', 70, NULL, 2725.0000, '2026-02-05T10:15:00Z', 'FILLED', 'seed-order-lion-reliance-buy-1'),
    ('10000000-0000-4000-8000-000000000103', 10, 'TCS.NS', 'BUY', 'MARKET', 90, NULL, 4010.0000, '2026-02-20T09:55:00Z', 'FILLED', 'seed-order-lion-tcs-buy-1'),
    ('10000000-0000-4000-8000-000000000104', 10, 'HDFCBANK.NS', 'BUY', 'MARKET', 150, NULL, 1588.0000, '2026-03-04T10:20:00Z', 'FILLED', 'seed-order-lion-hdfc-buy-1'),
    ('10000000-0000-4000-8000-000000000105', 10, 'ICICIBANK.NS', 'BUY', 'MARKET', 140, NULL, 1095.0000, '2026-03-18T09:40:00Z', 'FILLED', 'seed-order-lion-icici-buy-1'),
    ('10000000-0000-4000-8000-000000000106', 10, 'SBIN.NS', 'BUY', 'MARKET', 220, NULL, 760.0000, '2026-04-02T11:05:00Z', 'FILLED', 'seed-order-lion-sbin-buy-1'),
    ('10000000-0000-4000-8000-000000000107', 10, 'TCS.NS', 'SELL', 'MARKET', 30, NULL, 4185.0000, '2026-06-21T10:30:00Z', 'FILLED', 'seed-order-lion-tcs-sell-1'),
    ('10000000-0000-4000-8000-000000000108', 10, 'INFY.NS', 'SELL', 'MARKET', 40, NULL, 1615.2000, '2026-07-10T10:05:00Z', 'FILLED', 'seed-order-lion-infy-sell-1'),
    ('10000000-0000-4000-8000-000000000109', 10, 'RELIANCE.NS', 'SELL', 'MARKET', 20, NULL, 2915.7000, '2026-07-13T10:15:00Z', 'FILLED', 'seed-order-lion-reliance-sell-1'),
    ('10000000-0000-4000-8000-00000000010a', 10, 'HDFCBANK.NS', 'SELL', 'MARKET', 50, NULL, 1712.0000, '2026-08-12T11:10:00Z', 'FILLED', 'seed-order-lion-hdfc-sell-1'),
    ('10000000-0000-4000-8000-00000000010b', 10, 'ICICIBANK.NS', 'SELL', 'MARKET', 40, NULL, 1218.0000, '2026-09-02T09:45:00Z', 'FILLED', 'seed-order-lion-icici-sell-1'),
    ('10000000-0000-4000-8000-00000000010c', 10, 'SBIN.NS', 'SELL', 'MARKET', 100, NULL, 812.0000, '2026-09-18T11:20:00Z', 'FILLED', 'seed-order-lion-sbin-sell-1'),
    ('11000000-0000-4000-8000-0000000001d1', 11, 'INFY.NS', 'BUY', 'MARKET', 45, NULL, 1478.0000, '2025-11-05T10:00:00Z', 'FILLED', 'seed-order-tiger-infy-pre-buy-1'),
    ('11000000-0000-4000-8000-0000000001d2', 11, 'INFY.NS', 'SELL', 'MARKET', 45, NULL, 1456.5000, '2025-11-21T10:05:00Z', 'FILLED', 'seed-order-tiger-infy-pre-sell-1'),
    ('11000000-0000-4000-8000-0000000001d3', 11, 'RELIANCE.NS', 'BUY', 'MARKET', 22, NULL, 2695.0000, '2025-11-14T10:10:00Z', 'FILLED', 'seed-order-tiger-reliance-pre-buy-1'),
    ('11000000-0000-4000-8000-0000000001d4', 11, 'RELIANCE.NS', 'SELL', 'MARKET', 22, NULL, 2758.0000, '2025-12-04T10:20:00Z', 'FILLED', 'seed-order-tiger-reliance-pre-sell-1'),
    ('11000000-0000-4000-8000-0000000001d5', 11, 'TCS.NS', 'BUY', 'MARKET', 20, NULL, 3768.0000, '2025-11-19T09:50:00Z', 'FILLED', 'seed-order-tiger-tcs-pre-buy-1'),
    ('11000000-0000-4000-8000-0000000001d6', 11, 'TCS.NS', 'SELL', 'MARKET', 20, NULL, 3712.0000, '2025-12-12T09:55:00Z', 'FILLED', 'seed-order-tiger-tcs-pre-sell-1'),
    ('11000000-0000-4000-8000-0000000001d7', 11, 'HDFCBANK.NS', 'BUY', 'MARKET', 40, NULL, 1525.0000, '2025-12-02T10:15:00Z', 'FILLED', 'seed-order-tiger-hdfc-pre-buy-1'),
    ('11000000-0000-4000-8000-0000000001d8', 11, 'HDFCBANK.NS', 'SELL', 'MARKET', 40, NULL, 1511.0000, '2025-12-19T10:20:00Z', 'FILLED', 'seed-order-tiger-hdfc-pre-sell-1'),
    ('11000000-0000-4000-8000-0000000001d9', 11, 'ICICIBANK.NS', 'BUY', 'MARKET', 55, NULL, 989.0000, '2025-12-09T09:40:00Z', 'FILLED', 'seed-order-tiger-icici-pre-buy-1'),
    ('11000000-0000-4000-8000-0000000001da', 11, 'ICICIBANK.NS', 'SELL', 'MARKET', 55, NULL, 1024.0000, '2025-12-27T09:45:00Z', 'FILLED', 'seed-order-tiger-icici-pre-sell-1'),
    ('11000000-0000-4000-8000-0000000001db', 11, 'SBIN.NS', 'BUY', 'MARKET', 65, NULL, 688.0000, '2025-12-15T11:00:00Z', 'FILLED', 'seed-order-tiger-sbin-pre-buy-1'),
    ('11000000-0000-4000-8000-0000000001dc', 11, 'SBIN.NS', 'SELL', 'MARKET', 65, NULL, 709.5000, '2026-01-04T11:05:00Z', 'FILLED', 'seed-order-tiger-sbin-pre-sell-1'),
    ('11000000-0000-4000-8000-000000000101', 11, 'INFY.NS', 'BUY', 'MARKET', 180, NULL, 1515.0000, '2026-01-12T10:00:00Z', 'FILLED', 'seed-order-tiger-infy-buy-1'),
    ('11000000-0000-4000-8000-000000000102', 11, 'RELIANCE.NS', 'BUY', 'MARKET', 60, NULL, 2810.0000, '2026-02-01T10:10:00Z', 'FILLED', 'seed-order-tiger-reliance-buy-1'),
    ('11000000-0000-4000-8000-000000000103', 11, 'TCS.NS', 'BUY', 'MARKET', 75, NULL, 3895.0000, '2026-02-19T09:50:00Z', 'FILLED', 'seed-order-tiger-tcs-buy-1'),
    ('11000000-0000-4000-8000-000000000104', 11, 'HDFCBANK.NS', 'BUY', 'MARKET', 100, NULL, 1620.0000, '2026-03-09T10:20:00Z', 'FILLED', 'seed-order-tiger-hdfc-buy-1'),
    ('11000000-0000-4000-8000-000000000105', 11, 'ICICIBANK.NS', 'BUY', 'MARKET', 200, NULL, 1042.0000, '2026-03-25T09:40:00Z', 'FILLED', 'seed-order-tiger-icici-buy-1'),
    ('11000000-0000-4000-8000-000000000106', 11, 'SBIN.NS', 'BUY', 'MARKET', 160, NULL, 742.0000, '2026-04-10T11:00:00Z', 'FILLED', 'seed-order-tiger-sbin-buy-1'),
    ('11000000-0000-4000-8000-000000000107', 11, 'TCS.NS', 'SELL', 'MARKET', 25, NULL, 4088.0000, '2026-06-18T10:20:00Z', 'FILLED', 'seed-order-tiger-tcs-sell-1'),
    ('11000000-0000-4000-8000-000000000108', 11, 'INFY.NS', 'SELL', 'MARKET', 80, NULL, 1572.8500, '2026-07-06T10:05:00Z', 'FILLED', 'seed-order-tiger-infy-sell-1'),
    ('11000000-0000-4000-8000-000000000109', 11, 'RELIANCE.NS', 'SELL', 'MARKET', 30, NULL, 2866.2500, '2026-07-09T10:10:00Z', 'FILLED', 'seed-order-tiger-reliance-sell-1'),
    ('11000000-0000-4000-8000-00000000010a', 11, 'HDFCBANK.NS', 'SELL', 'MARKET', 40, NULL, 1688.0000, '2026-08-01T11:00:00Z', 'FILLED', 'seed-order-tiger-hdfc-sell-1'),
    ('11000000-0000-4000-8000-00000000010b', 11, 'SBIN.NS', 'SELL', 'MARKET', 60, NULL, 799.0000, '2026-08-28T11:10:00Z', 'FILLED', 'seed-order-tiger-sbin-sell-1'),
    ('11000000-0000-4000-8000-00000000010c', 11, 'ICICIBANK.NS', 'SELL', 'MARKET', 120, NULL, 1175.0000, '2026-09-05T09:45:00Z', 'FILLED', 'seed-order-tiger-icici-sell-1'),
    ('12000000-0000-4000-8000-0000000002d1', 12, 'INFY.NS', 'BUY', 'MARKET', 28, NULL, 1418.0000, '2025-11-10T10:00:00Z', 'FILLED', 'seed-order-elephant-infy-pre-buy-1'),
    ('12000000-0000-4000-8000-0000000002d2', 12, 'INFY.NS', 'SELL', 'MARKET', 28, NULL, 1461.2500, '2025-11-28T10:05:00Z', 'FILLED', 'seed-order-elephant-infy-pre-sell-1'),
    ('12000000-0000-4000-8000-0000000002d3', 12, 'RELIANCE.NS', 'BUY', 'MARKET', 20, NULL, 2682.0000, '2025-11-17T10:10:00Z', 'FILLED', 'seed-order-elephant-reliance-pre-buy-1'),
    ('12000000-0000-4000-8000-0000000002d4', 12, 'RELIANCE.NS', 'SELL', 'MARKET', 20, NULL, 2644.5000, '2025-12-05T10:20:00Z', 'FILLED', 'seed-order-elephant-reliance-pre-sell-1'),
    ('12000000-0000-4000-8000-0000000002d5', 12, 'TCS.NS', 'BUY', 'MARKET', 30, NULL, 3822.0000, '2025-11-24T09:50:00Z', 'FILLED', 'seed-order-elephant-tcs-pre-buy-1'),
    ('12000000-0000-4000-8000-0000000002d6', 12, 'TCS.NS', 'SELL', 'MARKET', 30, NULL, 3905.0000, '2025-12-16T10:00:00Z', 'FILLED', 'seed-order-elephant-tcs-pre-sell-1'),
    ('12000000-0000-4000-8000-0000000002d7', 12, 'HDFCBANK.NS', 'BUY', 'MARKET', 55, NULL, 1488.0000, '2025-12-04T10:15:00Z', 'FILLED', 'seed-order-elephant-hdfc-pre-buy-1'),
    ('12000000-0000-4000-8000-0000000002d8', 12, 'HDFCBANK.NS', 'SELL', 'MARKET', 55, NULL, 1539.5000, '2025-12-24T10:20:00Z', 'FILLED', 'seed-order-elephant-hdfc-pre-sell-1'),
    ('12000000-0000-4000-8000-0000000002d9', 12, 'ICICIBANK.NS', 'BUY', 'MARKET', 45, NULL, 1006.0000, '2025-12-12T09:35:00Z', 'FILLED', 'seed-order-elephant-icici-pre-buy-1'),
    ('12000000-0000-4000-8000-0000000002da', 12, 'ICICIBANK.NS', 'SELL', 'MARKET', 45, NULL, 1061.0000, '2025-12-31T09:40:00Z', 'FILLED', 'seed-order-elephant-icici-pre-sell-1'),
    ('12000000-0000-4000-8000-0000000002db', 12, 'SBIN.NS', 'BUY', 'MARKET', 80, NULL, 676.0000, '2025-12-20T10:55:00Z', 'FILLED', 'seed-order-elephant-sbin-pre-buy-1'),
    ('12000000-0000-4000-8000-0000000002dc', 12, 'SBIN.NS', 'SELL', 'MARKET', 80, NULL, 702.0000, '2026-01-06T11:05:00Z', 'FILLED', 'seed-order-elephant-sbin-pre-sell-1'),
    ('12000000-0000-4000-8000-000000000101', 12, 'INFY.NS', 'BUY', 'MARKET', 90, NULL, 1462.0000, '2026-01-20T10:00:00Z', 'FILLED', 'seed-order-elephant-infy-buy-1'),
    ('12000000-0000-4000-8000-000000000102', 12, 'RELIANCE.NS', 'BUY', 'MARKET', 85, NULL, 2748.0000, '2026-02-11T10:10:00Z', 'FILLED', 'seed-order-elephant-reliance-buy-1'),
    ('12000000-0000-4000-8000-000000000103', 12, 'TCS.NS', 'BUY', 'MARKET', 110, NULL, 3955.0000, '2026-02-27T09:50:00Z', 'FILLED', 'seed-order-elephant-tcs-buy-1'),
    ('12000000-0000-4000-8000-000000000104', 12, 'HDFCBANK.NS', 'BUY', 'MARKET', 180, NULL, 1540.0000, '2026-03-14T10:15:00Z', 'FILLED', 'seed-order-elephant-hdfc-buy-1'),
    ('12000000-0000-4000-8000-000000000105', 12, 'ICICIBANK.NS', 'BUY', 'MARKET', 130, NULL, 1068.0000, '2026-03-29T09:40:00Z', 'FILLED', 'seed-order-elephant-icici-buy-1'),
    ('12000000-0000-4000-8000-000000000106', 12, 'SBIN.NS', 'BUY', 'MARKET', 240, NULL, 735.0000, '2026-04-15T11:00:00Z', 'FILLED', 'seed-order-elephant-sbin-buy-1'),
    ('12000000-0000-4000-8000-000000000107', 12, 'TCS.NS', 'SELL', 'MARKET', 50, NULL, 4122.0000, '2026-06-30T10:20:00Z', 'FILLED', 'seed-order-elephant-tcs-sell-1'),
    ('12000000-0000-4000-8000-000000000108', 12, 'INFY.NS', 'SELL', 'MARKET', 30, NULL, 1609.7500, '2026-07-09T10:05:00Z', 'FILLED', 'seed-order-elephant-infy-sell-1'),
    ('12000000-0000-4000-8000-000000000109', 12, 'RELIANCE.NS', 'SELL', 'MARKET', 25, NULL, 2884.6000, '2026-07-10T10:10:00Z', 'FILLED', 'seed-order-elephant-reliance-sell-1'),
    ('12000000-0000-4000-8000-00000000010a', 12, 'HDFCBANK.NS', 'SELL', 'MARKET', 80, NULL, 1705.0000, '2026-08-14T11:05:00Z', 'FILLED', 'seed-order-elephant-hdfc-sell-1'),
    ('12000000-0000-4000-8000-00000000010b', 12, 'ICICIBANK.NS', 'SELL', 'MARKET', 30, NULL, 1198.0000, '2026-09-11T09:45:00Z', 'FILLED', 'seed-order-elephant-icici-sell-1'),
    ('12000000-0000-4000-8000-00000000010c', 12, 'SBIN.NS', 'SELL', 'MARKET', 140, NULL, 805.0000, '2026-09-20T11:15:00Z', 'FILLED', 'seed-order-elephant-sbin-sell-1'),
    ('13000000-0000-4000-8000-0000000003d1', 13, 'INFY.NS', 'BUY', 'MARKET', 35, NULL, 1456.0000, '2025-11-04T10:00:00Z', 'FILLED', 'seed-order-panda-infy-pre-buy-1'),
    ('13000000-0000-4000-8000-0000000003d2', 13, 'INFY.NS', 'SELL', 'MARKET', 35, NULL, 1505.0000, '2025-11-24T10:05:00Z', 'FILLED', 'seed-order-panda-infy-pre-sell-1'),
    ('13000000-0000-4000-8000-0000000003d3', 13, 'RELIANCE.NS', 'BUY', 'MARKET', 24, NULL, 2701.0000, '2025-11-13T10:10:00Z', 'FILLED', 'seed-order-panda-reliance-pre-buy-1'),
    ('13000000-0000-4000-8000-0000000003d4', 13, 'RELIANCE.NS', 'SELL', 'MARKET', 24, NULL, 2796.2500, '2025-12-03T10:15:00Z', 'FILLED', 'seed-order-panda-reliance-pre-sell-1'),
    ('13000000-0000-4000-8000-0000000003d5', 13, 'TCS.NS', 'BUY', 'MARKET', 18, NULL, 3888.0000, '2025-11-21T09:50:00Z', 'FILLED', 'seed-order-panda-tcs-pre-buy-1'),
    ('13000000-0000-4000-8000-0000000003d6', 13, 'TCS.NS', 'SELL', 'MARKET', 18, NULL, 3949.0000, '2025-12-11T09:55:00Z', 'FILLED', 'seed-order-panda-tcs-pre-sell-1'),
    ('13000000-0000-4000-8000-0000000003d7', 13, 'HDFCBANK.NS', 'BUY', 'MARKET', 50, NULL, 1502.0000, '2025-12-01T10:15:00Z', 'FILLED', 'seed-order-panda-hdfc-pre-buy-1'),
    ('13000000-0000-4000-8000-0000000003d8', 13, 'HDFCBANK.NS', 'SELL', 'MARKET', 50, NULL, 1568.0000, '2025-12-20T10:20:00Z', 'FILLED', 'seed-order-panda-hdfc-pre-sell-1'),
    ('13000000-0000-4000-8000-0000000003d9', 13, 'ICICIBANK.NS', 'BUY', 'MARKET', 65, NULL, 1001.0000, '2025-12-10T09:40:00Z', 'FILLED', 'seed-order-panda-icici-pre-buy-1'),
    ('13000000-0000-4000-8000-0000000003da', 13, 'ICICIBANK.NS', 'SELL', 'MARKET', 65, NULL, 1042.0000, '2025-12-29T09:45:00Z', 'FILLED', 'seed-order-panda-icici-pre-sell-1'),
    ('13000000-0000-4000-8000-0000000003db', 13, 'SBIN.NS', 'BUY', 'MARKET', 75, NULL, 694.0000, '2025-12-18T11:00:00Z', 'FILLED', 'seed-order-panda-sbin-pre-buy-1'),
    ('13000000-0000-4000-8000-0000000003dc', 13, 'SBIN.NS', 'SELL', 'MARKET', 75, NULL, 726.5000, '2026-01-05T11:05:00Z', 'FILLED', 'seed-order-panda-sbin-pre-sell-1'),
    ('13000000-0000-4000-8000-000000000101', 13, 'INFY.NS', 'BUY', 'MARKET', 210, NULL, 1498.0000, '2026-01-08T10:00:00Z', 'FILLED', 'seed-order-panda-infy-buy-1'),
    ('13000000-0000-4000-8000-000000000102', 13, 'RELIANCE.NS', 'BUY', 'MARKET', 95, NULL, 2782.0000, '2026-02-08T10:10:00Z', 'FILLED', 'seed-order-panda-reliance-buy-1'),
    ('13000000-0000-4000-8000-000000000103', 13, 'TCS.NS', 'BUY', 'MARKET', 65, NULL, 4052.0000, '2026-02-24T09:50:00Z', 'FILLED', 'seed-order-panda-tcs-buy-1'),
    ('13000000-0000-4000-8000-000000000104', 13, 'HDFCBANK.NS', 'BUY', 'MARKET', 140, NULL, 1572.0000, '2026-03-11T10:15:00Z', 'FILLED', 'seed-order-panda-hdfc-buy-1'),
    ('13000000-0000-4000-8000-000000000105', 13, 'ICICIBANK.NS', 'BUY', 'MARKET', 170, NULL, 1078.0000, '2026-03-27T09:40:00Z', 'FILLED', 'seed-order-panda-icici-buy-1'),
    ('13000000-0000-4000-8000-000000000106', 13, 'SBIN.NS', 'BUY', 'MARKET', 200, NULL, 748.0000, '2026-04-18T11:05:00Z', 'FILLED', 'seed-order-panda-sbin-buy-1'),
    ('13000000-0000-4000-8000-000000000107', 13, 'TCS.NS', 'SELL', 'MARKET', 15, NULL, 4208.0000, '2026-06-24T10:25:00Z', 'FILLED', 'seed-order-panda-tcs-sell-1'),
    ('13000000-0000-4000-8000-000000000108', 13, 'INFY.NS', 'SELL', 'MARKET', 60, NULL, 1586.0500, '2026-07-08T10:05:00Z', 'FILLED', 'seed-order-panda-infy-sell-1'),
    ('13000000-0000-4000-8000-000000000109', 13, 'RELIANCE.NS', 'SELL', 'MARKET', 35, NULL, 2907.9500, '2026-07-14T10:10:00Z', 'FILLED', 'seed-order-panda-reliance-sell-1'),
    ('13000000-0000-4000-8000-00000000010a', 13, 'HDFCBANK.NS', 'SELL', 'MARKET', 40, NULL, 1696.0000, '2026-08-08T11:00:00Z', 'FILLED', 'seed-order-panda-hdfc-sell-1'),
    ('13000000-0000-4000-8000-00000000010b', 13, 'ICICIBANK.NS', 'SELL', 'MARKET', 70, NULL, 1210.0000, '2026-09-08T09:45:00Z', 'FILLED', 'seed-order-panda-icici-sell-1'),
    ('13000000-0000-4000-8000-00000000010c', 13, 'SBIN.NS', 'SELL', 'MARKET', 80, NULL, 808.0000, '2026-09-22T11:20:00Z', 'FILLED', 'seed-order-panda-sbin-sell-1')
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