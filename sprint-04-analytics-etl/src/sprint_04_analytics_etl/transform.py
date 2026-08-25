from __future__ import annotations

from datetime import datetime
from typing import Any

import pandas as pd


REQUIRED_PRICE_FIELDS = ("open", "high", "low", "close", "adjclose")


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
        return pd.Timestamp(datetime.strptime(value, "%Y-%m-%d").date())
    except ValueError:
        return None


def transform_candles(raw_payload: dict[str, Any]) -> pd.DataFrame:
    data = raw_payload.get("data") or {}
    meta = raw_payload.get("meta") or {}
    candles = data.get("candles") or []

    cleaned_rows: list[dict[str, Any]] = []
    seen_dates: set[pd.Timestamp] = set()

    for candle in candles:
        trade_date = _parse_date(candle.get("date"))
        if trade_date is None or trade_date in seen_dates:
            continue

        try:
            row = {
                "symbol": data.get("symbol") or meta.get("symbol"),
                "trade_date": trade_date,
                "currency": data.get("currency"),
                "interval": data.get("interval"),
                "open": _coerce_float(candle["open"]),
                "high": _coerce_float(candle["high"]),
                "low": _coerce_float(candle["low"]),
                "close": _coerce_float(candle["close"]),
                "adjclose": _coerce_float(candle["adjclose"]),
                "volume": _coerce_volume(candle.get("volume")),
                "synthetic": bool(candle.get("synthetic", False)),
            }
        except KeyError:
            continue
        except (TypeError, ValueError):
            if isinstance(candle.get("high"), (int, float)) and isinstance(candle.get("low"), (int, float)):
                if float(candle["high"]) < float(candle["low"]):
                    raise ValueError(f"{row['symbol']} candle on {candle.get('date')} has high below low")
            continue

        if row["high"] < row["low"]:
            raise ValueError(f"{row['symbol']} candle on {candle.get('date')} has high below low")

        seen_dates.add(trade_date)
        cleaned_rows.append(row)

    frame = pd.DataFrame(cleaned_rows)
    if frame.empty:
        return frame

    frame = frame.sort_values(["symbol", "trade_date"]).reset_index(drop=True)
    frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise").astype("datetime64[ns]")
    return frame[["symbol", "trade_date", "currency", "interval", "open", "high", "low", "close", "adjclose", "volume", "synthetic"]]