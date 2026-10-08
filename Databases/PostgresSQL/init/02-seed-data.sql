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

-- Seed Sample Users (Password for all seeded users is: Password123)
-- BCrypt hash for 'Password123': $2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6
INSERT INTO auth.users (id, username, email, password_hash, role, status, notification_channel) VALUES
    (1, 'admin', 'admin@trading.com', '$2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6', 'ADMIN', 'ACTIVE', 'PUSH'),
    (2, 'john_doe', 'john@example.com', '$2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6', 'CUSTOMER', 'ACTIVE', 'PUSH'),
    (3, 'jane_smith', 'jane@example.com', '$2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6', 'CUSTOMER', 'ACTIVE', 'PUSH'),
    (4, 'bob_suspended', 'bob@example.com', '$2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6', 'CUSTOMER', 'SUSPENDED', 'PUSH'),
    (5, 'alice_closed', 'alice@example.com', '$2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6', 'CUSTOMER', 'CLOSED', 'PUSH'),
    (6, 'charlie_closed_usr', 'charlie@example.com', '$2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6', 'CUSTOMER', 'CLOSED', 'PUSH'),
    (7, 'dave_suspended_usr', 'dave@example.com', '$2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6', 'CUSTOMER', 'SUSPENDED', 'PUSH'),
    (8, 'eve_active_usr', 'eve@example.com', '$2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6', 'CUSTOMER', 'ACTIVE', 'PUSH'),
    (9, 'frank_active_usr', 'frank@example.com', '$2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6', 'CUSTOMER', 'ACTIVE', 'PUSH'),
    (10, 'muru', 'muru@example.com', '$2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6', 'CUSTOMER', 'ACTIVE', 'PUSH'),
    (11, 'krish', 'krish@example.com', '$2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6', 'CUSTOMER', 'ACTIVE', 'PUSH'),
    (12, 'pranav', 'pranav@example.com', '$2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6', 'CUSTOMER', 'ACTIVE', 'PUSH'),
    (13, 'tanu', 'tanu@example.com', '$2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6', 'CUSTOMER', 'ACTIVE', 'PUSH'),
    (14, 'sowmya', 'sowmya@example.com', '$2b$10$vYd7O8NJMqixSBM5qamqv.wHYYRWNWkG.Wrj6THbR2jECjrupwWM6', 'CUSTOMER', 'ACTIVE', 'PUSH')
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
    (10, 'ACC-USD-MURU10', 10, 185000.00, 'USD', 'ACTIVE', 0),
    (11, 'ACC-USD-KRISH11', 11, 172500.00, 'USD', 'ACTIVE', 0),
    (12, 'ACC-USD-PRANAV12', 12, 210000.00, 'USD', 'ACTIVE', 0),
    (13, 'ACC-INR-TANU13', 13, 1450000.00, 'INR', 'ACTIVE', 0),
    (14, 'ACC-INR-SOWMYA14', 14, 1580000.00, 'INR', 'ACTIVE', 0)
ON CONFLICT (id) DO NOTHING;

-- Reset sequence for accounts table
SELECT setval('accounts_id_seq', (SELECT MAX(id) FROM accounts));

-- Seed User Positions
INSERT INTO positions (id, account_id, symbol, quantity, average_cost, version) VALUES
    (1, 2, 'AAPL', 15, 220.5000, 0),
    (2, 2, 'NVDA', 25, 165.0000, 0),
    (3, 3, 'MSFT', 10, 495.2500, 0),
    (100, 2, 'TSLA', 10, 200.0000, 0),
    (101, 10, 'AAPL', 60, 277.0650, 0),
    (102, 10, 'GOOGL', 35, 315.0657, 0),
    (103, 10, 'META', 50, 695.4730, 0),
    (104, 10, 'MSFT', 50, 455.2680, 0),
    (105, 10, 'NVDA', 80, 215.3675, 0),
    (106, 11, 'AAPL', 50, 273.2940, 0),
    (107, 11, 'GOOGL', 30, 314.1960, 0),
    (108, 11, 'META', 40, 692.3725, 0),
    (109, 11, 'MSFT', 45, 447.0422, 0),
    (110, 11, 'NVDA', 70, 214.2307, 0),
    (111, 12, 'AAPL', 70, 279.7586, 0),
    (112, 12, 'GOOGL', 45, 314.1960, 0),
    (113, 12, 'META', 55, 693.7818, 0),
    (114, 12, 'MSFT', 60, 449.9800, 0),
    (115, 12, 'NVDA', 90, 214.4833, 0),
    (116, 13, 'INFY.NS', 95, 1200.4552, 0),
    (117, 13, 'RELIANCE.NS', 45, 1279.1333, 0),
    (118, 14, 'INFY.NS', 100, 1191.1250, 0),
    (119, 14, 'RELIANCE.NS', 50, 1280.8800, 0)
