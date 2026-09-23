# Architecture & Design: Incremental Trade Load ETL

This document explains the architectural decisions, dimensional modeling, and reliability guarantees of the Sprint 7 Trade ETL pipeline.

---

## 1. Context: Why a Star Schema in DuckDB?

The transactional database (PostgreSQL) is optimized for high-concurrency ACID order placement and state locks. Running heavy aggregations (e.g. quarterly fill rate across millions of trades) directly on PostgreSQL degrades transactional latency.

To decouple analytics from trading:
- **OLTP (PostgreSQL)**: Source of record. Normalized, row-oriented, strict foreign keys.
- **OLAP (DuckDB)**: Analytics engine. Star schema, columnar, denormalized dimensions joined to a single fact table on surrogate keys.

```
                      PostgreSQL (OLTP)
                ┌───────────────────────────┐
                │ orders, accounts, users,  │
                │ instruments               │
                └─────────────┬─────────────┘
                              │
                    ETL (Python Pipeline)
                ┌─────────────▼─────────────┐
                │ 1. Extract (updated_at)   │
                │ 2. Transform & 11 Checks  │
                │ 3. Load & Surrogate Keys  │
                └─────────────┬─────────────┘
                              │
                     DuckDB (Star Schema)
                              │
            ┌─────────────────┴─────────────────┐
            ▼                                   ▼
      [DIMENSIONS]                            [FACT]
   - dim_date (YYYYMMDD)                   - fact_trades
   - dim_instrument (Type 1)                 - Foreign keys to all dims
   - dim_account (Type 2 SCD)                - Additive: quantity, trade_value
                                             - Semi-additive: price
```

---

## 2. Dimensional Model Design

Conforms to [`contracts/analytics-schema.sql`]

### Dimensions
1. **`DIM_DATE` (Calendar Dimension)**
   - Pre-populated on warehouse initialization for 20 years (2014 to 2034).
   - Integer key in `YYYYMMDD` format (e.g. `20260917`) for high-performance join and range partition scans.
   - Includes calendar flags: `is_weekday`, `quarter`, `month_name`, `day_name`.

2. **`DIM_INSTRUMENT` (Type 1 Dimension)**
   - Overwritten in-place when attributes change.
   - Derives `exchange` venue convention:
     - `.NS` $\rightarrow$ `NSE`
     - `.BO` $\rightarrow$ `BSE`
     - `FX:` $\rightarrow$ `FX`
     - `X:` $\rightarrow$ `CRYPTO`
     - Plain ticker $\rightarrow$ `US`

3. **`DIM_ACCOUNT` (Type 2 Slowly Changing Dimension)**
   - Tracks historical state transitions (e.g., `ACTIVE` to `SUSPENDED`).
   - Fields: `effective_date`, `end_date` (NULL on current version), `is_current` (`TRUE` on current).
   - Joins operational `accounts` with `users` to capture `account_number` and `holder_name`.

### Facts
1. **`FACT_TRADES` (Grain: One Order)**
   - Contains all order outcomes: `NEW`, `FILLED`, `CANCELLED`, `REJECTED`.
   - Idempotent upsert based on `source_order_id`.
   - Precomputes `trade_value` (`quantity * executed_price` for filled orders, or `quantity * price` for unfilled) to eliminate repeated aggregation arithmetic in queries.

---

## 3. Reliability & Operational Guarantees

### Watermarking on `updated_at`
Extracting orders with `WHERE updated_at > watermark` ensures:
- Newly placed orders are ingested.
- Orders transitioning from `NEW` to `FILLED` or `REJECTED` are captured during subsequent incremental runs and updated in-place via natural key merge.

### Incremental Dimension Loading
Dimensions also support incremental loading to avoid redundant processing:
- **Full Load** (`load-dimensions`): Extracts ALL instruments and accounts, used during initial setup.
- **Incremental Load** (`load-trades` / `load-all`): Extracts only new/changed instruments and accounts since the last run using `updated_at` watermarks.
  - Tracks separate watermarks: `dim_instrument_incremental` and `dim_account_incremental`
  - Significantly reduces processing time when dimensions are stable
  - Ensures new accounts/instruments added to PostgreSQL are captured before processing related trades

### 11-Point Quality & Integrity Gate
Before reaching `fact_trades`, every order passes through:
1. Missing field detection
2. NULL checks on required attributes
3. Type verification (handling PostgreSQL UUID objects)
4. Side constraint (`BUY` or `SELL`)
5. Status constraint (`NEW`, `FILLED`, `CANCELLED`, `REJECTED`)
6. Positive integer quantity
7. Mandatory positive price on `FILLED` orders
8. Precision trade value calculation
9. Date key derivation
10. Referential integrity against `dim_instrument`
11. Referential integrity against `dim_account`

### Dead-Letter Queue (DLQ)
Corrupt or unreferenced rows are never dropped silently. They are persisted to `dead_letter_queue` with batch ID, timestamp, failure reason, and the original JSON payload for debugging.
