# Sprint 4 Incremental Build Guide

This is the plain-English guide for the Sprint 4 analytics work. It explains what we have already built, what each piece does, and how the team can add the next piece without breaking the earlier ones.

## What we have built so far

We completed these stories in order:

1. Set up the sprint as a Python package so it can be installed from the repository root.
2. Chose a small Indian stock universe: `INFY.NS`, `RELIANCE.NS`, and `TATASTEEL.BO`.
3. Split the pipeline into three jobs: get the raw data, clean it, and save it.
4. Made the extractor read the API key from `.env` only and cache the raw response on disk.
5. Added checks for the API health and quota so we can tell the difference between a broken request and a quota problem.
6. Wrote tests for the cleaner and the raw-data cache.
7. Added a simple command a teammate can run without reading the source.

## What each part does

- **Extract** fetches the raw candle data from Fauxnance and hands it on unchanged.
- **Transform** cleans the data, fixes types, removes bad rows, and prepares it for storage.
- **Load** writes the cleaned data into DuckDB, which is the analytics database used for this sprint.
- **CLI / entry point** is the small wrapper that connects the three steps together.

Think of it like a kitchen line:

- Extract is the person bringing in the ingredients.
- Transform is the prep table where the ingredients are washed and cut.
- Load is the oven or storage shelf where the finished dish goes.
- The entry point is the manager that tells each station when to work.

## How to set it up

From the repository root:

```powershell
py -m venv .venv
.venv\Scripts\python.exe -m pip install -e '.\sprint-04-analytics-etl[dev]'
```

If you already created `.venv` with an older Python version, remove it and create it again with
Python 3.11 before running the install.

Put your local Fauxnance settings in the repository root `.env` file. The extractor reads only these values:

- `FAUXNANCE_BASE_URL`
- `FAUXNANCE_API_KEY`

## How to run it

Use the console script when you want to run the whole pipeline:

```powershell
.venv\Scripts\fauxnance-etl.exe --symbols INFY.NS RELIANCE.NS
```

You can also run the package directly if you prefer:

```powershell
.venv\Scripts\python.exe -m sprint_04_analytics_etl
```

What happens when you run it:

1. The pipeline asks Fauxnance for raw candle data.
2. It saves that raw response in `.cache/` so the same request does not cost quota again.
3. It cleans the data.
4. It writes the cleaned result into `analytics.duckdb`.

If the API seems unhealthy, check `GET /health` first because it needs no key. If that looks fine, check `GET /usage` before assuming the API is broken.

## How to run the tests

Run the complete test suite from the repository root with the Python executable
inside the virtual environment:

```powershell
.venv\Scripts\python.exe -m pytest sprint-04-analytics-etl
```

The suite currently contains five tests. A successful run ends with output
similar to this:

```text
collected 5 items
============================== 5 passed ==============================
```

To run one test module at a time:

```powershell
.venv\Scripts\python.exe -m pytest sprint-04-analytics-etl\tests\test_extract.py
.venv\Scripts\python.exe -m pytest sprint-04-analytics-etl\tests\test_load.py
.venv\Scripts\python.exe -m pytest sprint-04-analytics-etl\tests\test_transform.py
```

The tests check these behaviors:

- `test_extract.py` uses a fake HTTP session, so it makes no network request.
	It verifies that the API key comes from `FAUXNANCE_API_KEY`, the date range
	is sent to Fauxnance, the raw response is cached using the symbol and range,
	and a second request reads that cache instead of calling the service again.
	It also verifies that usage checks fail clearly when the environment key is
	missing.
- `test_load.py` loads one candle into a temporary DuckDB file and verifies
	that the file is created, the returned path is correct, and the
	`fact_candles` table contains one row.
- `test_transform.py` verifies that valid fixture rows are retained, dates are
	converted to pandas datetimes, a missing volume remains null, and the
	synthetic flag is preserved. Its malformed-input test verifies that a candle
	with a high price below its low price raises `ValueError`.

The tests use temporary directories and fixture data, so they do not change the
real `.cache/` directory or `analytics.duckdb`, and they do not require an API
key or a network connection.

## Why the cache matters

The cache stores the original raw response, not the cleaned table. That matters because the cleaning rules may change often while the source data stays the same. If we change the cleaner, we do not want to spend another API request just to test the new cleaning rule.

The cache key uses both the symbol and the date range, so `INFY.NS` for July is stored separately from `INFY.NS` for another month.

## Current entry point

The team should run the pipeline through the `fauxnance-etl` console script declared in `pyproject.toml`. That script just wires the steps together; it does not do the real work itself.

## Stories to build next

When the team adds the next stories, the safest order is:

1. Add more data-cleaning rules in `transform`.
2. Add more tests for bad rows.
3. Add chart generation once the cleaned data is stable.
4. Fill in the claims only after the charts and numbers are believable.