ON CONFLICT (account_id, symbol) DO NOTHING;

-- Reset sequence for positions table
SELECT setval('positions_id_seq', (SELECT MAX(id) FROM positions));

-- Seed User Watchlists
INSERT INTO watchlists (id, user_id, name) VALUES
    (1, 2, 'Tech Leaders'),
    (2, 3, 'AI & Growth'),
    (3, 10, 'US Compounders'),
    (4, 11, 'Big Tech Radar'),
    (5, 12, 'Momentum Board'),
    (6, 13, 'India Core'),
    (7, 14, 'India Ideas')
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
    (2, 'AMZN'),
    (3, 'AMZN'),
    (3, 'TSLA'),
    (3, 'NFLX'),
    (3, 'AAPL'),
    (3, 'NVDA'),
    (4, 'META'),
    (4, 'MSFT'),
    (4, 'GOOGL'),
    (4, 'DIS'),
    (4, 'AMD'),
    (5, 'AAPL'),
    (5, 'GOOGL'),
    (5, 'NVDA'),
    (5, 'SBUX'),
    (5, 'NKE'),
    (6, 'INFY.NS'),
    (6, 'RELIANCE.NS'),
    (6, 'ITC.NS'),
    (6, 'LT.NS'),
    (6, 'HDFCBANK.NS'),
    (7, 'RELIANCE.NS'),
    (7, 'INFY.NS'),
    (7, 'BAJFINANCE.NS'),
    (7, 'ICICIBANK.NS'),
    (7, 'BHARTIARTL.NS')
ON CONFLICT (watchlist_id, symbol) DO NOTHING;

