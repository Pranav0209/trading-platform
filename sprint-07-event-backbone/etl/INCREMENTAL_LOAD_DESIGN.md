# Trade ETL Incremental Load Design

## Overview

Incremental ETL pipeline that extracts trade data from PostgreSQL (Sprint 6 Trade API), validates it comprehensively, and loads it into a star schema analytics warehouse (DuckDB).

## Star Schema

```
                    ┌──────────────────┐
                    │   fact_trades    │
                    │                  │
                    │  - order_id (PK) │◄───────┐
                    │  - quantity      │        │ FK
                    │  - side (BUY/SELL)       │
                    │  - trade_value   │        │
                    │  - date_key ──────────────────┐
                    │  - account_key ──────┐        │ FK
                    │  - instrument_key ────────────┼────┐
                    │  - status        │        │    │    │
                    │  - created_at    │        │    │    │
                    └──────────────────┘        │    │    │
                                               │    │    │
                         ┌─────────────────────┘    │    │
                         │                          │    │
                         │                          │    │
                    ┌────▼─────────┐     ┌──────────▼───┐   ┌──────────────────┐
                    │  dim_account  │     │dim_instrument│   │   dim_date       │
                    │               │     │              │   │                  │
                    │ account_key   │     │instrument_key│   │ date_key (PK)   │
                    │ account_id    │     │ symbol       │   │ year, month, day │
                    │ account_number│     │ display_name │   │ quarter, week    │
                    │ currency      │     │ asset_class  │   │ day_name         │
                    │ account_status│     │ currency     │   │ is_weekend       │
                    └───────────────┘     │ tradable     │   └──────────────────┘
                                          └──────────────┘
```

**Tables:**

- **fact_trades:** Order-level facts
  - Unique on `order_id` (idempotency)
  - Contains `created_at`, `executed_on`, `loaded_at` timestamps
  - `trade_value = quantity * executed_price` (computed for FILLED only)

- **dim_date:** Trading calendar (2014-2034)
  - Populated once at initialization
  - Attributes: year, month, quarter, week, day_name, day_of_week, is_weekend

- **dim_instrument:** Symbols dimension
  - Reloaded in full each run (not slowly-changing type 2)
  - Preserves `source_loaded_at` for lineage

- **dim_account:** Account dimension
  - All statuses: ACTIVE, SUSPENDED, CLOSED
  - Reloaded in full each run
  - Closed accounts remain queryable for history

## Incremental Load Design

### Watermarking

**Problem:** Without watermarking, re-running the load would re-process entire history.

**Solution:** Track max `created_at` from last successful load in `load_watermarks`.

On subsequent runs, query only `WHERE created_at > last_watermark`.

**First load:** No watermark → fetch all orders.

### Idempotency via MERGE

**Problem:** Running load twice would insert duplicates.

**Solution:** Upsert on `order_id` using `ON CONFLICT ... DO UPDATE SET`.

```sql
INSERT INTO fact_trades (order_id, ..., loaded_at)
VALUES (?, ...)
ON CONFLICT(order_id) DO UPDATE SET
    order_status = EXCLUDED.order_status,
    executed_price = EXCLUDED.executed_price,
    trade_value = EXCLUDED.trade_value,
    executed_on = EXCLUDED.executed_on,
    loaded_at = CURRENT_TIMESTAMP;
```

Result:
- First load: row inserted
- Subsequent loads: row updated (no duplicates)
- `loaded_at` tracks most recent load time

### Dead-lettering (not silent drop)

**Problem:** Silently dropping invalid rows hides faults.

**Solution:** Record every failed row in `dead_letter_queue`.

Stores:
- `load_batch_id` - Unique batch UUID (for traceability)
- `error_reason` - Short code (e.g., 'invalid_quantity')
- `error_detail` - Full error message
- `row_data` - Entire original row as JSON
- `created_at` - When it failed
- `source_order_id`, `source_account_id`, `source_symbol` - For lookup

**Outcome:** fact_tables are identical (both missing invalid row), but dead-lettering allows investigation.

## Validation Checks

Every row validated before reaching fact_trades:

### 1. Null and Type Mismatches
Required fields must be present and correct type:
- `id` (UUID)
- `account_id` (int)
- `symbol` (string)
- `side` (string)
- `quantity` (int)
- `status` (string)
- `created_at` (datetime)

### 2. Domain Constraints
- **Side:** 'BUY' or 'SELL'
- **Status:** 'NEW', 'FILLED', 'CANCELLED', or 'REJECTED'
- **Quantity:** > 0
- **Executed price:** > 0 (when FILLED)
- **Trade value:** quantity × executed_price (computed for FILLED)

