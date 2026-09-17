"""Data warehouse schema initialization and management."""

from __future__ import annotations

from pathlib import Path
from datetime import datetime, date, timedelta
from typing import Optional

import duckdb


def _get_date_range_sql() -> str:
    """Generate a date dimension covering a range of dates."""
    # Cover 10 years of trading history
    start_date = date(2014, 1, 1)
    end_date = date(2034, 12, 31)
    
    return f"""
    WITH RECURSIVE date_series AS (
        SELECT '{start_date}'::DATE AS date_key
        UNION ALL
        SELECT date_key + INTERVAL '1 day'
        FROM date_series
        WHERE date_key < '{end_date}'::DATE
    )
    SELECT
        date_key,
        EXTRACT(YEAR FROM date_key)::INTEGER AS year,
        EXTRACT(MONTH FROM date_key)::INTEGER AS month,
        EXTRACT(DAY FROM date_key)::INTEGER AS day,
        EXTRACT(QUARTER FROM date_key)::INTEGER AS quarter,
        EXTRACT(WEEK FROM date_key)::INTEGER AS week,
        DAYNAME(date_key) AS day_name,
        EXTRACT(DAYOFWEEK FROM date_key)::INTEGER AS day_of_week,
        (EXTRACT(DAYOFWEEK FROM date_key)::INTEGER IN (6, 7)) AS is_weekend
    FROM date_series
    """


def initialize_warehouse(database_path: str | Path = "analytics.duckdb") -> None:
    """Initialize the star schema warehouse with all dimension and fact tables.
    
    Schema:
    - dim_date: Date dimension covering trading days
    - dim_instrument: Instrument (symbol) dimension 
    - dim_account: Account dimension
    - fact_trades: Fact table containing trades
    - dead_letter_queue: Failed rows with validation errors
    - load_watermarks: Track incremental loads by timestamp
    """
    database = Path(database_path)
    database.parent.mkdir(parents=True, exist_ok=True)

    with duckdb.connect(str(database)) as conn:
        # Create sequences first (before they're referenced)
        try:
            conn.execute("CREATE SEQUENCE IF NOT EXISTS seq_instrument_key START 1")
            conn.execute("CREATE SEQUENCE IF NOT EXISTS seq_account_key START 1")
            conn.execute("CREATE SEQUENCE IF NOT EXISTS seq_trade_key START 1")
            conn.execute("CREATE SEQUENCE IF NOT EXISTS seq_dlq_key START 1")
            conn.execute("CREATE SEQUENCE IF NOT EXISTS seq_watermark_key START 1")
        except Exception as e:
            # Sequences may already exist, that's fine
            pass

        # Create DIM_DATE
        conn.execute("""
            CREATE TABLE IF NOT EXISTS dim_date (
                date_key DATE PRIMARY KEY,
                year INTEGER NOT NULL,
                month INTEGER NOT NULL,
                day INTEGER NOT NULL,
                quarter INTEGER NOT NULL,
                week INTEGER NOT NULL,
                day_name VARCHAR NOT NULL,
                day_of_week INTEGER NOT NULL,
                is_weekend BOOLEAN NOT NULL
            )
        """)

        # Populate DIM_DATE if empty
        count = conn.execute("SELECT COUNT(*) FROM dim_date").fetchone()[0]
        if count == 0:
            date_sql = _get_date_range_sql()
            conn.execute(f"INSERT INTO dim_date {date_sql}")

        # Create DIM_INSTRUMENT
        conn.execute("""
            CREATE TABLE IF NOT EXISTS dim_instrument (
                instrument_key INTEGER PRIMARY KEY DEFAULT nextval('seq_instrument_key'),
                symbol VARCHAR NOT NULL UNIQUE,
                display_name VARCHAR NOT NULL,
                asset_class VARCHAR NOT NULL,
                currency VARCHAR NOT NULL,
                tradable BOOLEAN NOT NULL,
                source_loaded_at TIMESTAMPTZ NOT NULL
            )
        """)

        # Create DIM_ACCOUNT
        conn.execute("""
            CREATE TABLE IF NOT EXISTS dim_account (
                account_key INTEGER PRIMARY KEY DEFAULT nextval('seq_account_key'),
                account_id BIGINT NOT NULL UNIQUE,
                account_number VARCHAR NOT NULL UNIQUE,
                currency VARCHAR NOT NULL,
                account_status VARCHAR NOT NULL,
                source_loaded_at TIMESTAMPTZ NOT NULL
            )
        """)

        # Create FACT_TRADES
        conn.execute("""
            CREATE TABLE IF NOT EXISTS fact_trades (
                trade_key INTEGER PRIMARY KEY DEFAULT nextval('seq_trade_key'),
                order_id UUID NOT NULL UNIQUE,
                account_key INTEGER NOT NULL REFERENCES dim_account(account_key),
                instrument_key INTEGER NOT NULL REFERENCES dim_instrument(instrument_key),
                date_key DATE NOT NULL REFERENCES dim_date(date_key),
                
                side VARCHAR NOT NULL CHECK(side IN ('BUY', 'SELL')),
                order_status VARCHAR NOT NULL CHECK(order_status IN ('NEW', 'FILLED', 'CANCELLED', 'REJECTED')),
                quantity INTEGER NOT NULL CHECK(quantity > 0),
                executed_price NUMERIC(19, 4),
                trade_value NUMERIC(19, 2),
                
                created_at TIMESTAMPTZ NOT NULL,
                executed_on TIMESTAMPTZ,
                loaded_at TIMESTAMPTZ NOT NULL,
                
                UNIQUE(order_id)
            )
        """)

        # Create index for incremental loads and queries
        conn.execute("""
            CREATE INDEX IF NOT EXISTS idx_fact_trades_account_date 
            ON fact_trades(account_key, date_key DESC)
        """)

        conn.execute("""
            CREATE INDEX IF NOT EXISTS idx_fact_trades_created_at 
            ON fact_trades(created_at DESC)
        """)

        # Create DEAD_LETTER_QUEUE for failed rows
        conn.execute("""
            CREATE TABLE IF NOT EXISTS dead_letter_queue (
                dlq_key INTEGER PRIMARY KEY DEFAULT nextval('seq_dlq_key'),
                load_batch_id VARCHAR NOT NULL,
                source_order_id UUID,
                source_account_id BIGINT,
                source_symbol VARCHAR,
                error_reason VARCHAR NOT NULL,
                error_detail TEXT,
                row_data JSON,
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """)

        # Create LOAD_WATERMARKS to track incremental loads
        conn.execute("""
            CREATE TABLE IF NOT EXISTS load_watermarks (
                watermark_key INTEGER PRIMARY KEY DEFAULT nextval('seq_watermark_key'),
                load_name VARCHAR NOT NULL UNIQUE,
                last_loaded_timestamp TIMESTAMPTZ NOT NULL,
                batch_id VARCHAR NOT NULL,
                row_count INTEGER NOT NULL,
                dlq_count INTEGER NOT NULL,
                load_completed_at TIMESTAMPTZ NOT NULL
            )
        """)

        conn.commit()


