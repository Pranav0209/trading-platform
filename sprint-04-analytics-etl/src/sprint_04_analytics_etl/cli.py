from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd
from dotenv import load_dotenv

from .extract import extract_candles
from .load import load_candles
from .transform import transform_candles


DEFAULT_SYMBOLS = ["INFY.NS", "RELIANCE.NS"]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run the Sprint 4 Fauxnance ETL pipeline")
    parser.add_argument("--symbols", nargs="+", default=DEFAULT_SYMBOLS)
    parser.add_argument("--from-date", default=None)
    parser.add_argument("--to-date", default=None)
    parser.add_argument("--database", default="analytics.duckdb")
    parser.add_argument("--cache-dir", default=".cache")
    parser.add_argument("--base-url", default=None)
    parser.add_argument("--api-key", default=None)
    return parser


def main() -> None:
    load_dotenv()
    args = build_parser().parse_args()

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
        frames.append(transform_candles(raw))

    combined = pd.concat(frames, ignore_index=True) if frames else pd.DataFrame()
    load_candles(combined, database_path=args.database)


if __name__ == "__main__":
    main()