### 3. Referential Integrity
- Symbol must exist in `dim_instrument`
- Account ID must exist in `dim_account`

**Why no placeholder dimensions:** Do not insert placeholder rows (e.g., `{symbol: 'UNKNOWN'}`). That hides the fault (usually missed dimension load). Dead-letter instead.

## Load Order

```
dim_date (once, full range)
    ↓
dim_instrument (every load, full reload)
    ↓
dim_account (every load, full reload)
    ↓
fact_trades (incremental, only new orders)
```

**Why:**
1. Dates are constant
2. Dimensions are small, full reload is cheap
3. Facts depend on dimensions (FK constraints)

## Orders Included

Pipeline loads **all** order statuses:
- **FILLED:** Successfully executed
- **NEW:** Pending
- **CANCELLED:** User cancelled
- **REJECTED:** Exchange/compliance rejected

**Why:** Fill rate = FILLED count / total count. Need all statuses for denominator.

## Database Constraints

Constraints enforced at database level:

```sql
CREATE TABLE fact_trades (
    ...
    side VARCHAR CHECK(side IN ('BUY', 'SELL'))
    order_status VARCHAR CHECK(order_status IN ('NEW', 'FILLED', 'CANCELLED', 'REJECTED'))
    quantity INTEGER CHECK(quantity > 0)
    executed_price NUMERIC(19, 4)  -- nullable
    trade_value NUMERIC(19, 2)     -- nullable
    order_id UUID UNIQUE
)
```

Invalid rows cannot reach the database; caught during upsert and dead-lettered.

## Three Pipeline Commands

### 1. Load Dimensions
```powershell
trade-etl load-dimensions --postgres-url "postgresql://..."
```
Loads dim_instrument and dim_account. dim_date pre-populated.

### 2. Load Trades (Incremental)
```powershell
trade-etl load-trades --postgres-url "postgresql://..."
```
Incremental load since last watermark. Validates and dead-letters failures.

### 3. Load All (One Shot)
```powershell
trade-etl load-all --postgres-url "postgresql://..."
```
Runs dimensions + trades. Useful for initial setup.

## Unit Test Scenarios

### Scenario 1: First incremental load populates FACT_TRADES
- Setup: Empty warehouse, 2 orders with created_at = T1
- Expected: fact_trades has 2 rows, watermark recorded at T1

### Scenario 2: Second load with no new data adds no rows
- Setup: Run load 1, then load 2 with same data (created_at <= T1)
- Expected: fact_trades still has 2 rows (no duplicates)

### Scenario 3: Transform handles nulls and type mismatches
- NULL executed_price for NEW order → accepted
- NULL executed_price for FILLED order → validation error
- quantity = 100.5 (float) → validation error
- side = 'INVALID' → validation error

### Scenario 4: Invalid row is dead-lettered with reason and batch ID
- Setup: Order with quantity = -10
- Expected: fact_trades = 0 rows, dead_letter_queue = 1 row with error_reason and batch_id

## Performance

### Write cost
- **fact_trades:** O(1) per row (upsert on unique order_id)
- **Dimensions:** O(n) full reload (small tables, acceptable)

### Query performance
- Index on `fact_trades(account_key, date_key DESC)` for time-series
- Index on `fact_trades(created_at DESC)` for watermark queries

### Storage
- dim_date: ~7300 rows × 9 columns ≈ 200 KB
- dim_instrument: < 100 symbols
- dim_account: < 1000 accounts
- fact_trades: grows with order volume (no hard limit)

## Monitoring

### Load status
```sql
SELECT * FROM load_watermarks ORDER BY load_completed_at DESC LIMIT 1;
```

### Dead-lettered rows by batch
```sql
SELECT 
    error_reason,
    COUNT(*)
FROM dead_letter_queue
WHERE load_batch_id = 'YOUR-BATCH-ID'
GROUP BY error_reason;
```

### Audit trail (orders per load)
```sql
SELECT 
    DATE(loaded_at) AS load_date,
    COUNT(*) AS row_count
FROM fact_trades
GROUP BY DATE(loaded_at)
ORDER BY load_date DESC;
```

## Future Enhancements

- Slowly-changing dimensions (Type 2) for account/instrument history
- Partitioning by date_key for archival
- Incremental dimensions (only load changed records)
- SLA monitoring (alert on dead_letter_queue growth)
- Reconciliation (compare row counts with source)
