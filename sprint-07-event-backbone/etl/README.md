# Sprint 7: Incremental Trade Load ETL

A modular, reliable Python ETL pipeline extracting operational trade and account data from **PostgreSQL** (Sprint 6 Trade API) and loading it into an analytical **DuckDB** star schema (conforming to [`contracts/analytics-schema.sql`](file:///c:/Users/krish/training/chennai-capstone-SE1-team4/contracts/analytics-schema.sql)).

---

## Directory Structure

```
sprint-07-event-backbone/etl/
├── README.md               # Main documentation & architecture overview
├── pyproject.toml          # Package configuration & dependencies
├── verify_warehouse.py     # Standalone analytical verification runner
│
├── docs/                   # Organized documentation guides
│   ├── ARCHITECTURE.md     # Dimensional model, SCD Type 2 & design decisions
│   ├── COMMANDS.md         # Complete CLI command reference
│   └── QUICK_START.md      # 5-minute setup and verification walkthrough
│
├── src/
│   └── sprint_07_event_backbone_etl/
│       ├── __init__.py     # Package exports
│       ├── __main__.py     # Entry point for python -m
│       ├── extract.py      # Extract layer: PostgreSQL connections & watermark queries
│       ├── transform.py    # Transform layer: 11 validations, pricing & calculations
│       ├── load.py         # Load layer: DuckDB upserts, dimension management & DLQ
│       ├── warehouse.py    # Warehouse DDL, surrogate keys & schema initialization
│       ├── pipeline.py     # Orchestrator: connects Extract -> Transform -> Load
│       ├── analytics.py    # Analytics runner: fill rates, venue reports & health checks
│       └── cli.py          # Unified CLI: init, dimensions, trades, all, verify
│
└── tests/
    └── test_incremental_load.py  # Comprehensive test suite
```

---

## ETL Architecture Layers

```
  PostgreSQL (trading)
          │
  ┌───────▼────────────────┐
  │ extract.py             │  Queries orders WHERE updated_at > watermark
  └───────┬────────────────┘
          │
  ┌───────▼────────────────┐
  │ transform.py           │  11 validations, exchange derivation, trade value
  └───────┬────────────────┘
          │
  ┌───────▼────────────────┐
  │ load.py                │  Type 1 instruments, Type 2 accounts, idempotent fact upserts
  └───────┬────────────────┘
          │
  DuckDB (analytics.duckdb)
```

1. **`extract.py` (Extraction)**:
   Connects to PostgreSQL. Extracts `instruments`, `accounts` (joined with `users` for `holder_name`), and `orders` filtered by `updated_at > watermark` so no order state transitions are missed.
2. **`transform.py` (Transformation & Quality)**:
   Applies an 11-point validation gate (types, UUID handling, positive quantities, valid statuses, non-null prices). Computes `trade_value` and integer `YYYYMMDD` `date_key`, and derives `exchange` from ticker format.
3. **`load.py` (Loading & Idempotency)**:
   Writes to DuckDB. Implements Type 1 SCD for instruments, Type 2 SCD for accounts (`effective_date`, `end_date`, `is_current`), and idempotent natural-key upserts (`ON CONFLICT (source_order_id)`) for trades. Quarantines invalid records into `dead_letter_queue`.
4. **`warehouse.py` (Schema & Watermarks)**:
   Provides ANSI SQL schema definitions, surrogate key helpers (without sequences), and records `load_watermarks`.
5. **`analytics.py` (Verification & Reporting)**:
   Runs integrity audits and business analytics queries (fill rate, volume by venue, account summaries).

---

## Quick Usage

### 1. Setup Environment
```powershell
python -m venv .venv
.venv\Scripts\activate
pip install -e ".[dev]"
```

### 2. Run Tests
```powershell
pytest tests/ -v
```

### 3. Initialize Warehouse & Run Pipeline
```powershell
# Initialize star schema
trade-etl init-warehouse

# Ingest dimensions and trades
trade-etl load-all --postgres-url "postgresql://postgres:postgres@localhost:5432/trading"

# Ingest subsequent incremental trades
trade-etl load-trades --postgres-url "postgresql://postgres:postgres@localhost:5432/trading"
```

### 4. Verify & Inspect Analytics
```powershell
trade-etl verify
```

---

