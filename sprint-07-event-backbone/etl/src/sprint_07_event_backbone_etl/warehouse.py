"""Data warehouse schema initialization and management.
- ANSI SQL compatibility
- Star schema with DIM_DATE, DIM_ACCOUNT, DIM_INSTRUMENT, FACT_TRADES
- Dead letter queue for failed/invalid records
- Load watermarks for incremental loading
"""

from __future__ import annotations

import json
from datetime import datetime, date
from pathlib import Path
from typing import Any, Optional

import duckdb


def _get_date_range_sql() -> str:
    
    """Generate date dimension SQL covering 20 years (2014 to 2034).
    Integer date_key in YYYYMMDD format according to analytics-schema.sql.
    """
    start_date = date(2014, 1, 1)
    end_date = date(2034, 12, 31)

    return f"""
    WITH RECURSIVE date_series AS (
        SELECT '{start_date}'::DATE AS full_date
        UNION ALL
        SELECT full_date + INTERVAL '1 day'
        FROM date_series
        WHERE full_date < '{end_date}'::DATE
    )
    SELECT
        (EXTRACT(YEAR FROM full_date)::INTEGER * 10000 + 
         EXTRACT(MONTH FROM full_date)::INTEGER * 100 + 
         EXTRACT(DAY FROM full_date)::INTEGER)::INTEGER AS date_key,
        full_date,
        EXTRACT(DAY FROM full_date)::INTEGER AS day,
        EXTRACT(MONTH FROM full_date)::INTEGER AS month,
        EXTRACT(YEAR FROM full_date)::INTEGER AS year,
        EXTRACT(QUARTER FROM full_date)::INTEGER AS quarter,
        EXTRACT(DAYOFWEEK FROM full_date)::INTEGER AS day_of_week,
        DAYNAME(full_date) AS day_name,
        MONTHNAME(full_date) AS month_name,
        (DAYNAME(full_date) NOT IN ('Saturday', 'Sunday')) AS is_weekday
    FROM date_series
    """


def initialize_warehouse(database_path: str | Path = "analytics.duckdb") -> None:
    """Initialize the star schema warehouse conforming to contracts/analytics-schema.sql."""
    database = Path(database_path)
    database.parent.mkdir(parents=True, exist_ok=True)

    with duckdb.connect(str(database)) as conn:
        # 1. DIM_DATE
        conn.execute("""
            CREATE TABLE IF NOT EXISTS dim_date (
                date_key    INTEGER     NOT NULL,
                full_date   DATE        NOT NULL,
                day         INTEGER     NOT NULL,
                month       INTEGER     NOT NULL,
                year        INTEGER     NOT NULL,
                quarter     INTEGER     NOT NULL,
                day_of_week INTEGER     NOT NULL,
                day_name    VARCHAR(9)  NOT NULL,
                month_name  VARCHAR(9)  NOT NULL,
                is_weekday  BOOLEAN     NOT NULL,
                CONSTRAINT pk_dim_date PRIMARY KEY (date_key),
                CONSTRAINT uq_dim_date_full_date UNIQUE (full_date)
            )
        """)

        # Populate DIM_DATE if empty
        count = conn.execute("SELECT COUNT(*) FROM dim_date").fetchone()[0]
        if count == 0:
            date_sql = _get_date_range_sql()
            conn.execute(f"INSERT INTO dim_date {date_sql}")

        # 2. DIM_INSTRUMENT (Type 1)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS dim_instrument (
                instrument_key  BIGINT       NOT NULL,
                symbol          VARCHAR(20)  NOT NULL,
                name            VARCHAR(255) NOT NULL,
                asset_class     VARCHAR(20)  NOT NULL,
                currency        CHAR(3)      NOT NULL,
                exchange        VARCHAR(20),
                tradable        BOOLEAN      NOT NULL,
                loaded_at       TIMESTAMP    NOT NULL,
                CONSTRAINT pk_dim_instrument PRIMARY KEY (instrument_key),
                CONSTRAINT uq_dim_instrument_symbol UNIQUE (symbol)
            )
        """)

        # 3. DIM_ACCOUNT (Type 2 SCD)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS dim_account (
                account_key     BIGINT       NOT NULL,
                account_id      VARCHAR(32)  NOT NULL,
                holder_name     VARCHAR(255) NOT NULL,
                status          VARCHAR(20)  NOT NULL,
                effective_date  DATE         NOT NULL,
                end_date        DATE,
                is_current      BOOLEAN      NOT NULL,
                source_id       BIGINT       NOT NULL,
                loaded_at       TIMESTAMP    NOT NULL,
                CONSTRAINT pk_dim_account PRIMARY KEY (account_key)
            )
        """)

        # 4. FACT_TRADES
        conn.execute("""
            CREATE TABLE IF NOT EXISTS fact_trades (
                trade_key       BIGINT        NOT NULL,
                account_key     BIGINT        NOT NULL,
                instrument_key  BIGINT        NOT NULL,
                date_key        INTEGER       NOT NULL,
                side            VARCHAR(4)    NOT NULL,
                quantity        INTEGER       NOT NULL,
                price           DECIMAL(18,2) NOT NULL,
                status          VARCHAR(20)   NOT NULL,
                executed_price  DECIMAL(18,2),
                trade_value     DECIMAL(18,2) NOT NULL,
                source_order_id VARCHAR(36)   NOT NULL,
                created_at      TIMESTAMP     NOT NULL,
                executed_on     TIMESTAMP,
                loaded_at       TIMESTAMP     NOT NULL,
                CONSTRAINT pk_fact_trades PRIMARY KEY (trade_key),
                CONSTRAINT uq_fact_trades_source UNIQUE (source_order_id),
                CONSTRAINT fk_fact_trades_account FOREIGN KEY (account_key) REFERENCES dim_account (account_key),
                CONSTRAINT fk_fact_trades_instrument FOREIGN KEY (instrument_key) REFERENCES dim_instrument (instrument_key),
                CONSTRAINT fk_fact_trades_date FOREIGN KEY (date_key) REFERENCES dim_date (date_key)
            )
        """)

        conn.execute("""
            CREATE INDEX IF NOT EXISTS idx_fact_trades_account_date 
            ON fact_trades(account_key, date_key DESC)
        """)
        conn.execute("""
            CREATE INDEX IF NOT EXISTS idx_fact_trades_created_at 
            ON fact_trades(created_at DESC)
        """)

        # 5. DEAD_LETTER_QUEUE
        conn.execute("""
            CREATE TABLE IF NOT EXISTS dead_letter_queue (
                dlq_key           BIGINT       NOT NULL,
                load_batch_id     VARCHAR      NOT NULL,
                source_order_id   VARCHAR(36),
                source_account_id BIGINT,
                source_symbol     VARCHAR(20),
                error_reason      VARCHAR      NOT NULL,
                error_detail      TEXT,
                row_data          JSON,
                created_at        TIMESTAMP    NOT NULL,
                CONSTRAINT pk_dead_letter_queue PRIMARY KEY (dlq_key)
            )
        """)

        # 6. LOAD_WATERMARKS
        conn.execute("""
            CREATE TABLE IF NOT EXISTS load_watermarks (
                watermark_key         BIGINT       NOT NULL,
                load_name             VARCHAR      NOT NULL,
                last_loaded_timestamp TIMESTAMP    NOT NULL,
                batch_id              VARCHAR      NOT NULL,
                row_count             INTEGER      NOT NULL,
                dlq_count             INTEGER      NOT NULL,
                load_completed_at     TIMESTAMP    NOT NULL,
                CONSTRAINT pk_load_watermarks PRIMARY KEY (watermark_key),
                CONSTRAINT uq_load_watermarks_name UNIQUE (load_name)
            )
        """)

        conn.commit()


