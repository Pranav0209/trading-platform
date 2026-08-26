from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd
from dotenv import load_dotenv

from .analytics import analyse_candles
from .dashboard import build_dashboard
from .extract import extract_candles
from .load import load_candles
from .transform import transform_candles


DEFAULT_SYMBOLS = ["INFY.NS", "RELIANCE.NS"]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Run the Sprint 4 Fauxnance ETL and analytics pipeline"
    )
    parser.add_argument("--symbols", nargs="+", default=DEFAULT_SYMBOLS)
    parser.add_argument("--from-date", default=None)
    parser.add_argument("--to-date", default=None)
    parser.add_argument("--database", default="analytics.duckdb")
    parser.add_argument("--cache-dir", default=".cache")
    parser.add_argument("--base-url", default=None)
    parser.add_argument("--api-key", default=None)
    parser.add_argument(
        "--report",
        default="artefacts/report.html",
        help="Output path for the analytics dashboard",
    )
    return parser


def main() -> None:
    load_dotenv()
    args = build_parser().parse_args()

    # ---------------------------------------------------------
    # 1. EXTRACT
    # ---------------------------------------------------------
    frames: list[pd.DataFrame] = []

    for symbol in args.symbols:
        raw = extract_candles(
            symbol,
            from_date=args.from_date,
            to_date=args.to_date,
            api_key=args.api_key,
            base_url=args.base_url,
            cache_dir=Path(args.cache_dir),
        )

        # -----------------------------------------------------
        # 2. TRANSFORM
        # -----------------------------------------------------
        frame = transform_candles(raw)

        if not frame.empty:
            frames.append(frame)

    if not frames:
        raise RuntimeError("No valid candle data was produced")

    combined = pd.concat(frames, ignore_index=True)

    # ---------------------------------------------------------
    # 3. LOAD → DuckDB
    # ---------------------------------------------------------
    database_path = load_candles(
        combined,
        database_path=args.database,
    )

    # ---------------------------------------------------------
    # 4. ANALYZE ← NEW
    # ---------------------------------------------------------
    metrics, summary = analyse_candles(
        database_path=database_path,
    )

    # ---------------------------------------------------------
    # 5. BUILD DASHBOARD ← NEW
    # ---------------------------------------------------------
    report_path = build_dashboard(
        metrics,
        summary,
        output_path=args.report,
    )

    print(f"Loaded data into: {database_path}")
    print(f"Dashboard created: {report_path}")


if __name__ == "__main__":
    main()