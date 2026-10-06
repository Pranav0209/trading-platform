"""Load layer: manages dimensional loading, fact table upserts, and dead-lettering in DuckDB."""

from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from typing import Any
import duckdb

from .warehouse import get_next_surrogate_key, dead_letter
from .transform import derive_exchange


class DuckDBLoader:
    """Manages all writes, upserts, and lookups against the DuckDB analytical warehouse."""

    def __init__(self, database_path: str | Path = "analytics.duckdb"):
        self.database_path = Path(database_path)

    def get_dimension_lookups(self) -> tuple[set[str], set[int]]:
        """Fetch active symbols and account source IDs for referential validation."""
        with duckdb.connect(str(self.database_path)) as conn:
            symbols = {
                row[0] for row in conn.execute("SELECT symbol FROM dim_instrument").fetchall()
            }
            accounts = {
                int(row[0]) for row in conn.execute(
                    "SELECT source_id FROM dim_account WHERE is_current = TRUE"
                ).fetchall()
            }
            return symbols, accounts

    def load_instruments(self, instruments: list[dict[str, Any]]) -> int:
        """Upsert instruments into DIM_INSTRUMENT (Type 1 SCD)."""
        if not instruments:
            return 0

        with duckdb.connect(str(self.database_path)) as conn:
            now = datetime.now()
            for row in instruments:
                symbol = row["symbol"]
                name = row["display_name"]
                asset_class = row["asset_class"]
                currency = row["currency"]
                tradable = bool(row["tradable"])
                exchange = derive_exchange(symbol)

                existing = conn.execute(
                    "SELECT instrument_key FROM dim_instrument WHERE symbol = ?",
                    [symbol],
                ).fetchone()

                if existing:
                    conn.execute("""
                        UPDATE dim_instrument
                        SET name = ?, asset_class = ?, currency = ?, exchange = ?, tradable = ?, loaded_at = ?
                        WHERE symbol = ?
                    """, [name, asset_class, currency, exchange, tradable, now, symbol])
                else:
                    new_key = get_next_surrogate_key(conn, "dim_instrument", "instrument_key")
                    conn.execute("""
                        INSERT INTO dim_instrument 
                        (instrument_key, symbol, name, asset_class, currency, exchange, tradable, loaded_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, [new_key, symbol, name, asset_class, currency, exchange, tradable, now])

            conn.commit()
            return len(instruments)

    def load_accounts(self, accounts: list[dict[str, Any]]) -> int:
        """Upsert accounts into DIM_ACCOUNT (Type 2 SCD)."""
        if not accounts:
            return 0

        with duckdb.connect(str(self.database_path)) as conn:
            now = datetime.now()
            today = now.date()

            for row in accounts:
                source_id = row["source_id"]
                account_id = row["account_id"]
                holder_name = row["holder_name"]
                status = row["status"]
                effective_date = row.get("effective_date") or today

                curr = conn.execute("""
                    SELECT account_key, status, holder_name
                    FROM dim_account
                    WHERE source_id = ? AND is_current = TRUE
                """, [source_id]).fetchone()

                if not curr:
                    new_key = get_next_surrogate_key(conn, "dim_account", "account_key")
                    conn.execute("""
                        INSERT INTO dim_account 
                        (account_key, account_id, holder_name, status, effective_date, end_date, is_current, source_id, loaded_at)
                        VALUES (?, ?, ?, ?, ?, NULL, TRUE, ?, ?)
                    """, [new_key, account_id, holder_name, status, effective_date, source_id, now])
                elif curr[1] != status:
                    # Expire older version
                    conn.execute("""
                        UPDATE dim_account
                        SET end_date = ?, is_current = FALSE
                        WHERE account_key = ?
                    """, [today, curr[0]])

                    new_key = get_next_surrogate_key(conn, "dim_account", "account_key")
                    conn.execute("""
                        INSERT INTO dim_account 
                        (account_key, account_id, holder_name, status, effective_date, end_date, is_current, source_id, loaded_at)
                        VALUES (?, ?, ?, ?, ?, NULL, TRUE, ?, ?)
                    """, [new_key, account_id, holder_name, status, today, source_id, now])
                else:
                    # Update metadata / holder name
                    conn.execute("""
                        UPDATE dim_account
                        SET holder_name = ?, loaded_at = ?
                        WHERE account_key = ?
                    """, [holder_name, now, curr[0]])

            conn.commit()
            return len(accounts)

    def load_instruments_incremental(self, instruments: list[dict[str, Any]]) -> tuple[int, int]:
        """Incrementally upsert instruments into DIM_INSTRUMENT (Type 1 SCD).
        
        Returns:
            (inserted_count, updated_count)
        """
        if not instruments:
            return 0, 0

        inserted = 0
        updated = 0

        with duckdb.connect(str(self.database_path)) as conn:
            now = datetime.now()
            for row in instruments:
                symbol = row["symbol"]
                name = row["display_name"]
                asset_class = row["asset_class"]
                currency = row["currency"]
                tradable = bool(row["tradable"])
                exchange = derive_exchange(symbol)

                existing = conn.execute(
                    "SELECT instrument_key FROM dim_instrument WHERE symbol = ?",
                    [symbol],
                ).fetchone()

                if existing:
                    conn.execute("""
                        UPDATE dim_instrument
                        SET name = ?, asset_class = ?, currency = ?, exchange = ?, tradable = ?, loaded_at = ?
                        WHERE symbol = ?
                    """, [name, asset_class, currency, exchange, tradable, now, symbol])
                    updated += 1
                else:
                    new_key = get_next_surrogate_key(conn, "dim_instrument", "instrument_key")
                    conn.execute("""
                        INSERT INTO dim_instrument 
                        (instrument_key, symbol, name, asset_class, currency, exchange, tradable, loaded_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, [new_key, symbol, name, asset_class, currency, exchange, tradable, now])
                    inserted += 1

            conn.commit()
            return inserted, updated

    def load_accounts_incremental(self, accounts: list[dict[str, Any]]) -> tuple[int, int]:
        """Incrementally upsert accounts into DIM_ACCOUNT (Type 2 SCD).
        
        Returns:
            (inserted_count, updated_count)
        """
        if not accounts:
            return 0, 0

        inserted = 0
        updated = 0

        with duckdb.connect(str(self.database_path)) as conn:
            now = datetime.now()
            today = now.date()

            for row in accounts:
                source_id = row["source_id"]
                account_id = row["account_id"]
                holder_name = row["holder_name"]
                status = row["status"]
                effective_date = row.get("effective_date") or today

                curr = conn.execute("""
                    SELECT account_key, status, holder_name
                    FROM dim_account
                    WHERE source_id = ? AND is_current = TRUE
                """, [source_id]).fetchone()

                if not curr:
                    new_key = get_next_surrogate_key(conn, "dim_account", "account_key")
                    conn.execute("""
                        INSERT INTO dim_account 
                        (account_key, account_id, holder_name, status, effective_date, end_date, is_current, source_id, loaded_at)
                        VALUES (?, ?, ?, ?, ?, NULL, TRUE, ?, ?)
                    """, [new_key, account_id, holder_name, status, effective_date, source_id, now])
                    inserted += 1
                elif curr[1] != status:
                    # Expire older version
                    conn.execute("""
                        UPDATE dim_account
                        SET end_date = ?, is_current = FALSE
                        WHERE account_key = ?
                    """, [today, curr[0]])

                    new_key = get_next_surrogate_key(conn, "dim_account", "account_key")
                    conn.execute("""
                        INSERT INTO dim_account 
                        (account_key, account_id, holder_name, status, effective_date, end_date, is_current, source_id, loaded_at)
                        VALUES (?, ?, ?, ?, ?, NULL, TRUE, ?, ?)
                    """, [new_key, account_id, holder_name, status, today, source_id, now])
                    updated += 1
                else:
                    # Update metadata / holder name
                    conn.execute("""
                        UPDATE dim_account
                        SET holder_name = ?, loaded_at = ?
                        WHERE account_key = ?
                    """, [holder_name, now, curr[0]])
                    updated += 1

            conn.commit()
            return inserted, updated

    def upsert_trades(self, validated_trades: list[dict[str, Any]]) -> tuple[int, int]:
        """Upsert validated trades into FACT_TRADES using natural key source_order_id.
        
        Returns:
            (inserted_count, updated_count)
        """
        if not validated_trades:
            return 0, 0

        inserted = 0
        updated = 0

        with duckdb.connect(str(self.database_path)) as conn:
            now = datetime.now()

            for trade in validated_trades:
                source_order_id = trade["source_order_id"]

                inst_res = conn.execute(
                    "SELECT instrument_key FROM dim_instrument WHERE symbol = ?",
                    [trade["symbol"]],
                ).fetchone()
                acc_res = conn.execute(
                    "SELECT account_key FROM dim_account WHERE source_id = ? AND is_current = TRUE",
                    [trade["account_id"]],
                ).fetchone()

                if not inst_res or not acc_res:
                    continue

                instrument_key = inst_res[0]
                account_key = acc_res[0]
                date_key = trade["date_key"]

                existing = conn.execute(
                    "SELECT trade_key FROM fact_trades WHERE source_order_id = ?",
                    [source_order_id],
                ).fetchone()

                if existing:
                    trade_key = existing[0]
                    conn.execute("""
                        UPDATE fact_trades
                        SET account_key = ?, instrument_key = ?, date_key = ?, side = ?, quantity = ?,
                            price = ?, status = ?, executed_price = ?, trade_value = ?, executed_on = ?, loaded_at = ?
                        WHERE trade_key = ?
                    """, [
                        account_key, instrument_key, date_key, trade["side"], trade["quantity"],
                        trade["price"], trade["status"], trade["executed_price"],
                        trade["trade_value"], trade["executed_on"], now, trade_key,
                    ])
                    updated += 1
                else:
                    trade_key = get_next_surrogate_key(conn, "fact_trades", "trade_key")
                    conn.execute("""
                        INSERT INTO fact_trades 
                        (trade_key, account_key, instrument_key, date_key, 
                         side, quantity, price, status, executed_price, trade_value,
                         source_order_id, created_at, executed_on, loaded_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, [
                        trade_key, account_key, instrument_key, date_key, trade["side"], trade["quantity"],
                        trade["price"], trade["status"], trade["executed_price"],
                        trade["trade_value"], source_order_id, trade["created_at"],
                        trade["executed_on"], now,
                    ])
                    inserted += 1

            conn.commit()
            return inserted, updated

    def dead_letter_row(
        self,
        batch_id: str,
        reason: str,
        detail: str,
        order: dict[str, Any],
    ) -> None:
        """Quarantine invalid row into dead_letter_queue."""
        dead_letter(
            load_batch_id=batch_id,
            error_reason=reason,
            error_detail=detail,
            order_id=str(order.get("id")),
            account_id=order.get("account_id"),
            symbol=order.get("symbol"),
            row_data=order,
            database_path=self.database_path,
        )
