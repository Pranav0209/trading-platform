from __future__ import annotations

import json
from pathlib import Path

import pandas as pd

from .charts2 import (
    create_performance_chart,
    create_range_chart,
    create_volume_price_chart,
)


def build_dashboard(
    metrics: pd.DataFrame,
    summary: pd.DataFrame,
    output_path: str | Path = "artefacts/report.html",
) -> Path:
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)

    symbols = sorted(summary["symbol"].dropna().unique().tolist())
    if not symbols:
        raise ValueError("Cannot build dashboard without symbols")

    performance_image = create_performance_chart(metrics)
    range_image = create_range_chart(summary)
    scatter_images = {
        symbol: create_volume_price_chart(metrics, symbol)
        for symbol in symbols
    }

    best_row = summary.loc[summary["cumulative_return_pct"].idxmax()]
    start_date = metrics["trade_date"].min().strftime("%d %b %Y")
    end_date = metrics["trade_date"].max().strftime("%d %b %Y")

    html = f"""<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title></title>
    <style>
        :root {{ --ink:#17324d; --muted:#62768a; --line:#dfe8ef; --canvas:#f4f7f9; --paper:#fff; --teal:#2d9b83; }}
        * {{ box-sizing:border-box; }}
        body {{ margin:0; background:var(--canvas); color:var(--ink); font-family:"Segoe UI", sans-serif; }}
        main {{ max-width:1400px; margin:auto; padding:34px; }}
        header {{ display:flex; justify-content:space-between; align-items:end; gap:24px; margin-bottom:24px; }}
        .eyebrow {{ color:var(--teal); font-size:12px; font-weight:800; letter-spacing:.12em; text-transform:uppercase; }}
        h1 {{ margin:7px 0; font:500 clamp(30px,4vw,52px) Georgia, serif; }}
        h2 {{ margin:0; font-size:19px; }}
        p, .note {{ color:var(--muted); font-size:13px; }}
        .controls {{ display:flex; align-items:center; gap:10px; }}
        select {{ min-width:180px; padding:10px 12px; border:1px solid var(--line); border-radius:6px; background:var(--paper); color:var(--ink); font:inherit; }}
        .kpis {{ display:grid; grid-template-columns:repeat(3,1fr); gap:14px; margin-bottom:18px; }}
        .kpi, .panel {{ background:var(--paper); border:1px solid var(--line); border-radius:8px; box-shadow:0 8px 24px rgba(23,50,77,.05); }}
        .kpi {{ padding:18px 20px; }}
        .label {{ color:var(--muted); font-size:11px; font-weight:800; letter-spacing:.07em; text-transform:uppercase; }}
        .value {{ margin-top:10px; font-size:27px; font-weight:800; }}
        .panel {{ padding:22px; margin-bottom:18px; }}
        .panel-head {{ display:flex; justify-content:space-between; align-items:baseline; gap:16px; margin-bottom:8px; }}
        .chart {{ width:100%; overflow:hidden; }}
        .chart img {{ display:block; width:100%; height:auto; }}
        footer {{ color:var(--muted); font-size:12px; padding-top:3px; }}
        @media (max-width:760px) {{ main {{ padding:20px 14px; }} header {{ align-items:start; flex-direction:column; }} .kpis {{ grid-template-columns:1fr; }} .panel {{ padding:14px; }} .panel-head {{ align-items:start; flex-direction:column; gap:4px; }} }}
    </style>
</head>
<body>
<main>
    <header>
        <div><div class="eyebrow">Sprint 4</div><h1>ZKUBA</h1><p>Zero Knowledge Unified Brokerage and Analysis</p></div>
        <div class="controls"><label for="scatter-symbol">Volume study</label><select id="scatter-symbol">{''.join(f'<option value="{symbol}">{symbol}</option>' for symbol in symbols)}</select></div>
    </header>
    <section class="panel"><div class="panel-head"><h2>1. Performance comparison</h2><span class="note">Direct adjusted-close prices appear first; the normalized growth-of-100 comparison follows below.</span></div><div class="chart"><img src="{performance_image}" alt="Direct adjusted-close prices followed by normalized growth of 100 for all symbols"></div></section>
    <section class="panel"><div class="panel-head"><h2>2. Average daily trading range by instrument</h2><span class="note">A wider range indicates larger typical high-to-low movement during a trading session.</span></div><div class="chart"><img src="{range_image}" alt="Bar chart of average daily trading range for all symbols"></div></section>
    <section class="panel"><div class="panel-head"><h2>3. Volume alone is not a reliable predictor of extreme movement</h2><span class="note">Choose one instrument. Pearson's r is shown on the chart; the y-axis uses absolute daily return.</span></div><div class="chart"><img id="scatter-chart" src="{scatter_images[symbols[0]]}" alt="Volume plotted against absolute daily return for {symbols[0]}"></div></section>
</main>
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
