# Zkuba Trading Platform

A working trading platform built one component per sprint. Customers hold accounts, place buy and sell orders against real instruments, and see their positions and cash move.

## Project Structure

This is a multi-sprint capstone project with the following sprint folders:

| Sprint | Component              | Folder                     |
| ------ | ---------------------- | -------------------------- |
| 3      | Trade database         | `sprint-03-trade-database` |
| 4      | Analytics ETL pipeline | `sprint-04-analytics-etl`  |
| 5      | Domain engine          | `sprint-05-domain-engine`  |
| 6      | Trade API              | `sprint-06-trade-api`      |

Each sprint includes a README with component details, deliverables, and acceptance criteria. Work for that sprint goes in the corresponding folder.

## Repository Organization

```
contracts/    Binding interface definitions. See contracts/README.md.
infra/        Infrastructure requirements. See infra/README.md.
.env.example  Environment template. Copy to .env before running.
```

Each sprint folder contains its own README with component reasoning, deliverables, and acceptance criteria.

## Market data: the Fauxnance API

Prices come from Fauxnance, a REST API provided for the programme. You will
receive a personal key. Send it in the `X-Api-Key` header.

| Endpoint                    | Use                                                                             |
| --------------------------- | ------------------------------------------------------------------------------- |
| `GET /candles/{symbol}`     | End-of-day OHLCV history. Sprint 4 analytics, Sprint 10 signals.                |
| `GET /quotes/{symbol}`      | One delayed quote. The Trade Executor prices a fill with this.                  |
| `GET /quotes?symbols=A,B,C` | Batch quotes, up to 25 symbols, counting as one request.                        |
| `GET /usage`                | Your own quota status. Check it before assuming a 429 is somebody else's fault. |
| `GET /health`               | No key required. Rules out "is it the API or is it me".                         |

The base URL is in `.env.example` and Swagger UI is at `/v1/docs` on that
host.

Three things about it shape your design.

The quota is 2000 requests per day per key. That is a real constraint and it
is meant to be. A poller that fetches every symbol every second exhausts a key
before lunch. Poll only the symbols someone holds or is watching, batch up to
25 per call, and cache what you have already asked for.

The key lives in an environment variable and is read from there at runtime. It
is never committed, never hard-coded, and never reaches the browser: the
Angular application does not call Fauxnance, because anything the browser
sends is visible to whoever is using it. If a key does reach a commit, say so
immediately and have it rotated. A quiet fix leaves it in the history.

There is no price stream. Fauxnance answers requests; it does not push. The
real-time behaviour in this platform is something you build in Sprint 7, with
a poller inside the Trade Executor that calls the batch endpoint on an interval
and publishes each quote onto `market-data`. Everything downstream, including
the watchlist alerts in Sprint 10, consumes what that poller produces.

Fauxnance covers NSE and BSE symbols alongside US equities, FX and crypto, so
`INFY.NS`, `RELIANCE.NS` and `TATASTEEL.BO` all price. Trade instruments you
recognise. A dashboard is easier to sanity-check when you already know roughly
what the numbers should look like.

## Env Handling

Keep secrets out of the repository. `.env` is git-ignored and stays that way.
Commit `.env.example` with placeholders when you add a variable, so the next
person knows what to set.
