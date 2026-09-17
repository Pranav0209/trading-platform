# Pipeline Commands Reference

Three commands run any stage of the incremental load pipeline independently.

## Command 1: Load Dimensions

Load instruments and accounts from Postgres into the data warehouse.

**When to use:** 
- Initial setup (before loading trades)
- When instruments or accounts change
- To refresh dimension data

**Syntax:**

```powershell
trade-etl load-dimensions --postgres-url "postgresql://user:password@host:5432/zkuba"
```

**Options:**
- `--postgres-url` (required): PostgreSQL connection string
- `--database` (optional): DuckDB path (default: `analytics.duckdb`)
- `--batch-id` (optional): Override batch ID (normally auto-generated)

**Example:**

```powershell
trade-etl load-dimensions \
    --postgres-url "postgresql://localhost/zkuba" \
    --database "path/to/warehouse.duckdb"
```

**Output:**

```
[batch-id] Loading dim_instrument...
[batch-id] Loaded 6 instruments
[batch-id] Loading dim_account...
[batch-id] Loaded 3 accounts
✓ Loaded dimensions (instruments: 6, accounts: 3)
```

---

## Command 2: Load Trades (Incremental)

Load trades from Postgres, incremental since last successful load.

**When to use:**
- Regular incremental loads (daily, hourly)
- After trades are added/modified in source
- Part of scheduled automation

**Syntax:**

```powershell
trade-etl load-trades --postgres-url "postgresql://user:password@host:5432/zkuba"
```

**Options:**
- `--postgres-url` (required): PostgreSQL connection string
- `--database` (optional): DuckDB path (default: `analytics.duckdb`)
- `--batch-id` (optional): Override batch ID (normally auto-generated)

**Example:**

```powershell
trade-etl load-trades \
    --postgres-url "postgresql://localhost/zkuba"
```

**What it does:**
1. Fetches last watermark (max `created_at` from previous load)
2. Queries Postgres for orders where `created_at > watermark`
3. Validates each order (side, status, quantity, price, references)
4. Either loads to fact_trades or dead-letters with reason
5. Updates watermark for next run

**Output:**

```
[batch-id] Starting incremental load pipeline...
[batch-id] Loading dim_instrument...
[batch-id] Loaded 6 instruments
[batch-id] Loading dim_account...
[batch-id] Loaded 3 accounts
[batch-id] Loading fact_trades (incremental)...
[batch-id] Fetched 42 new orders
[batch-id] Loaded 40 trades, dead-lettered 2
✓ Load completed (batch_id=550e8400-e29b-41d4-a716-446655440000)
  - Instruments: 6
  - Accounts: 3
  - Trades loaded: 40
  - Dead-lettered: 2
```

---

## Command 3: Load All (Initial Setup)

Run dimensions and trades in one command.

**When to use:**
- First time setting up the warehouse
- Complete refresh from scratch
- CI/CD jobs that need one command

**Syntax:**

```powershell
trade-etl load-all --postgres-url "postgresql://user:password@host:5432/zkuba"
```

**Options:**
- `--postgres-url` (required): PostgreSQL connection string
- `--database` (optional): DuckDB path (default: `analytics.duckdb`)
- `--batch-id` (optional): Override batch ID (normally auto-generated)

**Example:**

```powershell
trade-etl load-all --postgres-url "postgresql://localhost/zkuba"
```

**Equivalent to:**

```powershell
trade-etl load-dimensions --postgres-url "..."
trade-etl load-trades --postgres-url "..."
```

**Output:**

```
Starting complete ETL pipeline...
  Source: postgresql://localhost/zkuba
  Target: analytics.duckdb

============================================================
✓ PIPELINE COMPLETED SUCCESSFULLY
============================================================
Batch ID: 550e8400-e29b-41d4-a716-446655440000

Dimension tables:
  - Instruments: 6
  - Accounts: 3

Fact table:
  - Trades loaded: 100
  - Trades dead-lettered: 2
============================================================
```

---

## Batch ID and Traceability

Every load is tagged with a unique `batch_id`. Use it to:
- Find all rows dead-lettered in that batch
- Track which batch a watermark belongs to
- Audit load history

**Example: Find all dead-lettered rows from a batch**

```powershell
duckdb analytics.duckdb

-- Inside duckdb:
SELECT COUNT(*) FROM dead_letter_queue 
WHERE load_batch_id = '550e8400-e29b-41d4-a716-446655440000';
```

---

## Monitoring Queries

### View load history

```powershell
duckdb analytics.duckdb

SELECT 
    load_name,
    batch_id,
    last_loaded_timestamp,
    row_count,
    dlq_count,
    load_completed_at
FROM load_watermarks
ORDER BY load_completed_at DESC;
```

### Count trades by date

```powershell
duckdb analytics.duckdb

SELECT 
    DATE(created_at) AS trade_date,
    COUNT(*) AS trade_count,
    SUM(CASE WHEN order_status = 'FILLED' THEN 1 ELSE 0 END) AS filled_count
FROM fact_trades
GROUP BY DATE(created_at)
ORDER BY trade_date DESC;
```

### Dead-lettered rows by reason

```powershell
duckdb analytics.duckdb

SELECT 
    error_reason,
    COUNT(*) AS count
FROM dead_letter_queue
GROUP BY error_reason
ORDER BY count DESC;
```

### Dimension coverage

```powershell
duckdb analytics.duckdb

-- Symbols loaded
SELECT COUNT(*) FROM dim_instrument;

-- Accounts loaded
SELECT COUNT(*) FROM dim_account;

-- Date range
SELECT MIN(date_key), MAX(date_key) FROM dim_date;
```

---

## Troubleshooting

### "dimension not found" error

Trades reference symbols or accounts that don't exist in dimensions.

**Fix:** Run `load-dimensions` first:

```powershell
trade-etl load-dimensions --postgres-url "postgresql://localhost/zkuba"
```

Then run trades again.

### "invalid_quantity" error

An order has quantity <= 0 or is not an integer.

**Check:** 

```powershell
duckdb analytics.duckdb

SELECT row_data FROM dead_letter_queue 
WHERE error_reason = 'invalid_quantity' 
LIMIT 1;
```

### "negative_price" error

An order has executed_price < 0.

**Check:** Same as above.

### PostgreSQL connection error

Make sure connection string is correct:

```powershell
# Test with psql (if installed)
psql "postgresql://localhost/zkuba" -c "SELECT 1;"
```

---

## Recommended Workflow

### Initial setup (one-time)

```powershell
trade-etl load-all --postgres-url "postgresql://localhost/zkuba"
```

### Daily incremental (on schedule)

```powershell
trade-etl load-trades --postgres-url "postgresql://localhost/zkuba"
```

### Monitor (regularly)

```powershell
duckdb analytics.duckdb
SELECT * FROM load_watermarks ORDER BY load_completed_at DESC LIMIT 1;
SELECT COUNT(*) FROM dead_letter_queue WHERE date(created_at) = TODAY();
```

---

## Environment Variables

Create `.env` file in repository root:

```
POSTGRES_URL=postgresql://user:password@host:5432/zkuba
```

Then you can omit `--postgres-url` from commands:

```powershell
trade-etl load-all
```

Otherwise, always pass `--postgres-url` explicitly.
