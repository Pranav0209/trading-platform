# Sprint 7 ETL: Quick Start (5 minutes)

Get the incremental trade load pipeline running in 5 minutes.

## Prerequisites

- Python 3.11+
- PowerShell or cmd.exe
- PostgreSQL database with trade data (Sprint 6 database)

## Step 1: Install dependencies (1 min)

```powershell
cd c:\Users\Administrator\chennai-capstone-SE1-team4\sprint-07-event-backbone\etl

# Create and activate virtual environment
py -m venv .venv
.venv\Scripts\Activate.ps1

# Install package with dependencies
pip install -e ".[dev]"
```

## Step 2: Create `.env` file (optional)

```powershell
# In repository root, create .env with PostgreSQL connection
Add-Content .env "POSTGRES_URL=postgresql://user:password@host:5432/zkuba"
```

Or use `--postgres-url` flag on each command (see examples below).

## Step 3: Run the pipeline

### First time: Load everything

```powershell
trade-etl load-all --postgres-url "postgresql://localhost/zkuba"
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

What it did:
- ✓ Created `analytics.duckdb` warehouse
- ✓ Loaded dimension tables (dim_date, dim_instrument, dim_account)
- ✓ Loaded all trades (incremental load with watermark)
- ✓ Validated each trade, dead-lettered failures
- ✓ Recorded watermark for next incremental load

### Daily: Load only new trades

```powershell
trade-etl load-trades --postgres-url "postgresql://localhost/zkuba"
```

Fetches only orders created since last load (via watermark). Runs in seconds.

## Step 4: Verify the load

### Query the warehouse

```powershell
# Open DuckDB CLI
duckdb analytics.duckdb

-- Inside duckdb:
SELECT COUNT(*) FROM fact_trades;
SELECT COUNT(*) FROM dead_letter_queue;
SELECT * FROM load_watermarks;
```

### Check what failed

```powershell
duckdb analytics.duckdb

-- Inside duckdb:
SELECT 
    source_order_id,
    source_symbol,
    error_reason,
    error_detail
FROM dead_letter_queue
ORDER BY created_at DESC
LIMIT 5;
```

## Step 5: Run tests

```powershell
pytest tests/test_incremental_load.py -v
```

Expected: All tests pass.

## Common tasks

### Load just dimensions (refresh only)

```powershell
trade-etl load-dimensions --postgres-url "postgresql://localhost/zkuba"
```

### Check load history

```powershell
duckdb analytics.duckdb

SELECT 
    batch_id,
    last_loaded_timestamp,
    row_count,
    dlq_count,
    load_completed_at
FROM load_watermarks
ORDER BY load_completed_at DESC;
```

### Find dead-lettered rows by error reason

```powershell
duckdb analytics.duckdb

SELECT 
    error_reason,
    COUNT(*) AS count
FROM dead_letter_queue
GROUP BY error_reason
ORDER BY count DESC;
```

## Troubleshooting

### "dimension not found" error

**Problem:** Trades are dead-lettered with "dimension_not_found".

**Fix:** Run `load-dimensions` first.

```powershell
trade-etl load-dimensions --postgres-url "postgresql://localhost/zkuba"
```

### "Connection refused" error

**Problem:** Cannot connect to PostgreSQL.

**Fix:** Check your connection string:

```powershell
# Test connection manually
psql "postgresql://localhost/zkuba" -c "SELECT 1;"
```

### Many trades dead-lettered

**Check what failed:**

```powershell
duckdb analytics.duckdb

SELECT error_reason, COUNT(*) 
FROM dead_letter_queue 
GROUP BY error_reason 
ORDER BY count DESC;
```

## Next: Schedule daily loads

### Windows Task Scheduler

```powershell
# PowerShell as Administrator
$action = New-ScheduledTaskAction -Execute "powershell.exe" `
  -Argument "-Command `"cd 'c:\...\sprint-07-event-backbone\etl'; .\.venv\Scripts\trade-etl.exe load-trades --postgres-url 'postgresql://localhost/zkuba'`""

$trigger = New-ScheduledTaskTrigger -Daily -At 02:00AM

Register-ScheduledTask -TaskName "Trade ETL Daily" -Action $action -Trigger $trigger
```

## Full reference

See [PIPELINE_COMMANDS.md](./PIPELINE_COMMANDS.md) for all command options.

See [INCREMENTAL_LOAD_DESIGN.md](./INCREMENTAL_LOAD_DESIGN.md) for architecture details.
