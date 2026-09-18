"""ETL Pipeline orchestrator: connects Extract, Transform, and Load layers with watermark tracking."""

from __future__ import annotations

import uuid
from pathlib import Path
from typing import Any

from .extract import PostgresExtractor
from .transform import TradeTransformer, ValidationError
from .load import DuckDBLoader
from .warehouse import (
    initialize_warehouse,
    get_last_watermark,
    record_watermark,
)


class IncrementalTradePipeline:
    """Orchestrates the incremental ETL pipeline: Postgres -> DuckDB."""

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
            "dim_account_loaded": 0,
            "dim_account_updated": 0,
            "fact_trades_loaded": 0,
            "fact_trades_updated": 0,
            "dead_lettered": 0,
        }

    def run_all(self) -> dict[str, Any]:
        """Execute the complete pipeline."""
        initialize_warehouse(self.warehouse_path)
        print(f"[{self.batch_id}] Starting ETL pipeline run...")

        try:
            # Full dimension load.
            # Instruments are loaded only here.
            self.load_dimensions()

            # Incremental accounts + incremental trades.
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
        """
        Load dimensions fully.

        Instruments:
            FULL LOAD every run.

        Accounts:
            FULL LOAD when this method is explicitly called.
        """
        initialize_warehouse(self.warehouse_path)
        print(f"[{self.batch_id}] Loading dimension tables...")

        # ============================================================
        # 1. INSTRUMENTS - FULL LOAD
        # ============================================================
        #
        # instruments has no updated_at column.
        # Therefore there is no instrument watermark and no
        # incremental instrument extraction.
        #
        raw_instruments = self.extractor.extract_instruments()

        inst_count = self.loader.load_instruments(raw_instruments)

        self.stats["dim_instrument_loaded"] = inst_count

        # ============================================================
        # 2. ACCOUNTS - FULL LOAD
        # ============================================================

        raw_accounts = self.extractor.extract_accounts()

        acc_count = self.loader.load_accounts(raw_accounts)

        self.stats["dim_account_loaded"] = acc_count

        print(
            f"[{self.batch_id}] Loaded "
            f"{inst_count} instruments, "
            f"{acc_count} accounts."
        )

        return {
            "status": "success",
            "batch_id": self.batch_id,
            "stats": self.stats,
        }

    def load_dimensions_incremental(self) -> dict[str, Any]:
        """
        Load only dimensions that support incremental loading.

        Instruments are intentionally NOT loaded here.

        Accounts:
            INCREMENTAL using dim_account_incremental watermark.
        """
        initialize_warehouse(self.warehouse_path)
        print(f"[{self.batch_id}] Loading incremental dimensions...")

        # ============================================================
        # ACCOUNTS - INCREMENTAL LOAD
        # ============================================================

        acc_watermark = get_last_watermark(
            "dim_account_incremental",
            self.warehouse_path,
        )

        raw_accounts = self.extractor.extract_accounts_incremental(
            since=acc_watermark
        )

        if raw_accounts:
            acc_inserted, acc_updated = (
                self.loader.load_accounts_incremental(
                    raw_accounts
                )
            )

            self.stats["dim_account_loaded"] = acc_inserted
            self.stats["dim_account_updated"] = acc_updated

            max_acc_updated_at = max(
                acct.get("updated_at")
                for acct in raw_accounts
                if acct.get("updated_at")
            )

            if max_acc_updated_at:
                record_watermark(
                    load_name="dim_account_incremental",
                    loaded_timestamp=max_acc_updated_at,
                    batch_id=self.batch_id,
                    row_count=acc_inserted + acc_updated,
                    dlq_count=0,
                    database_path=self.warehouse_path,
                )

            print(
                f"[{self.batch_id}] Accounts: "
                f"inserted {acc_inserted}, "
                f"updated {acc_updated}."
            )

        else:
            print(
                f"[{self.batch_id}] No new/updated accounts "
                f"(watermark={acc_watermark})."
            )

        return {
            "status": "success",
            "batch_id": self.batch_id,
            "stats": self.stats,
        }

    def load_trades_incremental(self) -> dict[str, Any]:
        """
        Incrementally extract and load orders into fact_trades.

        Accounts:
            Incrementally loaded first.

        Instruments:
            NOT loaded here. They are handled by load_dimensions().

        Trades:
            Incrementally loaded using trades_incremental watermark.
        """
        initialize_warehouse(self.warehouse_path)
        print(f"[{self.batch_id}] Loading incremental trades...")

        # ============================================================
        # 1. LOAD INCREMENTAL DIMENSIONS
        # ============================================================
        #
        # Only accounts are loaded here.
        # Instruments are handled by load_dimensions().
        #
        self.load_dimensions_incremental()

        # ============================================================
        # 2. GET TRADE WATERMARK
        # ============================================================

        watermark = get_last_watermark(
            "trades_incremental",
            self.warehouse_path,
        )

        # ============================================================
        # 3. EXTRACT ORDERS INCREMENTALLY
        # ============================================================

        raw_orders = self.extractor.extract_orders(
            since=watermark
        )

        if not raw_orders:
            print(
                f"[{self.batch_id}] No new or modified orders found "
                f"(watermark={watermark})."
            )

            return {
                "status": "success",
                "batch_id": self.batch_id,
                "stats": self.stats,
            }

        print(
            f"[{self.batch_id}] Extracted "
            f"{len(raw_orders)} orders since {watermark}."
        )

        # ============================================================
        # 4. GET DIMENSION LOOKUPS
        # ============================================================

        symbols, accounts = self.loader.get_dimension_lookups()

        transformer = TradeTransformer(
            existing_symbols=symbols,
            existing_accounts=accounts,
        )

        validated_trades = []

        max_updated_at = watermark

        # ============================================================
        # 5. TRANSFORM + VALIDATE ORDERS
        # ============================================================

        for order in raw_orders:
            up_at = order.get("updated_at")

            if (
                max_updated_at is None
                or (up_at and up_at > max_updated_at)
            ):
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

        # ============================================================
        # 6. UPSERT VALIDATED TRADES
        # ============================================================

        if validated_trades:
            inserted, updated = self.loader.upsert_trades(
                validated_trades
            )

            self.stats["fact_trades_loaded"] += inserted
            self.stats["fact_trades_updated"] += updated

        # ============================================================
        # 7. RECORD TRADE WATERMARK
        # ============================================================

        if max_updated_at is not None:
            record_watermark(
                load_name="trades_incremental",
                loaded_timestamp=max_updated_at,
                batch_id=self.batch_id,
                row_count=(
                    self.stats["fact_trades_loaded"]
                    + self.stats["fact_trades_updated"]
                ),
                dlq_count=self.stats["dead_lettered"],
                database_path=self.warehouse_path,
            )

        # ============================================================
        # 8. LOG RESULTS
        # ============================================================

        print(
            f"[{self.batch_id}] Ingested "
            f"{self.stats['fact_trades_loaded']} new, "
            f"updated {self.stats['fact_trades_updated']} existing, "
            f"dead-lettered {self.stats['dead_lettered']}."
        )

        return {
            "status": "success",
            "batch_id": self.batch_id,
            "stats": self.stats,
        }


# Backward-compatible alias
IncrementalTradeLoader = IncrementalTradePipeline