def get_next_surrogate_key(
    conn: duckdb.DuckDBPyConnection, table: str, key_column: str
) -> int:
    """Get the next surrogate key for a table without using sequences."""
    res = conn.execute(f"SELECT COALESCE(MAX({key_column}), 0) + 1 FROM {table}").fetchone()
    return int(res[0]) if res else 1


def get_last_watermark(
    load_name: str = "trades_incremental",
    database_path: str | Path = "analytics.duckdb",
) -> Optional[datetime]:
    """Get the last loaded timestamp watermark."""
    database = Path(database_path)
    if not database.exists():
        return None

    with duckdb.connect(str(database)) as conn:
        try:
            result = conn.execute("""
                SELECT last_loaded_timestamp
                FROM load_watermarks
                WHERE load_name = ?
            """, [load_name]).fetchone()
            return result[0] if result and result[0] else None
        except Exception:
            return None


def record_watermark(
    load_name: str,
    loaded_timestamp: datetime,
    batch_id: str,
    row_count: int,
    dlq_count: int,
    database_path: str | Path = "analytics.duckdb",
) -> None:
    """Record or update a successful load watermark."""
    database = Path(database_path)

    with duckdb.connect(str(database)) as conn:
        now = datetime.now()
        existing = conn.execute("""
            SELECT watermark_key FROM load_watermarks WHERE load_name = ?
        """, [load_name]).fetchone()

        if existing:
            conn.execute("""
                UPDATE load_watermarks
                SET last_loaded_timestamp = ?,
                    batch_id = ?,
                    row_count = ?,
                    dlq_count = ?,
                    load_completed_at = ?
                WHERE load_name = ?
            """, [loaded_timestamp, batch_id, row_count, dlq_count, now, load_name])
        else:
            key = get_next_surrogate_key(conn, "load_watermarks", "watermark_key")
            conn.execute("""
                INSERT INTO load_watermarks 
                (watermark_key, load_name, last_loaded_timestamp, batch_id, row_count, dlq_count, load_completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """, [key, load_name, loaded_timestamp, batch_id, row_count, dlq_count, now])

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
    """Record a failed row to the dead letter queue."""
    database = Path(database_path)

    with duckdb.connect(str(database)) as conn:
        key = get_next_surrogate_key(conn, "dead_letter_queue", "dlq_key")
        row_data_json = json.dumps(row_data, default=str) if row_data else None
        now = datetime.now()

        conn.execute("""
            INSERT INTO dead_letter_queue 
            (dlq_key, load_batch_id, source_order_id, source_account_id, source_symbol, 
             error_reason, error_detail, row_data, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, [key, load_batch_id, str(order_id) if order_id else None, account_id, symbol, error_reason, error_detail, row_data_json, now])

        conn.commit()