-- Seed Sample Filled Orders
INSERT INTO orders (id, account_id, symbol, side, order_type, quantity, limit_price, executed_price, executed_on, status, idempotency_key, created_at, updated_at) VALUES
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 2, 'AAPL', 'BUY', 'MARKET', 15, NULL, 220.5000, CURRENT_TIMESTAMP, 'FILLED', 'seed-order-john-aapl-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 2, 'NVDA', 'BUY', 'MARKET', 25, NULL, 165.0000, CURRENT_TIMESTAMP, 'FILLED', 'seed-order-john-nvda-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', 3, 'MSFT', 'BUY', 'MARKET', 10, NULL, 495.2500, CURRENT_TIMESTAMP, 'FILLED', 'seed-order-jane-msft-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380a44', 2, 'AAPL', 'BUY', 'MARKET', 10, NULL, 150.0000, CURRENT_TIMESTAMP - INTERVAL '30 days', 'FILLED', 'seed-order-john-aapl-past-1', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days'),
    ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55', 2, 'AAPL', 'SELL', 'MARKET', 10, NULL, 180.0000, CURRENT_TIMESTAMP - INTERVAL '25 days', 'FILLED', 'seed-order-john-aapl-past-2', CURRENT_TIMESTAMP - INTERVAL '25 days', CURRENT_TIMESTAMP - INTERVAL '25 days'),
    ('f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a66', 2, 'TSLA', 'BUY', 'MARKET', 20, NULL, 200.0000, CURRENT_TIMESTAMP - INTERVAL '20 days', 'FILLED', 'seed-order-john-tsla-past-1', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days'),
    ('a1eebc99-9c0b-4ef8-bb6d-6bb9bd380a77', 2, 'TSLA', 'SELL', 'MARKET', 10, NULL, 250.0000, CURRENT_TIMESTAMP - INTERVAL '15 days', 'FILLED', 'seed-order-john-tsla-past-2', CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '15 days'),
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a88', 2, 'MSFT', 'BUY', 'MARKET', 5, NULL, 300.0000, CURRENT_TIMESTAMP - INTERVAL '10 days', 'FILLED', 'seed-order-john-msft-past-1', CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '10 days'),
    ('c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a99', 2, 'MSFT', 'SELL', 'MARKET', 5, NULL, 320.0000, CURRENT_TIMESTAMP - INTERVAL '5 days', 'FILLED', 'seed-order-john-msft-past-2', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '5 days'),
    ('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380aa1', 2, 'MSFT', 'BUY', 'MARKET', 50, NULL, 280.0000, CURRENT_TIMESTAMP - INTERVAL '1 year', 'FILLED', 'seed-order-john-msft-1y-buy', CURRENT_TIMESTAMP - INTERVAL '1 year', CURRENT_TIMESTAMP - INTERVAL '1 year'),
    ('e1eebc99-9c0b-4ef8-bb6d-6bb9bd380aa2', 2, 'MSFT', 'SELL', 'MARKET', 50, NULL, 350.0000, CURRENT_TIMESTAMP - INTERVAL '360 days', 'FILLED', 'seed-order-john-msft-1y-sell', CURRENT_TIMESTAMP - INTERVAL '360 days', CURRENT_TIMESTAMP - INTERVAL '360 days'),
    ('f1eebc99-9c0b-4ef8-bb6d-6bb9bd380aa3', 2, 'META', 'BUY', 'MARKET', 20, NULL, 400.0000, CURRENT_TIMESTAMP - INTERVAL '7 days', 'FILLED', 'seed-order-john-meta-1w-buy', CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP - INTERVAL '7 days'),
    ('a2eebc99-9c0b-4ef8-bb6d-6bb9bd380aa4', 2, 'META', 'SELL', 'MARKET', 20, NULL, 420.0000, CURRENT_TIMESTAMP - INTERVAL '5 days', 'FILLED', 'seed-order-john-meta-1w-sell', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '5 days'),
    ('b2eebc99-9c0b-4ef8-bb6d-6bb9bd380aa5', 2, 'AMZN', 'BUY', 'MARKET', 100, NULL, 150.0000, CURRENT_TIMESTAMP - INTERVAL '2 days', 'FILLED', 'seed-order-john-amzn-1d-buy', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days'),
    ('c2eebc99-9c0b-4ef8-bb6d-6bb9bd380aa6', 2, 'AMZN', 'SELL', 'MARKET', 100, NULL, 145.0000, CURRENT_TIMESTAMP - INTERVAL '1 day', 'FILLED', 'seed-order-john-amzn-1d-sell', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day'),
    ('10000000-0000-4000-8000-000000000001', 10, 'AAPL', 'BUY', 'MARKET', 65, NULL, 258.2100, '2026-01-15T10:00:00Z', 'FILLED', 'seed-order-muru-aapl-buy-1', '2026-01-15T10:00:00Z', '2026-01-15T10:00:00Z'),
    ('10000000-0000-4000-8000-000000000002', 10, 'AAPL', 'SELL', 'MARKET', 20, NULL, 310.0300, '2026-08-18T10:00:00Z', 'FILLED', 'seed-order-muru-aapl-sell-1', '2026-08-18T10:00:00Z', '2026-08-18T10:00:00Z'),
    ('10000000-0000-4000-8000-000000000003', 10, 'AAPL', 'BUY', 'MARKET', 15, NULL, 333.6300, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-muru-aapl-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('10000000-0000-4000-8000-000000000004', 10, 'GOOGL', 'BUY', 'MARKET', 35, NULL, 302.0200, '2026-02-17T10:00:00Z', 'FILLED', 'seed-order-muru-googl-buy-1', '2026-02-17T10:00:00Z', '2026-02-17T10:00:00Z'),
    ('10000000-0000-4000-8000-000000000005', 10, 'GOOGL', 'SELL', 'MARKET', 10, NULL, 342.8700, '2026-09-16T10:00:00Z', 'FILLED', 'seed-order-muru-googl-sell-1', '2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z'),
    ('10000000-0000-4000-8000-000000000006', 10, 'GOOGL', 'BUY', 'MARKET', 10, NULL, 347.6800, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-muru-googl-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('10000000-0000-4000-8000-000000000007', 10, 'META', 'BUY', 'MARKET', 50, NULL, 676.8700, '2026-04-16T10:00:00Z', 'FILLED', 'seed-order-muru-meta-buy-1', '2026-04-16T10:00:00Z', '2026-04-16T10:00:00Z'),
    ('10000000-0000-4000-8000-000000000008', 10, 'META', 'SELL', 'MARKET', 15, NULL, 673.3100, '2026-09-16T10:00:00Z', 'FILLED', 'seed-order-muru-meta-sell-1', '2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z'),
    ('10000000-0000-4000-8000-000000000009', 10, 'META', 'BUY', 'MARKET', 15, NULL, 738.8800, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-muru-meta-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('10000000-0000-4000-8000-000000000010', 10, 'MSFT', 'BUY', 'MARKET', 50, NULL, 423.5400, '2026-05-18T10:00:00Z', 'FILLED', 'seed-order-muru-msft-buy-1', '2026-05-18T10:00:00Z', '2026-05-18T10:00:00Z'),
    ('10000000-0000-4000-8000-000000000011', 10, 'MSFT', 'SELL', 'MARKET', 15, NULL, 490.3000, '2026-09-16T10:00:00Z', 'FILLED', 'seed-order-muru-msft-sell-1', '2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z'),
    ('10000000-0000-4000-8000-000000000012', 10, 'MSFT', 'BUY', 'MARKET', 15, NULL, 529.3000, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-muru-msft-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('10000000-0000-4000-8000-000000000013', 10, 'NVDA', 'BUY', 'MARKET', 85, NULL, 207.4100, '2026-06-16T10:00:00Z', 'FILLED', 'seed-order-muru-nvda-buy-1', '2026-06-16T10:00:00Z', '2026-06-16T10:00:00Z'),
    ('10000000-0000-4000-8000-000000000014', 10, 'NVDA', 'SELL', 'MARKET', 25, NULL, 219.7400, '2026-08-18T10:00:00Z', 'FILLED', 'seed-order-muru-nvda-sell-1', '2026-08-18T10:00:00Z', '2026-08-18T10:00:00Z'),
    ('10000000-0000-4000-8000-000000000015', 10, 'NVDA', 'BUY', 'MARKET', 20, NULL, 239.2400, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-muru-nvda-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000001', 11, 'AAPL', 'BUY', 'MARKET', 55, NULL, 258.2100, '2026-01-15T10:00:00Z', 'FILLED', 'seed-order-krish-aapl-buy-1', '2026-01-15T10:00:00Z', '2026-01-15T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000002', 11, 'AAPL', 'SELL', 'MARKET', 15, NULL, 310.0300, '2026-08-18T10:00:00Z', 'FILLED', 'seed-order-krish-aapl-sell-1', '2026-08-18T10:00:00Z', '2026-08-18T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000003', 11, 'AAPL', 'BUY', 'MARKET', 10, NULL, 333.6300, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-krish-aapl-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000004', 11, 'GOOGL', 'BUY', 'MARKET', 32, NULL, 302.0200, '2026-02-17T10:00:00Z', 'FILLED', 'seed-order-krish-googl-buy-1', '2026-02-17T10:00:00Z', '2026-02-17T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000005', 11, 'GOOGL', 'SELL', 'MARKET', 10, NULL, 342.8700, '2026-09-16T10:00:00Z', 'FILLED', 'seed-order-krish-googl-sell-1', '2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000006', 11, 'GOOGL', 'BUY', 'MARKET', 8, NULL, 347.6800, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-krish-googl-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000007', 11, 'META', 'BUY', 'MARKET', 45, NULL, 676.8700, '2026-04-16T10:00:00Z', 'FILLED', 'seed-order-krish-meta-buy-1', '2026-04-16T10:00:00Z', '2026-04-16T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000008', 11, 'META', 'SELL', 'MARKET', 15, NULL, 673.3100, '2026-09-16T10:00:00Z', 'FILLED', 'seed-order-krish-meta-sell-1', '2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000009', 11, 'META', 'BUY', 'MARKET', 10, NULL, 738.8800, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-krish-meta-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000010', 11, 'MSFT', 'BUY', 'MARKET', 50, NULL, 423.5400, '2026-05-18T10:00:00Z', 'FILLED', 'seed-order-krish-msft-buy-1', '2026-05-18T10:00:00Z', '2026-05-18T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000011', 11, 'MSFT', 'SELL', 'MARKET', 15, NULL, 490.3000, '2026-09-16T10:00:00Z', 'FILLED', 'seed-order-krish-msft-sell-1', '2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000012', 11, 'MSFT', 'BUY', 'MARKET', 10, NULL, 529.3000, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-krish-msft-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000013', 11, 'NVDA', 'BUY', 'MARKET', 75, NULL, 207.4100, '2026-06-16T10:00:00Z', 'FILLED', 'seed-order-krish-nvda-buy-1', '2026-06-16T10:00:00Z', '2026-06-16T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000014', 11, 'NVDA', 'SELL', 'MARKET', 20, NULL, 219.7400, '2026-08-18T10:00:00Z', 'FILLED', 'seed-order-krish-nvda-sell-1', '2026-08-18T10:00:00Z', '2026-08-18T10:00:00Z'),
    ('11000000-0000-4000-8000-000000000015', 11, 'NVDA', 'BUY', 'MARKET', 15, NULL, 239.2400, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-krish-nvda-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000001', 12, 'AAPL', 'BUY', 'MARKET', 70, NULL, 258.2100, '2026-01-15T10:00:00Z', 'FILLED', 'seed-order-pranav-aapl-buy-1', '2026-01-15T10:00:00Z', '2026-01-15T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000002', 12, 'AAPL', 'SELL', 'MARKET', 20, NULL, 310.0300, '2026-08-18T10:00:00Z', 'FILLED', 'seed-order-pranav-aapl-sell-1', '2026-08-18T10:00:00Z', '2026-08-18T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000003', 12, 'AAPL', 'BUY', 'MARKET', 20, NULL, 333.6300, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-pranav-aapl-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000004', 12, 'GOOGL', 'BUY', 'MARKET', 48, NULL, 302.0200, '2026-02-17T10:00:00Z', 'FILLED', 'seed-order-pranav-googl-buy-1', '2026-02-17T10:00:00Z', '2026-02-17T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000005', 12, 'GOOGL', 'SELL', 'MARKET', 15, NULL, 342.8700, '2026-09-16T10:00:00Z', 'FILLED', 'seed-order-pranav-googl-sell-1', '2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000006', 12, 'GOOGL', 'BUY', 'MARKET', 12, NULL, 347.6800, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-pranav-googl-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000007', 12, 'META', 'BUY', 'MARKET', 60, NULL, 676.8700, '2026-04-16T10:00:00Z', 'FILLED', 'seed-order-pranav-meta-buy-1', '2026-04-16T10:00:00Z', '2026-04-16T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000008', 12, 'META', 'SELL', 'MARKET', 20, NULL, 673.3100, '2026-09-16T10:00:00Z', 'FILLED', 'seed-order-pranav-meta-sell-1', '2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000009', 12, 'META', 'BUY', 'MARKET', 15, NULL, 738.8800, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-pranav-meta-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000010', 12, 'MSFT', 'BUY', 'MARKET', 65, NULL, 423.5400, '2026-05-18T10:00:00Z', 'FILLED', 'seed-order-pranav-msft-buy-1', '2026-05-18T10:00:00Z', '2026-05-18T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000011', 12, 'MSFT', 'SELL', 'MARKET', 20, NULL, 490.3000, '2026-09-16T10:00:00Z', 'FILLED', 'seed-order-pranav-msft-sell-1', '2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000012', 12, 'MSFT', 'BUY', 'MARKET', 15, NULL, 529.3000, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-pranav-msft-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000013', 12, 'NVDA', 'BUY', 'MARKET', 95, NULL, 207.4100, '2026-06-16T10:00:00Z', 'FILLED', 'seed-order-pranav-nvda-buy-1', '2026-06-16T10:00:00Z', '2026-06-16T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000014', 12, 'NVDA', 'SELL', 'MARKET', 25, NULL, 219.7400, '2026-08-18T10:00:00Z', 'FILLED', 'seed-order-pranav-nvda-sell-1', '2026-08-18T10:00:00Z', '2026-08-18T10:00:00Z'),
    ('12000000-0000-4000-8000-000000000015', 12, 'NVDA', 'BUY', 'MARKET', 20, NULL, 239.2400, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-pranav-nvda-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('13000000-0000-4000-8000-000000000001', 13, 'INFY.NS', 'BUY', 'MARKET', 100, NULL, 1267.1000, '2026-03-18T10:00:00Z', 'FILLED', 'seed-order-tanu-infy-ns-buy-1', '2026-03-18T10:00:00Z', '2026-03-18T10:00:00Z'),
    ('13000000-0000-4000-8000-000000000002', 13, 'INFY.NS', 'SELL', 'MARKET', 30, NULL, 1115.0000, '2026-08-18T10:00:00Z', 'FILLED', 'seed-order-tanu-infy-ns-sell-1', '2026-08-18T10:00:00Z', '2026-08-18T10:00:00Z'),
    ('13000000-0000-4000-8000-000000000003', 13, 'INFY.NS', 'BUY', 'MARKET', 25, NULL, 1013.8500, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-tanu-infy-ns-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('13000000-0000-4000-8000-000000000004', 13, 'RELIANCE.NS', 'BUY', 'MARKET', 45, NULL, 1296.6000, '2026-07-16T10:00:00Z', 'FILLED', 'seed-order-tanu-reliance-ns-buy-1', '2026-07-16T10:00:00Z', '2026-07-16T10:00:00Z'),
    ('13000000-0000-4000-8000-000000000005', 13, 'RELIANCE.NS', 'SELL', 'MARKET', 10, NULL, 1240.0000, '2026-09-16T10:00:00Z', 'FILLED', 'seed-order-tanu-reliance-ns-sell-1', '2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z'),
    ('13000000-0000-4000-8000-000000000006', 13, 'RELIANCE.NS', 'BUY', 'MARKET', 10, NULL, 1218.0000, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-tanu-reliance-ns-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('14000000-0000-4000-8000-000000000001', 14, 'INFY.NS', 'BUY', 'MARKET', 100, NULL, 1267.1000, '2026-03-18T10:00:00Z', 'FILLED', 'seed-order-sowmya-infy-ns-buy-1', '2026-03-18T10:00:00Z', '2026-03-18T10:00:00Z'),
    ('14000000-0000-4000-8000-000000000002', 14, 'INFY.NS', 'SELL', 'MARKET', 30, NULL, 1115.0000, '2026-08-18T10:00:00Z', 'FILLED', 'seed-order-sowmya-infy-ns-sell-1', '2026-08-18T10:00:00Z', '2026-08-18T10:00:00Z'),
    ('14000000-0000-4000-8000-000000000003', 14, 'INFY.NS', 'BUY', 'MARKET', 30, NULL, 1013.8500, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-sowmya-infy-ns-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z'),
    ('14000000-0000-4000-8000-000000000004', 14, 'RELIANCE.NS', 'BUY', 'MARKET', 55, NULL, 1296.6000, '2026-07-16T10:00:00Z', 'FILLED', 'seed-order-sowmya-reliance-ns-buy-1', '2026-07-16T10:00:00Z', '2026-07-16T10:00:00Z'),
    ('14000000-0000-4000-8000-000000000005', 14, 'RELIANCE.NS', 'SELL', 'MARKET', 15, NULL, 1240.0000, '2026-09-16T10:00:00Z', 'FILLED', 'seed-order-sowmya-reliance-ns-sell-1', '2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z'),
    ('14000000-0000-4000-8000-000000000006', 14, 'RELIANCE.NS', 'BUY', 'MARKET', 10, NULL, 1218.0000, '2026-10-06T10:00:00Z', 'FILLED', 'seed-order-sowmya-reliance-ns-buy-2', '2026-10-06T10:00:00Z', '2026-10-06T10:00:00Z')
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