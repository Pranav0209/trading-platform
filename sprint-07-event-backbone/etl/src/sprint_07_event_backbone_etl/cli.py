"""CLI for Sprint 7 incremental trade load pipeline."""

from __future__ import annotations

import argparse
import os
import sys
from dotenv import load_dotenv

from .pipeline import IncrementalTradePipeline
from .warehouse import initialize_warehouse
from .analytics import run_warehouse_analytics


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Sprint 7 event backbone incremental trade load pipeline"
    )
    subparsers = parser.add_subparsers(dest="command", help="Pipeline command")

    def add_common_args(p):
        p.add_argument(
            "--postgres-url",
            default=os.getenv("POSTGRES_URL", "postgresql://postgres:postgres@localhost:5432/trading"),
            help="PostgreSQL connection string",
        )
        p.add_argument(
            "--database",
            default="analytics.duckdb",
            help="DuckDB analytics database",
        )
        p.add_argument(
            "--batch-id",
            default=None,
            help="Unique batch identifier",
        )

    # init-warehouse
    init_parser = subparsers.add_parser("init-warehouse", help="Initialize DuckDB warehouse schema")
    init_parser.add_argument("--database", default="analytics.duckdb", help="DuckDB database path")

    # verify
    verify_parser = subparsers.add_parser("verify", help="Run analytical queries and schema integrity verification")
    verify_parser.add_argument("--database", default="analytics.duckdb", help="DuckDB database path")

    # load-dimensions, load-trades, load-all
    add_common_args(subparsers.add_parser("load-dimensions", help="Load dimension tables (instruments, accounts)"))
    add_common_args(subparsers.add_parser("load-trades", help="Load trades table incrementally"))
    add_common_args(subparsers.add_parser("load-all", help="Load dimensions and trades (full pipeline)"))

    return parser


def main() -> None:
    load_dotenv()
    args = build_parser().parse_args()

    if not args.command:
        print("Usage: trade-etl [init-warehouse | load-dimensions | load-trades | load-all | verify]")
        sys.exit(1)

    if args.command == "init-warehouse":
        initialize_warehouse(args.database)
        print(f"✓ Warehouse initialized successfully at {args.database}")
        return

    if args.command == "verify":
        run_warehouse_analytics(args.database)
        return

    pipeline = IncrementalTradePipeline(
        postgres_conn_string=args.postgres_url,
        warehouse_path=args.database,
        batch_id=args.batch_id,
    )

    if args.command == "load-dimensions":
        res = pipeline.load_dimensions()
    elif args.command == "load-trades":
        res = pipeline.load_trades_incremental()
    elif args.command == "load-all":
        res = pipeline.run_all()

    if res["status"] == "success":
        print(f"✓ Pipeline {args.command} completed: {res['stats']}")
    else:
        print(f"✗ Pipeline failed: {res.get('error')}")
        sys.exit(1)


if __name__ == "__main__":
    main()
