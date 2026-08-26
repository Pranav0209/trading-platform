from __future__ import annotations
from pathlib import Path
from .charts import (
    create_performance_chart,
    create_price_chart,
    create_range_chart,
    create_turnover_chart,
)

def build_dashboard(
    metrics: pd.DataFrame,
    summary: pd.DataFrame,
    output_path: str | Path = "artefacts/report.html",
) -> Path:

    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)

    price_chart = create_price_chart(metrics)
    performance_chart = create_performance_chart(summary)
    range_chart = create_range_chart(summary)
    turnover_chart = create_turnover_chart(summary)

    html = f"""
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8">
        <title>Market Analytics Dashboard</title>
    </head>

    <body>

        <h1>Market Analytics Dashboard</h1>

        <h2>Price Trend</h2>
        {price_chart.to_html(full_html=False, include_plotlyjs=True)}

        <h2>Performance</h2>
        {performance_chart.to_html(
            full_html=False,
            include_plotlyjs=False
        )}

        <h2>Price Movement</h2>
        {range_chart.to_html(
            full_html=False,
            include_plotlyjs=False
        )}

        <h2>Trading Activity</h2>
        {turnover_chart.to_html(
            full_html=False,
            include_plotlyjs=False
        )}

    </body>
    </html>
    """

    output.write_text(html, encoding="utf-8")

    return output