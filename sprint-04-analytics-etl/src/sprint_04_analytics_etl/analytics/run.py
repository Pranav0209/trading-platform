from __future__ import annotations

import argparse
from pathlib import Path

from .analytics import (
    calculate_daily_metrics,
    calculate_symbol_summary,
    load_candles,
)
from .dashboard import build_dashboard


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run analytics against the loaded DuckDB fact_candles table"
    )
    parser.add_argument(
        "--database",
        default="analytics.duckdb",
        help="Path to the DuckDB database containing fact_candles",
    )
    parser.add_argument(
        "--output",
        default="artefacts/report.html",
        help="Where to write the generated HTML dashboard",
    )
    args = parser.parse_args()

    frame = load_candles(database_path=args.database)

    if frame.empty:
        print(f"No data found in {args.database}. Run the ETL first.")
        return

    metrics = calculate_daily_metrics(frame)
    summary = calculate_symbol_summary(frame)

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)

    build_dashboard(metrics, summary, output)

    print(f"Analytics output written to: {output}")
    print("\nDaily metrics preview:")
    print(metrics.head().to_string(index=False))
    print("\nSymbol summary:")
    print(summary.to_string(index=False))


if __name__ == "__main__":
    main()
