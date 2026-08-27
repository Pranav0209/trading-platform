from __future__ import annotations

import json
from pathlib import Path

import pandas as pd

from .charts2 import (
    create_performance_chart,
    create_volatility_chart,
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
    volatility_image = create_volatility_chart(summary)
    scatter_images = {
        symbol: create_volume_price_chart(metrics, symbol)
        for symbol in symbols
    }

    best_row = summary.loc[summary["cumulative_return_pct"].idxmax()]
    most_volatile = summary.loc[summary["volatility_pct"].idxmax()]
    start_date = metrics["trade_date"].min().strftime("%d %b %Y")
    end_date = metrics["trade_date"].max().strftime("%d %b %Y")

    html = f"""<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>ZKUBA | Market Analytics</title>
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
        <div><div class="eyebrow">ZKUBA / Market analytics</div><h1>Three views for better judgement</h1><p>Performance, risk and activity from cleaned candle data between {start_date} and {end_date}.</p></div>
        <div class="controls"><label for="scatter-symbol">Volume study</label><select id="scatter-symbol">{''.join(f'<option value="{symbol}">{symbol}</option>' for symbol in symbols)}</select></div>
    </header>
    <section class="kpis"><article class="kpi"><div class="label">Best normalized result</div><div class="value">{best_row['cumulative_return_pct']:.2f}%</div><div class="note">{best_row['symbol']} ending value from a 100 starting base</div></article><article class="kpi"><div class="label">Highest volatility</div><div class="value">{most_volatile['volatility_pct']:.2f}%</div><div class="note">{most_volatile['symbol']} daily-return standard deviation</div></article><article class="kpi"><div class="label">Symbols compared</div><div class="value">{len(symbols)}</div><div class="note">All available instruments plotted together</div></article></section>
    <section class="panel"><div class="panel-head"><h2>1. Performance comparison</h2><span class="note">Every instrument starts at 100; direct adjusted-close prices appear below.</span></div><div class="chart"><img src="{performance_image}" alt="Normalized growth of 100 and direct adjusted-close price lines for all symbols"></div></section>
    <section class="panel"><div class="panel-head"><h2>2. Volatility does not determine direction</h2><span class="note">Volatility is compared with cumulative return, so positive and negative outcomes are visible.</span></div><div class="chart"><img src="{volatility_image}" alt="Volatility plotted against cumulative return for each symbol"></div></section>
    <section class="panel"><div class="panel-head"><h2>3. Volume does not imply extreme movement</h2><span class="note">Choose one instrument. The y-axis uses absolute daily return, measuring movement size regardless of direction.</span></div><div class="chart"><img id="scatter-chart" src="{scatter_images[symbols[0]]}" alt="Volume plotted against absolute daily return for {symbols[0]}"></div></section>
    <footer>Source: DuckDB fact_candles · Matplotlib static charts · Selected-stock drill-down is available above.</footer>
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
