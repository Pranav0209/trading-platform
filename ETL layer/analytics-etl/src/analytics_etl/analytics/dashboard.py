from __future__ import annotations

import json
from pathlib import Path

import pandas as pd

from .charts import (
    create_performance_chart,
    create_range_chart,
    create_volume_price_chart,
)


CLAIMS = {
    "performance-chart": "higher stock price doesn't imply it'll give more returns. Hence normalized charts are needed for actual comparision",
    "range-chart": "Higher volatality doesn't have any signifance in the performance of the stock. We can see a correlation that one with the highest volatality has the best performance",
    "volume-return-chart": "High volume of trade per day doesn't mean the returns are high",
}


def build_dashboard(
    metrics: pd.DataFrame,
    summary: pd.DataFrame,
    output_path: str | Path = "artefacts/report.html",
) -> Path:
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)

    symbols = sorted(summary["symbol"])
    if not symbols:
        raise ValueError("Cannot build dashboard without symbols")

    performance_image = create_performance_chart(metrics)
    range_image = create_range_chart(summary)
    scatter_images = {
        symbol: create_volume_price_chart(metrics, symbol)
        for symbol in symbols
    }

    html = f"""<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>ZKUBA</title>
    <style>
        body {{ font-family: sans-serif; max-width: 900px; margin: 20px auto; background-color: #F5FBFF; }}
        section {{ margin-bottom: 30px; }}
        img {{ max-width: 100%; }}
        .claim {{ font-weight: bold; }}
    </style>
</head>
<body>
    <h1>Zero Knowledge Unified Brokerage and Analytics </h1>
    <h2>Historical Trading Data Analytical Dashboard</h2>

    <section id="performance-chart">
        <h2>1. Performance comparison</h2>
         <p class="claim">claim 1: {CLAIMS['performance-chart']}</p>
        <img src="{performance_image}" alt="Direct adjusted-close prices followed by normalized growth of 100 for all symbols">
    </section>

    <section id="range-chart">
        <h2>2. Average daily trading range by instrument</h2>
        <p class="claim">claim 2: {CLAIMS['range-chart']}</p>
        <img src="{range_image}" alt="Bar chart of average daily trading range for all symbols">
    </section>

    <section id="volume-return-chart">
        <h2>3. Volume vs absolute daily return </h2>
         <p class="claim">claim 3: {CLAIMS['volume-return-chart']}</p>
        <img id="scatter-chart" src="{scatter_images[symbols[0]]}" alt="Volume plotted against absolute daily return for {symbols[0]}">
        <label for="scatter-symbol">Volume study:</label>
                        <select id="scatter-symbol">{''.join(f'<option value="{symbol}">{symbol}</option>' for symbol in symbols)}</select>
    </section>

<script>
const scatterImages = {json.dumps(scatter_images)};
document.getElementById("scatter-symbol").addEventListener("change", event => {{
    const symbol = event.target.value;
    const chart = document.getElementById("scatter-chart");
    chart.src = scatterImages[symbol];
    chart.alt = `Volume plotted against absolute daily return for ${{symbol}}`;
}});
</script>
</body>
</html>"""

    output.write_text(html, encoding="utf-8")
    return output
