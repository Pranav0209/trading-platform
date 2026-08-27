import pandas as pd
import plotly.express as px


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


def create_performance_chart(summary: pd.DataFrame):
    fig = px.bar(
        summary,
        x="symbol",
        y="cumulative_return_pct",
        custom_data=["first_adjclose", "last_adjclose", "average_daily_return_pct"],
        labels={
            "symbol": "Instrument",
            "cumulative_return_pct": "Cumulative return (%)",
        },
    )

    fig.update_traces(
        hovertemplate=(
            "<b>%{x}</b><br>Cumulative return: %{y:.2f}%<br>"
            "Start: %{customdata[0]:,.2f}<br>End: %{customdata[1]:,.2f}<br>"
            "Average daily return: %{customdata[2]:.2f}%<extra></extra>"
        ),
    )
    return _style_chart(fig)

def create_range_chart(summary: pd.DataFrame):
    fig = px.bar(
        summary,
        x="symbol",
        y="average_daily_range_pct",
        custom_data=["cumulative_return_pct", "average_daily_return_pct"],
        labels={
            "symbol": "Instrument",
            "average_daily_range_pct": "Average daily range (%)",
        },
    )

    fig.update_traces(
        marker_color="#e8b25b",
        hovertemplate=(
            "<b>%{x}</b><br>Average daily range: %{y:.2f}%<br>"
            "Cumulative return: %{customdata[0]:.2f}%<br>"
            "Average daily return: %{customdata[1]:.2f}%<extra></extra>"
        ),
    )
    return _style_chart(fig)

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