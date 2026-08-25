from __future__ import annotations

import json
from pathlib import Path

import pandas as pd
import pytest

from sprint_04_analytics_etl.transform import transform_candles


FIXTURES_DIR = Path(__file__).resolve().parents[1] / "fixtures"


def _load_fixture(name: str) -> dict:
    return json.loads((FIXTURES_DIR / name).read_text(encoding="utf-8"))


def test_transform_candles_keeps_valid_rows_and_null_volume() -> None:
    frame = transform_candles(_load_fixture("candles-infy-ns-2026-07.json"))

    assert list(frame["symbol"].unique()) == ["INFY.NS"]
    assert len(frame) == 8
    assert frame["trade_date"].dtype == "datetime64[ns]"
    assert pd.isna(frame.loc[frame["trade_date"] == pd.Timestamp("2026-07-06"), "volume"]).all()
    assert frame.loc[frame["trade_date"] == pd.Timestamp("2026-07-08"), "synthetic"].item() is True


def test_rejects_a_high_below_a_low() -> None:
    with pytest.raises(ValueError, match="high below low"):
        transform_candles(_load_fixture("candles-malformed.json"))