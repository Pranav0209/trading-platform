import pandas as pd
import plotly.express as px
import plotly.graph_objects as go
from plotly.subplots import make_subplots


CHART_TEMPLATE = "plotly_white"


def _style_chart(fig):
    fig.update_layout(
        template=CHART_TEMPLATE,
        paper_bgcolor="rgba(0,0,0,0)",
        plot_bgcolor="rgba(0,0,0,0)",
        font={"family": "DM Sans, sans-serif", "color": "#17324d"},
        margin={"l": 56, "r": 24, "t": 24, "b": 48},
        hoverlabel={"bgcolor": "#17324d", "font": {"color": "#ffffff"}},
        legend={"orientation": "h", "y": 1.08, "x": 0},
    )
    fig.update_xaxes(showgrid=False, linecolor="#d9e3ec", tickfont={"color": "#62768a"})
    fig.update_yaxes(gridcolor="#e8eef3", zeroline=False, tickfont={"color": "#62768a"})
    return fig


def create_performance_chart(metrics: pd.DataFrame):
    normalized = metrics.copy()
    normalized["normalized_price"] = normalized["adjclose"] / normalized.groupby("symbol")["adjclose"].transform("first") * 100
    fig = make_subplots(
        rows=2,
        cols=1,
        shared_xaxes=True,
        vertical_spacing=0.14,
        subplot_titles=("Growth of 100 invested", "Direct adjusted-close prices"),
    )
    for symbol, rows in normalized.groupby("symbol", sort=True):
        hover = [rows["adjclose"], rows["daily_return_pct"]]
        fig.add_trace(go.Scatter(x=rows["trade_date"], y=rows["normalized_price"], name=symbol, mode="lines+markers", customdata=list(zip(*hover)), hovertemplate="<b>%{fullData.name}</b><br>%{x|%d %b %Y}<br>Value of 100: %{y:.2f}<br>Direct adjusted close: %{customdata[0]:,.2f}<br>Daily return: %{customdata[1]:.2f}%<extra></extra>"), row=1, col=1)
        fig.add_trace(go.Scatter(x=rows["trade_date"], y=rows["adjclose"], name=f"{symbol} direct", mode="lines", showlegend=False, line={"dash": "dot"}), row=2, col=1)
    fig.update_yaxes(title_text="Portfolio value", row=1, col=1, gridcolor="#e8eef3")
    fig.update_yaxes(title_text="Adjusted close", row=2, col=1, gridcolor="#e8eef3")
    return _style_chart(fig).update_layout(height=680, hovermode="x unified")

def create_volatility_chart(summary: pd.DataFrame):
    fig = px.bar(
        summary,
        x="symbol",
        y="volatility_pct",
        custom_data=["cumulative_return_pct", "average_daily_range_pct"],
        labels={
            "symbol": "Instrument",
            "volatility_pct": "Daily return volatility (%)",
        },
    )

    fig.update_traces(
        marker_color="#e8b25b",
        hovertemplate=(
            "<b>%{x}</b><br>Return volatility: %{y:.2f}%<br>"
            "Cumulative return: %{customdata[0]:.2f}%<br>"
            "Average daily range: %{customdata[1]:.2f}%<extra></extra>"
        ),
    )
    return _style_chart(fig)

def create_volume_price_scatter(metrics: pd.DataFrame, symbol: str):
    rows = metrics[metrics["symbol"] == symbol].copy()
    rows["plot_volume"] = rows["volume"].fillna(0)
    fig = px.scatter(
        rows,
        x="volume",
        y="adjclose",
        size="plot_volume",
        color="daily_return_pct",
        color_continuous_scale=["#3977a9", "#e8b25b", "#2d9b83"],
        custom_data=["trade_date", "close", "high", "low", "daily_return_pct"],
        labels={"volume": "Volume (shares)", "adjclose": "Adjusted close", "daily_return_pct": "Daily return (%)"},
    )
    fig.update_traces(hovertemplate="<b>%{x:,.0f} shares</b><br>Adjusted close: %{y:,.2f}<br>Date: %{customdata[0]|%d %b %Y}<br>Close: %{customdata[1]:,.2f}<br>High / low: %{customdata[2]:,.2f} / %{customdata[3]:,.2f}<br>Daily return: %{customdata[4]:.2f}%<extra></extra>")
    return _style_chart(fig).update_layout(coloraxis_colorbar={"title": "Daily return (%)"})


def create_turnover_chart(summary: pd.DataFrame):
    fig = px.bar(
        summary,
        x="symbol",
        y="average_estimated_turnover",
        custom_data=["cumulative_return_pct", "average_daily_range_pct"],
        labels={
            "symbol": "Instrument",
            "average_estimated_turnover": "Estimated turnover",
        },
    )

    fig.update_traces(
        marker_color="#2d9b83",
        hovertemplate=(
            "<b>%{x}</b><br>Average turnover: %{y:,.0f}<br>"
            "Cumulative return: %{customdata[0]:.2f}%<br>"
            "Average range: %{customdata[1]:.2f}%<extra></extra>"
        ),
    )
    return _style_chart(fig)

def create_price_chart(metrics: pd.DataFrame):
    fig = px.line(
        metrics,
        x="trade_date",
        y="adjclose",
        color="symbol",
        custom_data=["open", "high", "low", "close", "volume", "daily_return_pct"],
        labels={
            "trade_date": "Trading date",
            "adjclose": "Adjusted close",
            "symbol": "Instrument",
        },
    )

    fig.update_traces(
        line={"width": 2.5},
        hovertemplate=(
            "<b>%{fullData.name}</b><br>%{x|%d %b %Y}<br>"
            "Adjusted close: %{y:,.2f}<br>Open: %{customdata[0]:,.2f}<br>"
            "High / low: %{customdata[1]:,.2f} / %{customdata[2]:,.2f}<br>"
            "Close: %{customdata[3]:,.2f}<br>Volume: %{customdata[4]:,.0f}<br>"
            "Daily return: %{customdata[5]:.2f}%<extra></extra>"
        ),
    )
    return _style_chart(fig)