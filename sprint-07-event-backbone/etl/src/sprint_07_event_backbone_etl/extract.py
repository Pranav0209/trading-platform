"""Extract layer: connects to PostgreSQL and extracts raw operational records.

Extracts:
- Instruments (from instruments table)
- Accounts (joining accounts and users to capture account_number and holder_name)
- Orders (with incremental watermark filter on updated_at)
"""

from __future__ import annotations

from datetime import datetime
from typing import Any, Optional
import psycopg2
import psycopg2.extras


class PostgresExtractor:
    """Handles all data extraction from the PostgreSQL operational database."""

    def __init__(self, conn_string: str):
        self.conn_string = conn_string

    def extract_instruments(self) -> list[dict[str, Any]]:
        """Extract all tradable instruments."""
        with psycopg2.connect(self.conn_string) as conn:
            with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cursor:
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
                rows = cursor.fetchall()
                return [dict(row) for row in rows]

    # def extract_instruments_incremental(self, since: Optional[datetime] = None) -> list[dict[str, Any]]:
    #     """Extract instruments incrementally based on updated_at watermark."""
    #     with psycopg2.connect(self.conn_string) as conn:
    #         with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cursor:
    #             if since is not None:
    #                 query = """
    #                     SELECT 
    #                         symbol,
    #                         display_name,
    #                         asset_class,
    #                         currency,
    #                         tradable,
    #                         updated_at
    #                     FROM instruments
    #                     WHERE updated_at > %s
    #                     ORDER BY updated_at ASC
    #                 """
    #                 cursor.execute(query, (since,))
    #             else:
    #                 query = """
    #                     SELECT 
    #                         symbol,
    #                         display_name,
    #                         asset_class,
    #                         currency,
    #                         tradable,
    #                         updated_at
    #                     FROM instruments
    #                     ORDER BY updated_at ASC
    #                 """
    #                 cursor.execute(query)

    #             rows = cursor.fetchall()
    #             return [dict(row) for row in rows]

    def extract_accounts(self) -> list[dict[str, Any]]:
        """Extract accounts joined with users to capture business account number and holder name."""
        with psycopg2.connect(self.conn_string) as conn:
            with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cursor:
                cursor.execute("""
                    SELECT 
                        a.id AS source_id,
                        a.account_number AS account_id,
                        u.username AS holder_name,
                        a.status,
                        a.created_at::DATE AS effective_date,
                        a.updated_at
                    FROM accounts a
                    JOIN users u ON a.user_id = u.id
                    ORDER BY a.id
                """)
                rows = cursor.fetchall()
                return [dict(row) for row in rows]

    def extract_accounts_incremental(self, since: Optional[datetime] = None) -> list[dict[str, Any]]:
        """Extract accounts incrementally based on updated_at watermark."""
        with psycopg2.connect(self.conn_string) as conn:
            with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cursor:
                if since is not None:
                    query = """
                        SELECT 
                            a.id AS source_id,
                            a.account_number AS account_id,
                            u.username AS holder_name,
                            a.status,
                            a.created_at::DATE AS effective_date,
                            a.updated_at
                        FROM accounts a
                        JOIN users u ON a.user_id = u.id
                        WHERE a.updated_at > %s
                        ORDER BY a.updated_at ASC
                    """
                    cursor.execute(query, (since,))
                else:
                    query = """
                        SELECT 
                            a.id AS source_id,
                            a.account_number AS account_id,
                            u.username AS holder_name,
                            a.status,
                            a.created_at::DATE AS effective_date,
                            a.updated_at
                        FROM accounts a
                        JOIN users u ON a.user_id = u.id
                        ORDER BY a.updated_at ASC
                    """
                    cursor.execute(query)

                rows = cursor.fetchall()
                return [dict(row) for row in rows]

    def extract_orders(self, since: Optional[datetime] = None) -> list[dict[str, Any]]:
        """Extract orders incrementally based on updated_at watermark.
        
        Using updated_at ensures both newly placed orders and order state
        transitions (e.g., NEW -> FILLED / CANCELLED / REJECTED) are captured.
        """
        with psycopg2.connect(self.conn_string) as conn:
            with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cursor:
                base_sql = """
                    SELECT 
                        id,
                        account_id,
                        symbol,
                        side,
                        order_type,
                        quantity,
                        limit_price,
                        executed_price,
                        executed_on,
                        status,
                        created_at,
                        updated_at
                    FROM orders
                """
                if since is not None:
                    query = base_sql + " WHERE updated_at > %s ORDER BY updated_at ASC"
                    cursor.execute(query, (since,))
                else:
                    query = base_sql + " ORDER BY updated_at ASC"
                    cursor.execute(query)

                rows = cursor.fetchall()
                return [dict(row) for row in rows]
