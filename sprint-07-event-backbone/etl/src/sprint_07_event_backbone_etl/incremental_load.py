"""Incremental load pipeline: Postgres → DuckDB star schema."""

from __future__ import annotations

import json
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

import psycopg2
import psycopg2.extras
import duckdb

from .warehouse import (
    initialize_warehouse,
    get_last_watermark,
    record_watermark,
    dead_letter,
)
from .validate import (
    ValidationError,
    validate_order_data,
    validate_referential_integrity,
    validate_null_and_type_mismatches,
)


class IncrementalTradeLoader:
    """Loads trade data from Postgres to DuckDB incrementally with watermarking.
    
    Ensures:
    - Incremental loads via watermark on order.created_at
    - Idempotency via merge on (order_id)
    - Dead-lettering of failed rows with reason and batch ID
    - All validation checks before reaching fact table
    - Proper dimensional loading order
    """

    def __init__(
        self,
        postgres_conn_string: str,
        warehouse_path: str | Path = "analytics.duckdb",
        batch_id: str | None = None,
    ):
        """Initialize the loader.
        
        Args:
            postgres_conn_string: PostgreSQL connection string
            warehouse_path: Path to DuckDB database
            batch_id: Unique batch identifier (generated if not provided)
        """
        self.postgres_conn_string = postgres_conn_string
        self.warehouse_path = Path(warehouse_path)
        self.batch_id = batch_id or str(uuid.uuid4())
        
        self.stats = {
            'dim_date_loaded': 0,
            'dim_instrument_loaded': 0,
            'dim_account_loaded': 0,
            'fact_trades_loaded': 0,
            'fact_trades_skipped': 0,
            'dead_lettered': 0,
        }

    def load_dimensions_and_facts(self) -> dict[str, Any]:
        """Execute the complete incremental load pipeline.
        
        Runs in order:
        1. Load DIM_INSTRUMENT
        2. Load DIM_ACCOUNT
        3. Load FACT_TRADES (incremental)
        
        Returns:
            Dictionary with load statistics and summary
        """
        # Initialize warehouse schema if needed
        initialize_warehouse(self.warehouse_path)
        
        print(f"[{self.batch_id}] Starting incremental load pipeline...")
        
        try:
            self._load_instruments()
            self._load_accounts()
            self._load_trades_incremental()
            
            # Record watermark for next run
            last_watermark = self._get_max_trade_timestamp()
            if last_watermark:
                record_watermark(
                    'trades_incremental',
                    last_watermark,
                    self.batch_id,
                    self.stats['fact_trades_loaded'],
                    self.stats['dead_lettered'],
                    self.warehouse_path,
                )
            
            return {
                'status': 'success',
                'batch_id': self.batch_id,
                'stats': self.stats,
            }
        
        except Exception as e:
            print(f"[{self.batch_id}] Load failed: {e}")
            return {
                'status': 'error',
                'batch_id': self.batch_id,
                'error': str(e),
                'stats': self.stats,
            }

    def _load_instruments(self) -> None:
        """Load DIM_INSTRUMENT from Postgres instruments table."""
        print(f"[{self.batch_id}] Loading dim_instrument...")
        
        with psycopg2.connect(self.postgres_conn_string) as pg_conn:
            with pg_conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cursor:
                cursor.execute("""
                    SELECT 
                        symbol,
                        display_name,
                        asset_class,
                        currency,
                        tradable
                    FROM instruments
                    ORDER BY symbol
                """)
                
                instruments = cursor.fetchall()
        
        if not instruments:
            print(f"[{self.batch_id}] No instruments to load")
            return
        
        with duckdb.connect(str(self.warehouse_path)) as dw_conn:
            # Clear existing instruments
            dw_conn.execute("DELETE FROM dim_instrument")
            
            # Insert each instrument
            now = datetime.now()
            for row in instruments:
                dw_conn.execute("""
                    INSERT INTO dim_instrument 
                    (symbol, display_name, asset_class, currency, tradable, source_loaded_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                """, [row['symbol'], row['display_name'], row['asset_class'], 
                       row['currency'], row['tradable'], now])
            
            dw_conn.commit()
            self.stats['dim_instrument_loaded'] = len(instruments)
        
        print(f"[{self.batch_id}] Loaded {len(instruments)} instruments")

    def _load_accounts(self) -> None:
        """Load DIM_ACCOUNT from Postgres accounts table."""
        print(f"[{self.batch_id}] Loading dim_account...")
        
        with psycopg2.connect(self.postgres_conn_string) as pg_conn:
            with pg_conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cursor:
                cursor.execute("""
                    SELECT 
                        id,
                        account_number,
                        currency,
                        status
                    FROM accounts
                    ORDER BY id
                """)
                
                accounts = cursor.fetchall()
        
        if not accounts:
            print(f"[{self.batch_id}] No accounts to load")
            return
        
        with duckdb.connect(str(self.warehouse_path)) as dw_conn:
            # Clear existing accounts
            dw_conn.execute("DELETE FROM dim_account")
            
            # Insert each account
            now = datetime.now()
            for row in accounts:
                dw_conn.execute("""
                    INSERT INTO dim_account 
                    (account_id, account_number, currency, account_status, source_loaded_at)
                    VALUES (?, ?, ?, ?, ?)
                """, [row['id'], row['account_number'], row['currency'], 
                       row['status'], now])
            
            dw_conn.commit()
            self.stats['dim_account_loaded'] = len(accounts)
        
        print(f"[{self.batch_id}] Loaded {len(accounts)} accounts")

    def _load_trades_incremental(self) -> None:
        """Load FACT_TRADES incrementally from Postgres.
        
        Fetches all orders (including rejected/cancelled) that are newer than
        the last watermark, validates each row, and either loads or dead-letters.
        """
        print(f"[{self.batch_id}] Loading fact_trades (incremental)...")
        
        # Get the watermark for incremental load
        last_watermark = get_last_watermark(self.warehouse_path)
        
        # Fetch orders from Postgres
        orders = self._fetch_orders_from_postgres(last_watermark)
        
        if not orders:
            print(f"[{self.batch_id}] No new orders to load (watermark={last_watermark})")
            return
        
        print(f"[{self.batch_id}] Fetched {len(orders)} new orders")
        
        # Build lookups for dimension validation
        existing_symbols = self._get_existing_symbols()
        existing_accounts = self._get_existing_accounts()
        
        # Process each order
        validated_trades = []
        
        for order in orders:
            try:
                # Validate null/types
                validate_null_and_type_mismatches(order)
                
                # Validate domain constraints
                validated = validate_order_data(
                    order_id=order['id'],
                    account_id=order['account_id'],
                    symbol=order['symbol'],
                    side=order['side'],
                    quantity=order['quantity'],
                    executed_price=order.get('executed_price'),
                    status=order['status'],
                    created_at=order['created_at'],
                    executed_on=order.get('executed_on'),
                )
                
                # Validate referential integrity
                validate_referential_integrity(
                    order['symbol'],
                    order['account_id'],
                    existing_accounts,
                    existing_symbols,
                )
                
                validated_trades.append((validated, order))
                
            except ValidationError as e:
                # Dead-letter the failed row
                self._dead_letter_row(order, e)
                self.stats['dead_lettered'] += 1
        
        # Upsert validated trades into fact_trades
        if validated_trades:
            self._upsert_trades(validated_trades)
        
        print(f"[{self.batch_id}] Loaded {self.stats['fact_trades_loaded']} trades, "
              f"dead-lettered {self.stats['dead_lettered']}")

    def _fetch_orders_from_postgres(self, since: Optional[datetime]) -> list[dict]:
        """Fetch orders from Postgres created since watermark.
        
        Loads all orders including rejected and cancelled, since fill rate
        cannot be computed from fills alone.
        """
        with psycopg2.connect(self.postgres_conn_string) as pg_conn:
            with pg_conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cursor:
                if since:
                    cursor.execute("""
                        SELECT 
                            id,
                            account_id,
                            symbol,
                            side,
                            quantity,
                            executed_price,
                            status,
                            created_at
                        FROM orders
                        WHERE created_at > %s
                        ORDER BY created_at ASC
                    """, (since,))
                else:
                    cursor.execute("""
                        SELECT 
                            id,
                            account_id,
                            symbol,
                            side,
                            quantity,
                            executed_price,
                            status,
                            created_at
                        FROM orders
                        ORDER BY created_at ASC
                    """)
                
                return cursor.fetchall()

    def _get_existing_symbols(self) -> set[str]:
        """Get set of existing instrument symbols from warehouse."""
        with duckdb.connect(str(self.warehouse_path)) as dw_conn:
            result = dw_conn.execute(
                "SELECT symbol FROM dim_instrument"
            ).fetchall()
            return {row[0] for row in result}

    def _get_existing_accounts(self) -> set[int]:
        """Get set of existing account IDs from warehouse."""
        with duckdb.connect(str(self.warehouse_path)) as dw_conn:
            result = dw_conn.execute(
                "SELECT account_id FROM dim_account"
            ).fetchall()
            return {row[0] for row in result}

    def _dead_letter_row(self, order: dict, error: ValidationError) -> None:
        """Record a failed row to the dead letter queue."""
        dead_letter(
            load_batch_id=self.batch_id,
            error_reason=error.reason,
            error_detail=error.detail,
            order_id=str(order.get('id')),
            account_id=order.get('account_id'),
            symbol=order.get('symbol'),
            row_data=dict(order),
            database_path=self.warehouse_path,
        )

    def _upsert_trades(self, validated_trades: list[tuple]) -> None:
        """Upsert trades into fact_trades using merge on order_id.
        
        Merge logic ensures idempotency: re-running doesn't double-count.
        """
        with duckdb.connect(str(self.warehouse_path)) as dw_conn:
            now = datetime.now()
            
            for validated, original_order in validated_trades:
                # Look up foreign keys
                instrument_key = dw_conn.execute(
                    "SELECT instrument_key FROM dim_instrument WHERE symbol = ?",
                    [validated['symbol']]
                ).fetchone()[0]
                
                account_key = dw_conn.execute(
                    "SELECT account_key FROM dim_account WHERE account_id = ?",
                    [validated['account_id']]
                ).fetchone()[0]
                
                # Extract date from created_at
                date_key = validated['created_at'].date()
                
                # Upsert: merge on order_id to ensure idempotency
                try:
                    dw_conn.execute("""
                        INSERT INTO fact_trades 
                        (order_id, account_key, instrument_key, date_key, 
                         side, order_status, quantity, executed_price, trade_value,
                         created_at, executed_on, loaded_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(order_id) DO UPDATE SET
                            order_status = EXCLUDED.order_status,
                            executed_price = EXCLUDED.executed_price,
                            trade_value = EXCLUDED.trade_value,
                            executed_on = EXCLUDED.executed_on,
                            loaded_at = ?
                    """, [
                        validated['order_id'],
                        account_key,
                        instrument_key,
                        date_key,
                        validated['side'],
                        validated['status'],
                        validated['quantity'],
                        validated['executed_price'],
                        validated['trade_value'],
                        validated['created_at'],
                        validated['executed_on'],
                        now,
                        now,
                    ])
                    
                    self.stats['fact_trades_loaded'] += 1
                
                except Exception as e:
                    # If upsert fails, dead-letter the row
                    error = ValidationError('upsert_failed', str(e))
                    self._dead_letter_row(original_order, error)
                    self.stats['dead_lettered'] += 1
            
            dw_conn.commit()

    def _get_max_trade_timestamp(self) -> Optional[datetime]:
        """Get the maximum created_at timestamp from fact_trades."""
        with duckdb.connect(str(self.warehouse_path)) as dw_conn:
            result = dw_conn.execute(
                "SELECT MAX(created_at) FROM fact_trades"
            ).fetchone()
            return result[0] if result and result[0] else None
