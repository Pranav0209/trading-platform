from __future__ import annotations

import pandas as pd
import duckdb

from sprint_04_analytics_etl.load import load_candles


def test_load_candles_writes_duckdb_file(tmp_path) -> None:
    frame = pd.DataFrame(
        [
            {
                "symbol": "INFY.NS",
                "trade_date": pd.Timestamp("2026-07-01"),
                "currency": "INR",
                "interval": "1d",
                "open": 1.0,
                "high": 2.0,
                "low": 0.5,
                "close": 1.5,
                "adjclose": 1.5,
                "volume": 100.0,
                "synthetic": False,
            }
        ]
    )

    database_path = tmp_path / "analytics.duckdb"
    result_path = load_candles(frame, database_path=database_path)

    assert result_path == database_path
    assert database_path.exists()
    with duckdb.connect(str(database_path)) as connection:
        row_count = connection.execute("SELECT COUNT(*) FROM fact_candles").fetchone()[0]

    assert row_count == 1
