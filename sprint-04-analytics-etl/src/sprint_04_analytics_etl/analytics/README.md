# Analytics module

This folder is intentionally separate from the ETL pipeline.

Its job is to read the cleaned market data already stored in DuckDB and turn it into analytical summaries and charts.

## Responsibilities

- read `fact_candles` from the database
- calculate daily metrics
- calculate symbol-level summary metrics
- build a dashboard/report in HTML

## Inputs

The analytics module expects the ETL pipeline to have already populated the database table:

- `fact_candles`

The default database path is:

- `analytics.duckdb`

## Run

From the repository root, with the project installed in the active virtual environment:

```bash
python -m sprint_04_analytics_etl.analytics.run
```

Optional flags:

```bash
python -m sprint_04_analytics_etl.analytics.run --database analytics.duckdb --output artefacts/report.html
```

## Output

The module writes an HTML dashboard to the output path, defaulting to:

- `artefacts/report.html`

It also prints a short preview of:

- daily metrics
- symbol summary

## Files

- `analytics.py` — loads data from DuckDB and calculates metrics
- `charts.py` — Plotly chart builders
- `dashboard.py` — assembles the HTML dashboard
- `run.py` — command entry point for running analytics

## Design note

This folder stays separate from extract/transform/load. It consumes the trusted, cleaned analytical table and does not touch the raw API or ETL workflow.
