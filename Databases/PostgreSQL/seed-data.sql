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
    (7, 'ICICIBANK.NS');

COMMIT;
