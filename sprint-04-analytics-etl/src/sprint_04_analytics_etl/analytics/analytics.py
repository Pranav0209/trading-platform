from __future__ import annotations

from pathlib import Path

import duckdb
import pandas as pd


def load_candles(
    database_path: str | Path = "analytics.duckdb",
) -> pd.DataFrame:
    with duckdb.connect(str(database_path), read_only=True) as connection:
        return connection.execute(
            """
            SELECT
                symbol,
                trade_date,
                open,
                high,
                low,
                close,
                adjclose,
                volume,
                synthetic
            FROM fact_candles
            ORDER BY symbol, trade_date
            """
        ).df()


def calculate_daily_metrics(frame: pd.DataFrame) -> pd.DataFrame:
    result = frame.copy()

    result["daily_return_pct"] = (
        result.groupby("symbol")["adjclose"]
        .pct_change()
        .mul(100)
    )

    result["daily_range_pct"] = (
        (result["high"] - result["low"])
        / result["low"]
        * 100
    )

    return result


def calculate_symbol_summary(
    frame: pd.DataFrame,
) -> pd.DataFrame:
    metrics = calculate_daily_metrics(frame)

    return (
        metrics.groupby("symbol")
        .agg(
            average_daily_range_pct=(
                "daily_range_pct",
                "mean",
            ),
        )
        .reset_index()
    )