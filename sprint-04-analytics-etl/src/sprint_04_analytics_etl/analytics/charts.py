import pandas as pd
import plotly.express as px


def create_performance_chart(summary: pd.DataFrame):
    fig = px.bar(
        summary,
        x="symbol",
        y="cumulative_return_pct",
        title="Cumulative adjusted-close return by instrument",
        labels={
            "symbol": "Instrument",
            "cumulative_return_pct": "Cumulative return (%)",
        },
    )

    fig.update_layout(
        template="plotly_white",
    )

    return fig

def create_range_chart(summary: pd.DataFrame):
    fig = px.bar(
        summary,
        x="symbol",
        y="average_daily_range_pct",
        title="Average daily trading range by instrument",
        labels={
            "symbol": "Instrument",
            "average_daily_range_pct": "Average daily range (%)",
        },
    )

    fig.update_layout(
        template="plotly_white",
    )

    return fig

def create_turnover_chart(summary: pd.DataFrame):
    fig = px.bar(
        summary,
        x="symbol",
        y="average_estimated_turnover",
        title="Average estimated turnover by instrument",
        labels={
            "symbol": "Instrument",
            "average_estimated_turnover": "Estimated turnover",
        },
    )

    fig.update_layout(
        template="plotly_white",
    )

    return fig

def create_price_chart(metrics: pd.DataFrame):
    fig = px.line(
        metrics,
        x="trade_date",
        y="adjclose",
        color="symbol",
        title="Adjusted closing price over time",
        labels={
            "trade_date": "Trading date",
            "adjclose": "Adjusted close",
            "symbol": "Instrument",
        },
    )

    fig.update_layout(
        template="plotly_white",
    )

    return fig