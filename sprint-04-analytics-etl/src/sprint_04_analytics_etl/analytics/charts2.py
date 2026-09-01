from __future__ import annotations

import base64
from io import BytesIO

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
import pandas as pd


COLORS = {
    "INFY.NS": "#3977a9",
    "RELIANCE.NS": "#2d9b83",
    "TATASTEEL.BO": "#d45d5d",
}
FALLBACK_COLORS = ["#3977a9", "#2d9b83", "#d45d5d", "#e8b25b"]


def _color(symbol: str, index: int = 0) -> str:
    return COLORS.get(symbol, FALLBACK_COLORS[index % len(FALLBACK_COLORS)])


def _to_data_uri(fig) -> str:
    buffer = BytesIO()
    fig.savefig(buffer, format="png", dpi=150, bbox_inches="tight", facecolor="#ffffff")
    plt.close(fig)
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
    return f"data:image/png;base64,{encoded}"


def create_performance_chart(metrics: pd.DataFrame) -> str:
    """Return one image containing normalized and direct price comparisons."""
    frame = metrics.sort_values(["symbol", "trade_date"]).copy()
    frame["normalized_price"] = (
        frame["adjclose"]
        / frame.groupby("symbol")["adjclose"].transform("first")
        * 100
    )

    fig, axes = plt.subplots(2, 1, figsize=(12, 8), sharex=True)
    fig.patch.set_facecolor("#ffffff")
    fig.suptitle("Performance comparison: direct price and growth of 100", fontsize=16, fontweight="bold", color="#17324d", x=0.08, ha="left")

    for index, (symbol, rows) in enumerate(frame.groupby("symbol", sort=True)):
        color = _color(symbol, index)
        axes[0].plot(rows["trade_date"], rows["adjclose"], label=symbol, color=color, linewidth=2.2)
        axes[1].plot(rows["trade_date"], rows["normalized_price"], label=symbol, color=color, linewidth=2.4)

    axes[1].axhline(100, color="#9aaab8", linestyle="--", linewidth=1)
    axes[0].set_ylabel("Adjusted close", color="#62768a")
    axes[0].set_title("Direct price path", loc="left", color="#17324d", fontsize=11, pad=10)
    axes[1].set_ylabel("Value of 100", color="#62768a")
    axes[1].set_title("Comparable return path", loc="left", color="#17324d", fontsize=11, pad=10)
    axes[1].set_xlabel("Trading date", color="#62768a")

    for axis in axes:
        axis.grid(axis="y", color="#e8eef3", linewidth=0.8)
        axis.spines[["top", "right"]].set_visible(False)
        axis.spines[["left", "bottom"]].set_color("#d9e3ec")
        axis.tick_params(colors="#62768a")
    handles, labels = axes[0].get_legend_handles_labels()
    fig.legend(
        handles,
        labels,
        loc="center left",
        bbox_to_anchor=(0.84, 0.5),
        frameon=False,
        title="Instrument",
    )
    fig.tight_layout(rect=[0, 0, 0.82, 0.95])
    return _to_data_uri(fig)


def create_range_chart(summary: pd.DataFrame) -> str:
    """Show the average daily high-to-low trading range by instrument."""
    frame = summary.dropna(subset=["average_daily_range_pct"]).sort_values("symbol")
    fig, axis = plt.subplots(figsize=(12, 5.5))
    fig.patch.set_facecolor("#ffffff")
    bars = axis.bar(
        frame["symbol"],
        frame["average_daily_range_pct"],
        color=[_color(symbol, index) for index, symbol in enumerate(frame["symbol"])],
        width=0.58,
    )
    axis.bar_label(bars, fmt="%.2f%%", padding=5, color="#17324d", fontweight="bold")
    axis.set_title("Average daily trading range by instrument", fontsize=16, fontweight="bold", color="#17324d", loc="center")
    axis.set_xlabel("Instrument", color="#62768a")
    axis.set_ylabel("Average daily range (%)", color="#62768a")
    axis.grid(axis="y", color="#e8eef3", linewidth=0.8)
    axis.spines[["top", "right"]].set_visible(False)
    axis.spines[["left", "bottom"]].set_color("#d9e3ec")
    axis.tick_params(colors="#62768a")
    fig.tight_layout()
    return _to_data_uri(fig)


def create_volume_price_chart(metrics: pd.DataFrame, symbol: str) -> str:
    """Show volume against absolute daily movement for one selected stock."""
    rows = metrics[(metrics["symbol"] == symbol) & metrics["volume"].notna()].copy()
    rows["absolute_return_pct"] = rows["daily_return_pct"].abs()
    fig, axis = plt.subplots(figsize=(12, 5.5))
    fig.patch.set_facecolor("#ffffff")
    axis.scatter(rows["volume"], rows["absolute_return_pct"], s=75, alpha=0.78, color=_color(symbol), edgecolor="white", linewidth=0.7)

    correlation = None
    if len(rows) >= 2:
        correlation = rows["volume"].corr(rows["absolute_return_pct"])
        axis.text(0.02, 0.94, f"Pearson correlation (r): {correlation:.2f}", transform=axis.transAxes, color="#17324d", fontweight="bold")
    axis.set_title(f"Volume alone is not a reliable predictor of extreme movement: {symbol}", fontsize=16, fontweight="bold", color="#17324d", loc="left")
    axis.set_xlabel("Volume (shares)", color="#62768a")
    axis.set_ylabel("Absolute daily return (%)", color="#62768a")
    interpretation = "Moderate association; volume is not sufficient on its own." if correlation is not None and abs(correlation) < 0.7 else "Strong association in this sample; investigate other periods before generalising."
    axis.grid(color="#e8eef3", linewidth=0.8)
    axis.spines[["top", "right"]].set_visible(False)
    axis.spines[["left", "bottom"]].set_color("#d9e3ec")
    axis.tick_params(colors="#62768a")
    fig.tight_layout()
    return _to_data_uri(fig)
