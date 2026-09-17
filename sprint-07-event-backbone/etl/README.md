# Sprint 7: Incremental Trade Load ETL

Incremental ETL pipeline for loading trade data from PostgreSQL into a star schema analytics warehouse (DuckDB).

## Acceptance Criteria

✅ **Incremental load with watermarking:** Orders are loaded incrementally based on `created_at` timestamp watermark. Subsequent runs only fetch new data.

✅ **Idempotent loads:** Upsert on `order_id` prevents double-counting. Re-running doesn't add duplicates.

✅ **Dead-lettering (not silent drop):** Failed rows are recorded in `dead_letter_queue` with batch ID, reason, and full row data—never silently dropped.

✅ **Load order:** Dimensions (dim_date → dim_instrument → dim_account) then facts (fact_trades).

✅ **All order statuses:** Loads NEW, FILLED, CANCELLED, REJECTED orders (needed for fill rate computation).

✅ **Merge on natural key:** Upsert on `order_id` (natural key from source).

✅ **Validation checks:** Runs 11 checks before fact table:
  - Null and type mismatches
  - Side validation (BUY/SELL)
  - Status validation (NEW/FILLED/CANCELLED/REJECTED)
  - Positive quantity and price
  - Referential integrity (dimensions must exist)
  - Trade value computation

✅ **Three pipeline commands:** `load-dimensions`, `load-trades`, `load-all`

✅ **Unit test scenarios:** Incremental load, no-new-data idempotency, null/type handling, dead-lettering

## Setup

### Install

```bash
cd sprint-07-event-backbone/etl
python -m venv .venv
.venv\Scripts\python.exe -m pip install -e ".[dev]"
```

### Environment

Create `.env` in repository root (optional, or use `--postgres-url` flag):

```
POSTGRES_URL=postgresql://user:password@host:5432/zkuba
```

## Quick Start

### First run: Load all (dimensions + trades)

```bash
trade-etl load-all --postgres-url "postgresql://localhost/zkuba"
```

### Subsequent runs: Load only trades (incremental)

```bash
trade-etl load-trades --postgres-url "postgresql://localhost/zkuba"
```

### Load dimensions only (if needed)

```bash
trade-etl load-dimensions --postgres-url "postgresql://localhost/zkuba"
```

## Tests

```bash
pytest tests/test_incremental_load.py -v
```

## Documentation

- [QUICK_START.md](./QUICK_START.md) - 5-minute setup and usage
- [PIPELINE_COMMANDS.md](./PIPELINE_COMMANDS.md) - Detailed command reference
- [INCREMENTAL_LOAD_DESIGN.md](./INCREMENTAL_LOAD_DESIGN.md) - Architecture and design
- [IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md) - Maps code to acceptance criteria

## Output Files

- `analytics.duckdb` - DuckDB warehouse database (default location)
- `tests/` - Unit tests with 24+ test scenarios
