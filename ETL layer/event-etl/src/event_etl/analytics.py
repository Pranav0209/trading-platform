"""Warehouse analytics and verification runner."""

from __future__ import annotations

import sys
from pathlib import Path
import duckdb


def format_table(headers: list[str], rows: list[tuple]) -> str:
    """Format tabular data cleanly for console output without third-party dependencies."""
    if not rows:
        return "  (No records found)\n"

    col_widths = [len(h) for h in headers]
    str_rows = []
    for row in rows:
        formatted_row = [str(val) if val is not None else "NULL" for val in row]
        str_rows.append(formatted_row)
        for i, val in enumerate(formatted_row):
            col_widths[i] = max(col_widths[i], len(val))

    header_line = " | ".join(h.ljust(col_widths[i]) for i, h in enumerate(headers))
    sep_line = "-+-".join("-" * col_widths[i] for i in range(len(headers)))
    data_lines = [
        " | ".join(val.ljust(col_widths[i]) for i, val in enumerate(r))
        for r in str_rows
    ]

    return f"  {header_line}\n  {sep_line}\n" + "\n".join(f"  {line}" for line in data_lines) + "\n"


def run_warehouse_analytics(db_path: str | Path = "analytics.duckdb") -> None:
    """Execute integrity checks and analytical summaries against DuckDB."""
    path = Path(db_path)
    if not path.exists():
        print(f"Error: Database file '{path}' not found. Please initialize and load the warehouse first.")
        return

    print("=" * 80)
    print(f"  ZKUBA TRADING PLATFORM — ANALYTICS WAREHOUSE VERIFICATION")
    print(f"  Database: {path.resolve()}")
    print("=" * 80)

    with duckdb.connect(str(path), read_only=True) as conn:
        # 1. TABLE ROW COUNTS
        print("\n[1] TABLE ROW COUNTS")
        counts_sql = """
            SELECT 'dim_date' AS table_name, count(*) AS row_count FROM dim_date
            UNION ALL SELECT 'dim_instrument', count(*) FROM dim_instrument
            UNION ALL SELECT 'dim_account', count(*) FROM dim_account
            UNION ALL SELECT 'fact_trades', count(*) FROM fact_trades
            UNION ALL SELECT 'dead_letter_queue', count(*) FROM dead_letter_queue
            UNION ALL SELECT 'load_watermarks', count(*) FROM load_watermarks
        """
        rows = conn.execute(counts_sql).fetchall()
        print(format_table(["Table Name", "Row Count"], rows))

        # 2. REFERENTIAL INTEGRITY AUDIT
        print("[2] STAR SCHEMA REFERENTIAL INTEGRITY CHECK")
        orphan_acc = conn.execute("""
            SELECT count(*) FROM fact_trades f
            LEFT JOIN dim_account a ON f.account_key = a.account_key
            WHERE a.account_key IS NULL
        """).fetchone()[0]

        orphan_inst = conn.execute("""
            SELECT count(*) FROM fact_trades f
            LEFT JOIN dim_instrument i ON f.instrument_key = i.instrument_key
            WHERE i.instrument_key IS NULL
        """).fetchone()[0]

        orphan_date = conn.execute("""
            SELECT count(*) FROM fact_trades f
            LEFT JOIN dim_date d ON f.date_key = d.date_key
            WHERE d.date_key IS NULL
        """).fetchone()[0]

        print(f"  - Orphaned Account Keys:    {orphan_acc} " + ("✓ PASSED" if orphan_acc == 0 else "✗ FAILED"))
        print(f"  - Orphaned Instrument Keys: {orphan_inst} " + ("✓ PASSED" if orphan_inst == 0 else "✗ FAILED"))
        print(f"  - Orphaned Date Keys:       {orphan_date} " + ("✓ PASSED" if orphan_date == 0 else "✗ FAILED"))
        print()

        # 3. FILL RATE BREAKDOWN
        print("[3] ORDER STATUS & FILL RATE BREAKDOWN")
        fill_rate_sql = """
            SELECT 
                status,
                count(*) AS order_count,
                round(count(*) * 100.0 / sum(count(*)) OVER (), 2) || '%' AS pct_of_total,
                coalesce(sum(quantity), 0) AS total_quantity,
                round(coalesce(sum(trade_value), 0.0), 2) AS total_value
            FROM fact_trades
            GROUP BY status
            ORDER BY order_count DESC
        """
        rows = conn.execute(fill_rate_sql).fetchall()
        print(format_table(["Status", "Orders", "Share %", "Total Quantity", "Total Value ($)"], rows))

        # 4. VOLUME & VALUE BY VENUE
        print("[4] TRADING VOLUME & VALUE BY EXCHANGE VENUE")
        venue_sql = """
            SELECT 
                coalesce(i.exchange, 'UNKNOWN') AS venue,
                i.symbol,
                i.name,
                f.side,
                count(f.trade_key) AS orders_count,
                sum(f.quantity) AS total_shares,
                round(sum(f.trade_value), 2) AS total_value
            FROM fact_trades f
            JOIN dim_instrument i ON f.instrument_key = i.instrument_key
            GROUP BY i.exchange, i.symbol, i.name, f.side
            ORDER BY total_value DESC
        """
        rows = conn.execute(venue_sql).fetchall()
        print(format_table(["Venue", "Symbol", "Company Name", "Side", "Orders", "Shares", "Total Value ($)"], rows))

        # 5. ACCOUNT ACTIVITY
        print("[5] ACCOUNT TRADING SUMMARY (CURRENT ACCOUNTS)")
        account_sql = """
            SELECT 
                a.account_id AS account_number,
                a.holder_name,
                a.status AS account_status,
                count(f.trade_key) AS total_orders,
                count(CASE WHEN f.status = 'FILLED' THEN 1 END) AS filled_orders,
                round(coalesce(sum(f.trade_value), 0.0), 2) AS total_value_traded
            FROM dim_account a
            LEFT JOIN fact_trades f ON a.account_key = f.account_key
            WHERE a.is_current = TRUE
            GROUP BY a.account_id, a.holder_name, a.status
            ORDER BY total_value_traded DESC
        """
        rows = conn.execute(account_sql).fetchall()
        print(format_table(["Account No", "Holder Name", "Status", "Orders", "Filled", "Total Value ($)"], rows))

        # 6. INCREMENTAL WATERMARK
        print("[6] INCREMENTAL WATERMARK STATUS")
        watermark_sql = """
            SELECT 
                load_name,
                cast(last_loaded_timestamp as varchar) as last_watermark,
                batch_id,
                row_count,
                dlq_count,
                cast(load_completed_at as varchar) as completed_at
            FROM load_watermarks
        """
        rows = conn.execute(watermark_sql).fetchall()
        print(format_table(["Pipeline Name", "Last Watermark", "Batch ID", "Loaded", "DLQ", "Completed At"], rows))

        # 7. DEAD-LETTER QUEUE
        print("[7] DEAD-LETTER QUEUE STATUS")
        dlq_count = conn.execute("SELECT count(*) FROM dead_letter_queue").fetchone()[0]
        if dlq_count == 0:
            print("  ✓ Dead Letter Queue is empty (0 failed rows).\n")
        else:
            dlq_sql = """
                SELECT error_reason, count(*) as failure_count, min(error_detail) as sample_error
                FROM dead_letter_queue
                GROUP BY error_reason
            """
            rows = conn.execute(dlq_sql).fetchall()
            print(format_table(["Failure Reason", "Failures", "Sample Detail"], rows))

    print("=" * 80)
    print("  Verification complete.")
    print("=" * 80)
