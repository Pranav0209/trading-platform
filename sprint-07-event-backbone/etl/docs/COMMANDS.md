# CLI Command Reference: Trade ETL

The `trade-etl` CLI provides commands to initialize, run, and verify the pipeline.

---

## Commands Overview

| Command | Purpose |
| :--- | :--- |
| `init-warehouse` | Creates all DuckDB tables and pre-populates `dim_date`. |
| `load-dimensions` | Extracts and loads `dim_instrument` and `dim_account`. |
| `load-trades` | Runs incremental trade extraction based on watermark. |
| `load-all` | Runs the full pipeline: dimensions followed by incremental trades. |
| `verify` | Runs 8 integrity checks and analytical summaries. |

---

## Command Options

### 1. `init-warehouse`
Initializes the DuckDB database file without requiring a PostgreSQL connection.

```powershell
trade-etl init-warehouse [--database <path>]
```

**Options:**
- `--database`: Path to DuckDB file (default: `analytics.duckdb`).

---

### 2. `load-dimensions`
Loads all instruments from PostgreSQL into `dim_instrument` and accounts into `dim_account`.

```powershell
trade-etl load-dimensions [--postgres-url <url>] [--database <path>] [--batch-id <id>]
```

**Options:**
- `--postgres-url`: PostgreSQL connection string (defaults to `POSTGRES_URL` env or `postgresql://postgres:postgres@localhost:5432/trading`).
- `--database`: Path to DuckDB database.
- `--batch-id`: Optional custom batch identifier string.

---

### 3. `load-trades`
Runs incremental order extraction using the stored `updated_at` watermark, along with incremental dimension loading.
**Note:** Automatically loads new/updated dimensions before processing trades to ensure referential integrity.

```powershell
trade-etl load-trades [--postgres-url <url>] [--database <path>] [--batch-id <id>]
```

---

### 4. `load-all`
Recommended for scheduled jobs. Performs a complete ETL run:
- **Full dimension load**: Extracts all instruments and accounts from PostgreSQL
- **Incremental trades**: Loads only new/modified orders using watermark

Suitable for initial setup and periodic full reconciliation. On subsequent runs after `load-all`, use `load-trades` for faster incremental updates.

```powershell
trade-etl load-all [--postgres-url <url>] [--database <path>]
```

---

### 5. `verify`
Inspects table counts, tests foreign key referential integrity, and prints analytics (fill rate, venue volumes, account summaries).

```powershell
trade-etl verify [--database <path>]
```
