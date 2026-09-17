"""CLI for Sprint 7 incremental trade load pipeline."""

from __future__ import annotations

import argparse
import os
from pathlib import Path

from dotenv import load_dotenv

from .incremental_load import IncrementalTradeLoader


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Sprint 7 event backbone incremental trade load pipeline"
    )
    subparsers = parser.add_subparsers(dest="command", help="Pipeline command")
    
    # Load dimensions
    dimensions_parser = subparsers.add_parser(
        "load-dimensions",
        help="Load dimension tables (instruments, accounts)"
    )
    dimensions_parser.add_argument(
        "--postgres-url",
        required=True,
        help="PostgreSQL connection string"
    )
    dimensions_parser.add_argument(
        "--database",
        default="analytics.duckdb",
        help="DuckDB analytics database"
    )
    dimensions_parser.add_argument(
        "--batch-id",
        default=None,
        help="Unique batch identifier"
    )
    
    # Load trades
    trades_parser = subparsers.add_parser(
        "load-trades",
        help="Load trades table incrementally"
    )
    trades_parser.add_argument(
        "--postgres-url",
        required=True,
        help="PostgreSQL connection string"
    )
    trades_parser.add_argument(
        "--database",
        default="analytics.duckdb",
        help="DuckDB analytics database"
    )
    trades_parser.add_argument(
        "--batch-id",
        default=None,
        help="Unique batch identifier"
    )
    
    # Load all
    all_parser = subparsers.add_parser(
        "load-all",
        help="Load dimensions and trades (full pipeline)"
    )
    all_parser.add_argument(
        "--postgres-url",
        required=True,
        help="PostgreSQL connection string"
    )
    all_parser.add_argument(
        "--database",
        default="analytics.duckdb",
        help="DuckDB analytics database"
    )
    all_parser.add_argument(
        "--batch-id",
        default=None,
        help="Unique batch identifier"
    )
    
    return parser


def _load_dimensions_command(args) -> None:
    """Run only the dimension loading stage."""
    print(f"Loading dimensions from {args.postgres_url}...")
    
    loader = IncrementalTradeLoader(
        postgres_conn_string=args.postgres_url,
        warehouse_path=args.database,
        batch_id=args.batch_id,
    )
    
    # Manually load dimensions only
    loader._load_instruments()
    loader._load_accounts()
    
    print(f"✓ Loaded dimensions (instruments: {loader.stats['dim_instrument_loaded']}, "
          f"accounts: {loader.stats['dim_account_loaded']})")


def _load_trades_command(args) -> None:
    """Run only the trades loading stage (incremental)."""
    print(f"Loading trades from {args.postgres_url} (incremental)...")
    
    loader = IncrementalTradeLoader(
        postgres_conn_string=args.postgres_url,
        warehouse_path=args.database,
        batch_id=args.batch_id,
    )
    
    result = loader.load_dimensions_and_facts()
    
    if result['status'] == 'success':
        stats = result['stats']
        print(f"✓ Load completed (batch_id={result['batch_id']})")
        print(f"  - Instruments: {stats['dim_instrument_loaded']}")
        print(f"  - Accounts: {stats['dim_account_loaded']}")
        print(f"  - Trades loaded: {stats['fact_trades_loaded']}")
        print(f"  - Dead-lettered: {stats['dead_lettered']}")
    else:
        print(f"✗ Load failed: {result.get('error')}")
        exit(1)


def _load_all_command(args) -> None:
    """Run the complete incremental load pipeline."""
    print(f"Starting complete ETL pipeline...")
    print(f"  Source: {args.postgres_url}")
    print(f"  Target: {args.database}")
    
    loader = IncrementalTradeLoader(
        postgres_conn_string=args.postgres_url,
        warehouse_path=args.database,
        batch_id=args.batch_id,
    )
    
    result = loader.load_dimensions_and_facts()
    
    if result['status'] == 'success':
        stats = result['stats']
        print()
        print("=" * 60)
        print("✓ PIPELINE COMPLETED SUCCESSFULLY")
        print("=" * 60)
        print(f"Batch ID: {result['batch_id']}")
        print()
        print("Dimension tables:")
        print(f"  - Instruments: {stats['dim_instrument_loaded']}")
        print(f"  - Accounts: {stats['dim_account_loaded']}")
        print()
        print("Fact table:")
        print(f"  - Trades loaded: {stats['fact_trades_loaded']}")
        print(f"  - Trades dead-lettered: {stats['dead_lettered']}")
        print("=" * 60)
    else:
        print()
        print("✗ PIPELINE FAILED")
        print(f"Error: {result.get('error')}")
        exit(1)


def main() -> None:
    """Main entry point for the CLI."""
    load_dotenv()
    args = build_parser().parse_args()
    
    if not args.command:
        print("Please specify a command: load-dimensions, load-trades, or load-all")
        exit(1)
    
    if args.command == 'load-dimensions':
        _load_dimensions_command(args)
    elif args.command == 'load-trades':
        _load_trades_command(args)
    elif args.command == 'load-all':
        _load_all_command(args)
    else:
        print(f"Unknown command: {args.command}")
        exit(1)


if __name__ == "__main__":
    main()
