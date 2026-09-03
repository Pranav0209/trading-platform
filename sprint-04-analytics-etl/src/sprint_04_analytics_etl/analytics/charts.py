from __future__ import annotations

import base64
from io import BytesIO

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
import pandas as pd


def _to_data_uri(fig) -> str:
    buffer = BytesIO()
    fig.savefig(buffer, format="png")
    plt.close(fig)
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
    return f"data:image/png;base64,{encoded}"


def create_performance_chart(metrics: pd.DataFrame) -> str:
    frame = metrics.sort_values(["symbol", "trade_date"]).copy()
    frame["normalized_price"] = (
        frame["adjclose"]
        / frame.groupby("symbol")["adjclose"].transform("first")
        * 100
    )

    fig, axes = plt.subplots(2, 1, figsize=(8, 6))
    for symbol, rows in frame.groupby("symbol"):
        axes[0].plot(rows["trade_date"], rows["adjclose"], label=symbol)
        axes[1].plot(rows["trade_date"], rows["normalized_price"], label=symbol)

    axes[0].set_title("Misleading - Direct adjusted-close price")
    axes[0].set_ylabel("Adjusted close")
    axes[0].legend()

    axes[1].set_title("Correct - Growth of 100 (normalized)")
    axes[1].set_xlabel("Trading date")
    axes[1].set_ylabel("Value of 100")
    axes[1].legend()

    fig.tight_layout()
    return _to_data_uri(fig)


def create_range_chart(summary: pd.DataFrame) -> str:
    frame = summary.sort_values("symbol")
    fig, axis = plt.subplots(figsize=(6, 4))
    axis.bar(frame["symbol"], frame["average_daily_range_pct"], color="#1E6FA8")
    axis.set_title("Average daily trading range by instrument (%)")
    axis.set_xlabel("Instrument")
    axis.set_ylabel("Average daily range (%)")
    fig.tight_layout()
    return _to_data_uri(fig)


def create_volume_price_chart(metrics: pd.DataFrame, symbol: str) -> str:
    rows = metrics[(metrics["symbol"] == symbol) & metrics["volume"].notna()].copy()
    rows["absolute_return_pct"] = rows["daily_return_pct"].abs()

    fig, axis = plt.subplots(figsize=(6, 4))
    axis.scatter(rows["volume"], rows["absolute_return_pct"], color="#1E6FA8")

    if len(rows) >= 2:
        correlation = rows["volume"].corr(rows["absolute_return_pct"])
        axis.text(0.02, 0.94, f"Pearson correlation (r): {correlation:.2f}", transform=axis.transAxes)

    axis.set_title(f"Volume vs. absolute daily return: {symbol}")
    axis.set_xlabel("Volume (shares)")
    axis.set_ylabel("Absolute daily return (%)")
    fig.tight_layout()
    return _to_data_uri(fig)
