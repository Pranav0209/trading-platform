# Sprint 4 Incremental Build Guide

This file explains how to use the Sprint 4 analytics ETL as a starting point and extend it one step at a time.

## What is already in place

The sprint folder is now an installable Python package with:

- a `pyproject.toml` for editable installs
- a `dev` optional dependency group that includes pytest
- three pipeline functions split across three modules
- a console entry point called `fauxnance-etl`
- pytest coverage for the transform step, including a malformed-input case

## How to set it up

From the repository root:

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -e 'sprint-04-analytics-etl[dev]'
```

Put your local Fauxnance settings in the repository root `.env` file. The extractor reads:

- `FAUXNANCE_BASE_URL`
- `FAUXNANCE_API_KEY`

## How to run it

The pipeline can be run without reading the source by using the console script:

```bash
fauxnance-etl --symbols INFY.NS RELIANCE.NS
```

You can also run the module directly if you prefer:

```bash
python -m sprint_04_analytics_etl
```

By default, the pipeline:

- extracts raw candle data for the requested symbols
- caches the raw payloads under `.cache/`
- transforms the payload into a clean table-like frame
- loads the result into `analytics.duckdb`

## Current entry point

The team should run the pipeline through the `fauxnance-etl` console script declared in `pyproject.toml`.