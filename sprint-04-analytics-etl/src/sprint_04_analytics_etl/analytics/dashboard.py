from __future__ import annotations

import json
from pathlib import Path

import pandas as pd

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

    symbols = sorted(summary["symbol"].dropna().unique().tolist())
    start_date = metrics["trade_date"].min().strftime("%d %b %Y")
    end_date = metrics["trade_date"].max().strftime("%d %b %Y")
    total_turnover = metrics["estimated_turnover"].sum(skipna=True)
    latest_date = metrics["trade_date"].max().strftime("%d %b %Y")
    best_row = summary.loc[summary["cumulative_return_pct"].idxmax()]
    avg_range = summary["average_daily_range_pct"].mean()
    report_data = {
        "metrics": metrics.assign(
            trade_date=metrics["trade_date"].dt.strftime("%Y-%m-%d")
        ).where(pd.notna(metrics), None).to_dict("records"),
        "summary": summary.where(pd.notna(summary), None).to_dict("records"),
    }

    def chart_html(fig, div_id: str, include_js: bool = False) -> str:
        return fig.to_html(
            full_html=False,
            include_plotlyjs=include_js,
            div_id=div_id,
            config={"displaylogo": False, "responsive": True},
        )

    html = f"""
    <!doctype html>
    <html>
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Market Intelligence | Sprint 04</title>
        <style>
            :root {{ --ink: #17324d; --muted: #62768a; --line: #dfe8ef; --canvas: #f4f7f9; --paper: #ffffff; --teal: #2d9b83; }}
            * {{ box-sizing: border-box; }}
            body {{ margin: 0; background: var(--canvas); color: var(--ink); font-family: "DM Sans", "Segoe UI", sans-serif; }}
            .shell {{ max-width: 1440px; margin: 0 auto; padding: 34px 34px 54px; }}
            .topbar {{ display: flex; justify-content: space-between; align-items: flex-end; gap: 24px; margin-bottom: 28px; }}
            .eyebrow {{ color: var(--teal); font-size: 12px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }}
            h1 {{ margin: 7px 0 8px; font-family: Georgia, serif; font-size: clamp(30px, 4vw, 52px); font-weight: 500; }}
            .subtitle {{ margin: 0; color: var(--muted); font-size: 15px; }}
            .controls {{ display: flex; align-items: center; gap: 10px; flex-wrap: wrap; justify-content: flex-end; }}
            label {{ color: var(--muted); font-size: 12px; font-weight: 700; }}
            select {{ min-width: 170px; border: 1px solid var(--line); border-radius: 6px; background: var(--paper); color: var(--ink); padding: 11px 34px 11px 12px; font: inherit; cursor: pointer; }}
            .kpis {{ display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 22px; }}
            .kpi, .panel {{ background: var(--paper); border: 1px solid var(--line); border-radius: 8px; box-shadow: 0 8px 24px rgba(23,50,77,.05); }}
            .kpi {{ padding: 20px; min-height: 126px; }}
            .kpi-label {{ color: var(--muted); font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .06em; }}
            .kpi-value {{ margin-top: 12px; font-size: 29px; font-weight: 800; }}
            .kpi-note {{ margin-top: 7px; color: var(--muted); font-size: 12px; }}
            .panel {{ padding: 22px; margin-bottom: 18px; }}
            .panel-head {{ display: flex; justify-content: space-between; align-items: baseline; gap: 16px; margin-bottom: 8px; }}
            h2 {{ margin: 0; font-size: 18px; }}
            .panel-note {{ color: var(--muted); font-size: 12px; }}
            .grid-2 {{ display: grid; grid-template-columns: 1.35fr 1fr; gap: 18px; }}
            .chart {{ min-height: 350px; }}
            .insight {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-top: 14px; }}
            .insight-item {{ padding: 14px; border-left: 3px solid var(--teal); background: #f7fafb; }}
            .insight-item strong {{ display: block; font-size: 13px; margin-bottom: 5px; }}
            .insight-item span {{ color: var(--muted); font-size: 12px; line-height: 1.5; }}
            .footer {{ color: var(--muted); font-size: 12px; padding-top: 6px; }}
            @media (max-width: 900px) {{ .shell {{ padding: 24px 18px 40px; }} .topbar {{ align-items: flex-start; flex-direction: column; }} .controls {{ justify-content: flex-start; }} .kpis {{ grid-template-columns: repeat(2, 1fr); }} .grid-2 {{ grid-template-columns: 1fr; }} }}
            @media (max-width: 520px) {{ .kpis, .insight {{ grid-template-columns: 1fr; }} .panel {{ padding: 15px; }} }}
        </style>
    </head>
    <body>
        <main class="shell">
            <header class="topbar">
                <div><div class="eyebrow">Market intelligence / Sprint 04</div><h1>Signal, not noise.</h1><p class="subtitle">A decision-ready view of adjusted prices, returns, range and trading activity.</p></div>
                <div class="controls"><label for="symbol-filter">Focus instrument</label><select id="symbol-filter"><option value="ALL">All instruments</option>{''.join(f'<option value="{symbol}">{symbol}</option>' for symbol in symbols)}</select><label for="window-filter">Window</label><select id="window-filter"><option value="all">Full history</option><option value="30">Last 30 observations</option><option value="10">Last 10 observations</option></select></div>
            </header>
            <section class="kpis"><article class="kpi"><div class="kpi-label">Instruments tracked</div><div class="kpi-value" id="kpi-symbols">{len(symbols)}</div><div class="kpi-note">NSE and BSE coverage in scope</div></article><article class="kpi"><div class="kpi-label">Best cumulative return</div><div class="kpi-value" id="kpi-return">{best_row['cumulative_return_pct']:.2f}%</div><div class="kpi-note" id="kpi-return-note">{best_row['symbol']} over {start_date} to {end_date}</div></article><article class="kpi"><div class="kpi-label">Average daily range</div><div class="kpi-value" id="kpi-range">{avg_range:.2f}%</div><div class="kpi-note">Typical high-to-low movement</div></article><article class="kpi"><div class="kpi-label">Estimated turnover</div><div class="kpi-value" id="kpi-turnover">{total_turnover:,.0f}</div><div class="kpi-note">Through {latest_date}</div></article></section>
            <section class="panel"><div class="panel-head"><h2>Price trajectory</h2><span class="panel-note">Hover for OHLCV context; use the filter to isolate a series</span></div><div id="price-chart" class="chart">{chart_html(price_chart, 'price-chart', True)}</div></section>
            <section class="grid-2"><div class="panel"><div class="panel-head"><h2>Total return by instrument</h2><span class="panel-note">Compare the full observation window</span></div><div id="performance-chart" class="chart">{chart_html(performance_chart, 'performance-chart')}</div></div><div class="panel"><div class="panel-head"><h2>Risk and movement</h2><span class="panel-note">Average daily high-to-low range</span></div><div id="range-chart" class="chart">{chart_html(range_chart, 'range-chart')}</div></div></section>
            <section class="panel"><div class="panel-head"><h2>Trading activity</h2><span class="panel-note">Estimated turnover = close × volume; missing volume is excluded</span></div><div id="turnover-chart" class="chart">{chart_html(turnover_chart, 'turnover-chart')}</div></section>
            <section class="panel"><div class="panel-head"><h2>What stands out</h2><span class="panel-note">Auto-updated from the selected data slice</span></div><div class="insight" id="insights"><div class="insight-item"><strong>Leading instrument</strong><span>{best_row['symbol']} leads at {best_row['cumulative_return_pct']:.2f}% cumulative return.</span></div><div class="insight-item"><strong>Movement profile</strong><span>Average daily range across the universe is {avg_range:.2f}%.</span></div><div class="insight-item"><strong>Coverage window</strong><span>{len(metrics)} cleaned observations from {start_date} through {end_date}.</span></div></div></section>
            <div class="footer">Source: cleaned candles in DuckDB fact_candles · Generated {latest_date} · Hover charts for row-level context</div>
        </main>
        <script>
            const reportData = {json.dumps(report_data, default=str)};
            const initialMetrics = reportData.metrics;
            const initialSummary = reportData.summary;
            const money = value => Number.isFinite(value) ? Math.round(value).toLocaleString() : "—";
            const pct = value => Number.isFinite(value) ? `${{value.toFixed(2)}}%` : "—";
            function updateDashboard() {{
                const symbol = document.getElementById("symbol-filter").value;
                const windowSize = document.getElementById("window-filter").value;
                const filtered = initialMetrics.filter(row => symbol === "ALL" || row.symbol === symbol).slice(windowSize === "all" ? 0 : -Number(windowSize));
                const activeSymbols = [...new Set(filtered.map(row => row.symbol))];
                const summary = initialSummary.filter(row => activeSymbols.includes(row.symbol));
                const best = summary.length ? summary.reduce((a, b) => a.cumulative_return_pct > b.cumulative_return_pct ? a : b) : null;
                const chartLayout = {{ template: "plotly_white", paper_bgcolor: "rgba(0,0,0,0)", plot_bgcolor: "rgba(0,0,0,0)", margin: {{l: 56, r: 24, t: 24, b: 48}}, xaxis: {{showgrid: false, linecolor: "#d9e3ec"}}, yaxis: {{gridcolor: "#e8eef3", zeroline: false}}, legend: {{orientation: "h", y: 1.08, x: 0}} }};
                Plotly.react("price-chart", activeSymbols.map(name => {{ const rows = filtered.filter(row => row.symbol === name); return {{x: rows.map(row => row.trade_date), y: rows.map(row => row.adjclose), name, type: "scatter", mode: "lines+markers", line: {{width: 2.5}}, hovertemplate: "%{{x}}<br>Adjusted close: %{{y:,.2f}}<extra>" + name + "</extra>"}}; }}), chartLayout, {{responsive: true, displaylogo: false}});
                Plotly.react("performance-chart", [{{x: summary.map(row => row.symbol), y: summary.map(row => row.cumulative_return_pct), type: "bar", marker: {{color: "#3977a9"}}, hovertemplate: "<b>%{{x}}</b><br>Cumulative return: %{{y:.2f}}%<extra></extra>"}}], {{...chartLayout, yaxis: {{...chartLayout.yaxis, title: "Cumulative return (%)"}}}}, {{responsive: true, displaylogo: false}});
                Plotly.react("range-chart", [{{x: summary.map(row => row.symbol), y: summary.map(row => row.average_daily_range_pct), type: "bar", marker: {{color: "#e8b25b"}}, hovertemplate: "<b>%{{x}}</b><br>Average daily range: %{{y:.2f}}%<extra></extra>"}}], {{...chartLayout, yaxis: {{...chartLayout.yaxis, title: "Average daily range (%)"}}}}, {{responsive: true, displaylogo: false}});
                Plotly.react("turnover-chart", [{{x: summary.map(row => row.symbol), y: summary.map(row => row.average_estimated_turnover), type: "bar", marker: {{color: "#2d9b83"}}, hovertemplate: "<b>%{{x}}</b><br>Average turnover: %{{y:,.0f}}<extra></extra>"}}], {{...chartLayout, yaxis: {{...chartLayout.yaxis, title: "Estimated turnover"}}}}, {{responsive: true, displaylogo: false}});
                document.getElementById("kpi-symbols").textContent = activeSymbols.length;
                document.getElementById("kpi-return").textContent = best ? pct(best.cumulative_return_pct) : "—";
                document.getElementById("kpi-return-note").textContent = best ? `${{best.symbol}} in selected window` : "No data in selection";
                document.getElementById("kpi-range").textContent = summary.length ? pct(summary.reduce((sum, row) => sum + row.average_daily_range_pct, 0) / summary.length) : "—";
                document.getElementById("kpi-turnover").textContent = money(filtered.reduce((sum, row) => sum + (Number(row.estimated_turnover) || 0), 0));
                document.getElementById("insights").innerHTML = best ? `<div class="insight-item"><strong>Leading instrument</strong><span>${{best.symbol}} leads at ${{pct(best.cumulative_return_pct)}} cumulative return.</span></div><div class="insight-item"><strong>Movement profile</strong><span>${{best.symbol}} averages ${{pct(best.average_daily_range_pct)}} daily range.</span></div><div class="insight-item"><strong>Selected window</strong><span>${{filtered.length}} cleaned observations are in focus.</span></div>` : "";
            }}
            document.getElementById("symbol-filter").addEventListener("change", updateDashboard);
            document.getElementById("window-filter").addEventListener("change", updateDashboard);
        </script>
    </body>
    </html>
    """

    output.write_text(html, encoding="utf-8")

    return output