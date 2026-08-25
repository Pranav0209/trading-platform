from __future__ import annotations

from pathlib import Path

import duckdb
import pandas as pd


def load_candles(frame: pd.DataFrame, *, database_path: str | Path = "analytics.duckdb") -> Path:
    database = Path(database_path)
    database.parent.mkdir(parents=True, exist_ok=True)

    with duckdb.connect(str(database)) as connection:
        connection.register("incoming_candles", frame)
        connection.execute("CREATE OR REPLACE TABLE fact_candles AS SELECT * FROM incoming_candles")

    return database