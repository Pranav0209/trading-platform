BEGIN;

INSERT INTO users (user_id,username,email,password_hash,status) VALUES
(1,'pranav','pranav@example.com','$2b$12$seedhash_user_001','ACTIVE'),
(2,'rahul','rahul@example.com','$2b$12$seedhash_user_002','ACTIVE'),
(3,'ananya','ananya@example.com','$2b$12$seedhash_user_003','ACTIVE'),
(4,'vikram','vikram@example.com','$2b$12$seedhash_user_004','ACTIVE'),
(5,'meera','meera@example.com','$2b$12$seedhash_user_005','ACTIVE');

INSERT INTO accounts (account_id,user_id,account_number,cash_balance,status,version) VALUES
(101,1,'TRD000101',250000,'ACTIVE',3),
(102,2,'TRD000102',175000,'ACTIVE',2),
(103,3,'TRD000103',500000,'ACTIVE',5),
(104,4,'TRD000104',125000,'ACTIVE',1),
(105,5,'TRD000105',300000,'ACTIVE',4);

INSERT INTO instruments (instrument_id,symbol,isin,company_name,asset_class,exchange,currency,status) VALUES
(1001,'RELIANCE','INE002A01018','Reliance Industries Limited','EQUITY','NSE','INR','ACTIVE'),
(1002,'TCS','INE467B01029','Tata Consultancy Services Limited','EQUITY','NSE','INR','ACTIVE'),
(1003,'INFY','INE009A01021','Infosys Limited','EQUITY','NSE','INR','ACTIVE'),
(1004,'HDFCBANK','INE040A01034','HDFC Bank Limited','EQUITY','NSE','INR','ACTIVE'),
(1005,'ICICIBANK','INE090A01021','ICICI Bank Limited','EQUITY','NSE','INR','ACTIVE');

INSERT INTO orders
(order_id,account_id,instrument_id,order_type,order_side,quantity,filled_quantity,limit_price,executed_price,status,brokerage_amount,idempotency_key,placed_at,updated_at,completed_at,cancelled_at,version)
VALUES
(5001,101,1001,'LIMIT','BUY',100,100,1400,1398.50,'FILLED',20,'idem-5001','2026-08-18 09:30:00','2026-08-18 09:30:08','2026-08-18 09:30:08',NULL,1),
(5002,101,1002,'MARKET','BUY',50,50,NULL,3250,'FILLED',20,'idem-5002','2026-08-18 10:15:00','2026-08-18 10:15:03','2026-08-18 10:15:03',NULL,1),
(5003,102,1003,'LIMIT','BUY',75,75,1850,1845,'FILLED',20,'idem-5003','2026-08-19 09:45:00','2026-08-19 09:45:06','2026-08-19 09:45:06',NULL,1),
(5004,103,1004,'MARKET','SELL',40,40,NULL,1780,'FILLED',20,'idem-5004','2026-08-19 11:20:00','2026-08-19 11:20:04','2026-08-19 11:20:04',NULL,1),
(5005,104,1005,'LIMIT','BUY',100,0,1050,NULL,'NEW',0,'idem-5005','2026-08-20 10:00:00','2026-08-20 10:00:00',NULL,NULL,0);

INSERT INTO positions (account_id,instrument_id,quantity,average_buy_price,version) VALUES
(101,1001,100,1398.50,1),
(101,1002,50,3250,1),
(102,1003,75,1845,1),
(103,1004,20,1700,2),
(105,1005,80,1025,1);

INSERT INTO holdings (account_id,instrument_id,quantity,average_buy_price,version) VALUES
(101,1001,200,1350,4),
(101,1002,100,3100,2),
(102,1003,150,1780,3),
(103,1004,60,1700,2),
(105,1005,80,1025,1);

INSERT INTO pnl
(pnl_id,account_id,instrument_id,sell_order_id,quantity,buy_price,sell_price,gross_pnl,brokerage,net_pnl,realized_at)
VALUES
(7001,103,1004,5004,40,1700,1780,3200,20,3180,'2026-08-19 11:20:04');

INSERT INTO watchlists (watchlist_id,user_id,name) VALUES
(8001,1,'My Stocks'),
(8002,1,'Tech Stocks'),
(8003,1,'Banking'),
(8004,2,'Long Term'),
(8005,3,'My Watchlist');

INSERT INTO watchlist_instruments (watchlist_id,instrument_id) VALUES
(8001,1001),(8001,1002),(8001,1004),
(8002,1002),(8002,1003),
(8003,1004),(8003,1005),
(8004,1001),(8004,1005),
(8005,1003),(8005,1004);

SELECT setval(pg_get_serial_sequence('users','user_id'), COALESCE((SELECT MAX(user_id) FROM users),1));
SELECT setval(pg_get_serial_sequence('accounts','account_id'), COALESCE((SELECT MAX(account_id) FROM accounts),1));
SELECT setval(pg_get_serial_sequence('instruments','instrument_id'), COALESCE((SELECT MAX(instrument_id) FROM instruments),1));
SELECT setval(pg_get_serial_sequence('orders','order_id'), COALESCE((SELECT MAX(order_id) FROM orders),1));
SELECT setval(pg_get_serial_sequence('pnl','pnl_id'), COALESCE((SELECT MAX(pnl_id) FROM pnl),1));
SELECT setval(pg_get_serial_sequence('watchlists','watchlist_id'), COALESCE((SELECT MAX(watchlist_id) FROM watchlists),1));

COMMIT;