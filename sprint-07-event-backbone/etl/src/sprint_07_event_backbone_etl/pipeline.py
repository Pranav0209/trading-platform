"""ETL Pipeline orchestrator: connects Extract, Transform, and Load layers with watermark tracking."""

from __future__ import annotations

import uuid
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

from .extract import PostgresExtractor
from .transform import TradeTransformer, ValidationError
from .load import DuckDBLoader
from .warehouse import initialize_warehouse, get_last_watermark, record_watermark


class IncrementalTradePipeline:
    """Orchestrates the incremental ETL pipeline: Postgres -> Transform -> DuckDB."""

    def __init__(
        self,
        postgres_conn_string: str,
        warehouse_path: str | Path = "analytics.duckdb",
        batch_id: str | None = None,
    ):
        self.postgres_conn_string = postgres_conn_string
        self.warehouse_path = Path(warehouse_path)
        self.batch_id = batch_id or str(uuid.uuid4())

        self.extractor = PostgresExtractor(postgres_conn_string)
        self.loader = DuckDBLoader(self.warehouse_path)

        self.stats = {
            "dim_instrument_loaded": 0,
            "dim_instrument_updated": 0,
            "dim_account_loaded": 0,
            "dim_account_updated": 0,
            "fact_trades_loaded": 0,
            "fact_trades_updated": 0,
            "dead_lettered": 0,
        }

    def run_all(self) -> dict[str, Any]:
        """Execute the complete pipeline: dimensions followed by incremental trades."""
        initialize_warehouse(self.warehouse_path)
        print(f"[{self.batch_id}] Starting ETL pipeline run...")

        try:
            self.load_dimensions()
            self.load_trades_incremental()

            return {
                "status": "success",
                "batch_id": self.batch_id,
                "stats": self.stats,
            }
        except Exception as e:
            print(f"[{self.batch_id}] Pipeline run failed: {e}")
            return {
                "status": "error",
                "batch_id": self.batch_id,
                "error": str(e),
                "stats": self.stats,
            }

    def load_dimensions(self) -> dict[str, Any]:
        """Extract and load dimensions (instruments and accounts)."""
        initialize_warehouse(self.warehouse_path)
        print(f"[{self.batch_id}] Loading dimension tables...")

        # 1. Instruments
        raw_instruments = self.extractor.extract_instruments()
        inst_count = self.loader.load_instruments(raw_instruments)
        self.stats["dim_instrument_loaded"] = inst_count

        # 2. Accounts
        raw_accounts = self.extractor.extract_accounts()
        acc_count = self.loader.load_accounts(raw_accounts)
        self.stats["dim_account_loaded"] = acc_count

        print(f"[{self.batch_id}] Loaded {inst_count} instruments, {acc_count} accounts.")
        return {"status": "success", "batch_id": self.batch_id, "stats": self.stats}

    def load_dimensions_incremental(self) -> dict[str, Any]:
        """Extract and load only new/updated dimensions using watermarks."""
        initialize_warehouse(self.warehouse_path)
        print(f"[{self.batch_id}] Loading incremental dimensions...")

        # Get watermarks for instruments and accounts
        inst_watermark = get_last_watermark("dim_instrument_incremental", self.warehouse_path)
        acc_watermark = get_last_watermark("dim_account_incremental", self.warehouse_path)

        # 1. Incremental Instruments
        raw_instruments = self.extractor.extract_instruments_incremental(since=inst_watermark)
        if raw_instruments:
            inst_inserted, inst_updated = self.loader.load_instruments_incremental(raw_instruments)
            self.stats["dim_instrument_loaded"] = inst_inserted
            self.stats["dim_instrument_updated"] = inst_updated
            max_inst_updated_at = max(instr.get("updated_at") for instr in raw_instruments if instr.get("updated_at"))
            if max_inst_updated_at:
                record_watermark(
                    load_name="dim_instrument_incremental",
                    loaded_timestamp=max_inst_updated_at,
                    batch_id=self.batch_id,
                    row_count=inst_inserted + inst_updated,
                    dlq_count=0,
                    database_path=self.warehouse_path,
                )
            print(f"[{self.batch_id}] Instruments: inserted {inst_inserted}, updated {inst_updated}.")
        else:
            print(f"[{self.batch_id}] No new/updated instruments (watermark={inst_watermark}).")

        # 2. Incremental Accounts
        raw_accounts = self.extractor.extract_accounts_incremental(since=acc_watermark)
        if raw_accounts:
            acc_inserted, acc_updated = self.loader.load_accounts_incremental(raw_accounts)
            self.stats["dim_account_loaded"] = acc_inserted
            self.stats["dim_account_updated"] = acc_updated
            max_acc_updated_at = max(acct.get("updated_at") for acct in raw_accounts if acct.get("updated_at"))
            if max_acc_updated_at:
                record_watermark(
                    load_name="dim_account_incremental",
                    loaded_timestamp=max_acc_updated_at,
                    batch_id=self.batch_id,
                    row_count=acc_inserted + acc_updated,
                    dlq_count=0,
                    database_path=self.warehouse_path,
                )
            print(f"[{self.batch_id}] Accounts: inserted {acc_inserted}, updated {acc_updated}.")
        else:
            print(f"[{self.batch_id}] No new/updated accounts (watermark={acc_watermark}).")

        return {"status": "success", "batch_id": self.batch_id, "stats": self.stats}

    def load_trades_incremental(self) -> dict[str, Any]:
        """Incrementally extract and load orders into fact_trades using updated_at watermark.
        
        First loads any new/updated dimensions, then loads trades."""
        initialize_warehouse(self.warehouse_path)
        print(f"[{self.batch_id}] Loading incremental trades...")

        # Load incremental dimensions first
        self.load_dimensions_incremental()

        watermark = get_last_watermark("trades_incremental", self.warehouse_path)
        raw_orders = self.extractor.extract_orders(since=watermark)

        if not raw_orders:
            print(f"[{self.batch_id}] No new or modified orders found (watermark={watermark}).")
            return {"status": "success", "batch_id": self.batch_id, "stats": self.stats}

        print(f"[{self.batch_id}] Extracted {len(raw_orders)} orders since {watermark}.")

        # Retrieve dimension caches for referential checking
        symbols, accounts = self.loader.get_dimension_lookups()
        transformer = TradeTransformer(existing_symbols=symbols, existing_accounts=accounts)

        validated_trades = []
        max_updated_at = watermark

        for order in raw_orders:
            up_at = order.get("updated_at")
            if max_updated_at is None or (up_at and up_at > max_updated_at):
                max_updated_at = up_at

            try:
                transformed = transformer.transform_order(order)
                validated_trades.append(transformed)
            except ValidationError as err:
                self.loader.dead_letter_row(
                    batch_id=self.batch_id,
                    reason=err.reason,
                    detail=err.detail,
                    order=order,
                )
                self.stats["dead_lettered"] += 1

        # Upsert into DuckDB
        if validated_trades:
            inserted, updated = self.loader.upsert_trades(validated_trades)
            self.stats["fact_trades_loaded"] += inserted
            self.stats["fact_trades_updated"] += updated

        # Record updated watermark
        if max_updated_at is not None:
            record_watermark(
                load_name="trades_incremental",
                loaded_timestamp=max_updated_at,
                batch_id=self.batch_id,
                row_count=self.stats["fact_trades_loaded"] + self.stats["fact_trades_updated"],
                dlq_count=self.stats["dead_lettered"],
                database_path=self.warehouse_path,
            )

        print(f"[{self.batch_id}] Ingested {self.stats['fact_trades_loaded']} new, "
              f"updated {self.stats['fact_trades_updated']} existing, "
              f"dead-lettered {self.stats['dead_lettered']}.")
        return {"status": "success", "batch_id": self.batch_id, "stats": self.stats}


# Backward-compatible alias
IncrementalTradeLoader = IncrementalTradePipeline
