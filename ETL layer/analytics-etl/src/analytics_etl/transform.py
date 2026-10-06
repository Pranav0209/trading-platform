from __future__ import annotations

from datetime import datetime
from typing import Any

import pandas as pd


REQUIRED_PRICE_FIELDS = (
    "open",
    "high",
    "low",
    "close",
    "adjclose",
)


def _coerce_float(value: Any) -> float:
    if isinstance(value, bool):
        raise TypeError("boolean is not a valid price")
    return float(value)


def _coerce_volume(value: Any) -> float | None:
    if value is None:
        return None

    if isinstance(value, bool):
        raise TypeError("boolean is not a valid volume")

    volume = float(value)

    if volume < 0:
        raise ValueError("volume cannot be negative")

    return volume


def _parse_date(value: Any) -> pd.Timestamp | None:
    if not isinstance(value, str):
        return None

    try:
        parsed = datetime.strptime(value, "%Y-%m-%d")
        return pd.Timestamp(parsed.date())
    except ValueError:
        return None


def transform_candles(
    raw_payload: dict[str, Any],
) -> pd.DataFrame:
    data = raw_payload.get("data") or {}
    meta = raw_payload.get("meta") or {}
    candles = data.get("candles") or []

    symbol = data.get("symbol") or meta.get("symbol")
    currency = data.get("currency")
    interval = data.get("interval")

    cleaned_rows: list[dict[str, Any]] = []
    seen_dates: set[pd.Timestamp] = set()

    for candle in candles:
        # Invalid/non-ISO date -> DROP
        trade_date = _parse_date(candle.get("date"))

        if trade_date is None:
            continue

        # Duplicate date -> keep first, DROP duplicate
        if trade_date in seen_dates:
            continue

        # Missing required price -> DROP
        if any(
            field not in candle
            for field in REQUIRED_PRICE_FIELDS
        ):
            continue

        try:
            prices = {
                field: _coerce_float(candle[field])
                for field in REQUIRED_PRICE_FIELDS
            }

            volume = _coerce_volume(
                candle.get("volume")
            )

        except (TypeError, ValueError):
            # Invalid numeric value such as "n/a"
            # or negative volume -> DROP
            continue

        # Impossible OHLC data -> FAIL LOUDLY
        if prices["high"] < prices["low"]:
            raise ValueError(
                f"{symbol} candle on "
                f"{candle.get('date')} has high below low"
            )

        row = {
            "symbol": symbol,
            "trade_date": trade_date,
            "currency": currency,
            "interval": interval,
            "open": prices["open"],
            "high": prices["high"],
            "low": prices["low"],
            "close": prices["close"],
            "adjclose": prices["adjclose"],
            "volume": volume,
            "synthetic": bool(
                candle.get("synthetic", False)
            ),
        }

        seen_dates.add(trade_date)
        cleaned_rows.append(row)

    frame = pd.DataFrame(cleaned_rows)

    if frame.empty:
        return frame

    frame = (
        frame
        .sort_values(
            ["symbol", "trade_date"]
        )
        .reset_index(drop=True)
    )

    frame["trade_date"] = pd.to_datetime(
        frame["trade_date"],
        errors="raise",
    ).astype("datetime64[ns]")

    return frame[
        [
            "symbol",
            "trade_date",
            "currency",
            "interval",
            "open",
            "high",
            "low",
            "close",
            "adjclose",
            "volume",
            "synthetic",
        ]
    ]