def get_last_watermark(database_path: str | Path = "analytics.duckdb") -> Optional[datetime]:
    """Get the last successful load timestamp for incremental loads.
    
    Returns the last_loaded_timestamp from load_watermarks, or None if no prior load.
    """
    database = Path(database_path)
    
    with duckdb.connect(str(database)) as conn:
        result = conn.execute("""
            SELECT MAX(last_loaded_timestamp)
            FROM load_watermarks
        """).fetchone()
        
        return result[0] if result and result[0] else None


def record_watermark(
    load_name: str,
    loaded_timestamp: datetime,
    batch_id: str,
    row_count: int,
    dlq_count: int,
    database_path: str | Path = "analytics.duckdb",
) -> None:
    """Record a successful load watermark.
    
    Args:
        load_name: Name of the load pipeline (e.g., 'trades_incremental')
        loaded_timestamp: The watermark timestamp (max created_at from source)
        batch_id: Unique identifier for this batch
        row_count: Number of rows successfully loaded
        dlq_count: Number of rows dead-lettered
        database_path: Path to the DuckDB database
    """
    database = Path(database_path)
    
    with duckdb.connect(str(database)) as conn:
        now = datetime.now()
        conn.execute("""
            INSERT INTO load_watermarks 
            (load_name, last_loaded_timestamp, batch_id, row_count, dlq_count, load_completed_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(load_name) DO UPDATE SET
                last_loaded_timestamp = EXCLUDED.last_loaded_timestamp,
                batch_id = EXCLUDED.batch_id,
                row_count = EXCLUDED.row_count,
                dlq_count = EXCLUDED.dlq_count,
                load_completed_at = EXCLUDED.load_completed_at
        """, [load_name, loaded_timestamp, batch_id, row_count, dlq_count, now])
        
        conn.commit()


def dead_letter(
    load_batch_id: str,
    error_reason: str,
    error_detail: str | None = None,
    order_id: str | None = None,
    account_id: int | None = None,
    symbol: str | None = None,
    row_data: dict | None = None,
    database_path: str | Path = "analytics.duckdb",
) -> None:
    """Record a failed row to the dead letter queue.
    
    Args:
        load_batch_id: Unique batch identifier for this load
        error_reason: Short reason for failure (e.g., 'invalid_quantity')
        error_detail: Detailed error message
        order_id: The order UUID if available
        account_id: The account ID if available
        symbol: The instrument symbol if available
        row_data: The original row data as a dict
        database_path: Path to the DuckDB database
    """
    import json
    
    database = Path(database_path)
    
    with duckdb.connect(str(database)) as conn:
        row_data_json = json.dumps(row_data) if row_data else None
        
        conn.execute("""
            INSERT INTO dead_letter_queue 
            (load_batch_id, source_order_id, source_account_id, source_symbol, 
             error_reason, error_detail, row_data)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, [load_batch_id, order_id, account_id, symbol, error_reason, error_detail, row_data_json])
        
        conn.commit()
