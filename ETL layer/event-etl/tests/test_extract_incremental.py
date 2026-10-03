"""Unit tests for extract layer (full instruments and incremental accounts)."""

from __future__ import annotations

from datetime import datetime
from unittest.mock import MagicMock, patch

import pytest

from event_etl.extract import PostgresExtractor


class TestExtractInstruments:
    """Tests for full instrument extraction."""

    @patch("event_etl.extract.psycopg2.connect")
    def test_extract_instruments_full_load(self, mock_connect):
        """Test extracting all instruments."""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()

        mock_connect.return_value.__enter__.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

        mock_cursor.fetchall.return_value = [
            {
                "symbol": "INFY.NS",
                "display_name": "Infosys",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
            },
            {
                "symbol": "TCS.NS",
                "display_name": "TCS",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
            },
        ]

        extractor = PostgresExtractor("postgresql://test")

        result = extractor.extract_instruments()

        assert len(result) == 2

        assert result[0]["symbol"] == "INFY.NS"
        assert result[0]["display_name"] == "Infosys"
        assert result[0]["asset_class"] == "EQUITY"
        assert result[0]["currency"] == "INR"
        assert result[0]["tradable"] is True

        assert result[1]["symbol"] == "TCS.NS"

        mock_cursor.execute.assert_called_once()

        query = mock_cursor.execute.call_args[0][0]

        assert "FROM instruments" in query
        assert "updated_at" not in query
        assert "ORDER BY symbol" in query

    @patch("event_etl.extract.psycopg2.connect")
    def test_extract_instruments_empty_result(self, mock_connect):
        """Test extracting instruments when the source table is empty."""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()

        mock_connect.return_value.__enter__.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

        mock_cursor.fetchall.return_value = []

        extractor = PostgresExtractor("postgresql://test")

        result = extractor.extract_instruments()

        assert result == []

        mock_cursor.execute.assert_called_once()

        query = mock_cursor.execute.call_args[0][0]

        assert "FROM instruments" in query
        assert "updated_at" not in query


class TestExtractAccountsIncremental:
    """Tests for incremental account extraction."""

    @patch("event_etl.extract.psycopg2.connect")
    def test_extract_accounts_incremental_with_watermark(self, mock_connect):
        """Test extracting accounts since a watermark."""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()

        mock_connect.return_value.__enter__.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

        mock_cursor.fetchall.return_value = [
            {
                "source_id": 1,
                "account_id": "ACC001",
                "holder_name": "John Doe",
                "status": "ACTIVE",
                "effective_date": "2026-09-01",
                "updated_at": datetime(2026, 9, 17, 11, 0, 0),
            },
            {
                "source_id": 2,
                "account_id": "ACC002",
                "holder_name": "Jane Smith",
                "status": "ACTIVE",
                "effective_date": "2026-09-01",
                "updated_at": datetime(2026, 9, 17, 11, 30, 0),
            },
        ]

        extractor = PostgresExtractor("postgresql://test")

        watermark = datetime(2026, 9, 17, 10, 0, 0)

        result = extractor.extract_accounts_incremental(
            since=watermark
        )

        assert len(result) == 2
        assert result[0]["account_id"] == "ACC001"
        assert result[1]["account_id"] == "ACC002"
        assert result[0]["updated_at"] == datetime(
            2026, 9, 17, 11, 0, 0
        )

        mock_cursor.execute.assert_called_once()

        query = mock_cursor.execute.call_args[0][0]
        params = mock_cursor.execute.call_args[0][1]

        assert "JOIN users u" in query
        assert "a.updated_at > %s" in query
        assert params == (watermark,)

    @patch("event_etl.extract.psycopg2.connect")
    def test_extract_accounts_incremental_without_watermark(
        self, mock_connect
    ):
        """Test extracting all accounts when no watermark is provided."""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()

        mock_connect.return_value.__enter__.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

        mock_cursor.fetchall.return_value = [
            {
                "source_id": 1,
                "account_id": "ACC001",
                "holder_name": "John Doe",
                "status": "ACTIVE",
                "effective_date": "2026-09-01",
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            }
        ]

        extractor = PostgresExtractor("postgresql://test")

        result = extractor.extract_accounts_incremental(
            since=None
        )

        assert len(result) == 1
        assert result[0]["source_id"] == 1

        mock_cursor.execute.assert_called_once()

        query = mock_cursor.execute.call_args[0][0]

        assert "a.updated_at > %s" not in query

    @patch("event_etl.extract.psycopg2.connect")
    def test_extract_accounts_incremental_includes_user_details(
        self, mock_connect
    ):
        """Test account extraction includes user details through JOIN."""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()

        mock_connect.return_value.__enter__.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

        mock_cursor.fetchall.return_value = [
            {
                "source_id": 5,
                "account_id": "ACC005",
                "holder_name": "Alice Wong",
                "status": "ACTIVE",
                "effective_date": "2026-09-01",
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            }
        ]

        extractor = PostgresExtractor("postgresql://test")

        result = extractor.extract_accounts_incremental(
            since=None
        )

        assert len(result) == 1
        assert result[0]["holder_name"] == "Alice Wong"
        assert result[0]["source_id"] == 5

        query = mock_cursor.execute.call_args[0][0]

        assert "JOIN users u" in query

    @patch("event_etl.extract.psycopg2.connect")
    def test_extract_accounts_incremental_empty_result(
        self, mock_connect
    ):
        """Test extracting accounts when no changes exist."""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()

        mock_connect.return_value.__enter__.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

        mock_cursor.fetchall.return_value = []

        extractor = PostgresExtractor("postgresql://test")

        watermark = datetime(2026, 9, 18, 10, 0, 0)

        result = extractor.extract_accounts_incremental(
            since=watermark
        )

        assert result == []

        mock_cursor.execute.assert_called_once()


class TestExtractInstrumentsVsAccounts:
    """Tests comparing full and incremental extraction behavior."""

    @patch("event_etl.extract.psycopg2.connect")
    def test_instruments_full_accounts_incremental(
        self, mock_connect
    ):
        """
        Instruments are always fully extracted because the source table
        has no updated_at column.

        Accounts are extracted incrementally because accounts have
        updated_at.
        """
        mock_conn = MagicMock()
        mock_cursor = MagicMock()

        mock_connect.return_value.__enter__.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

        mock_cursor.fetchall.side_effect = [
            [
                {
                    "symbol": "INFY.NS",
                    "display_name": "Infosys",
                    "asset_class": "EQUITY",
                    "currency": "INR",
                    "tradable": True,
                }
            ],
            [
                {
                    "source_id": 1,
                    "account_id": "ACC001",
                    "holder_name": "John",
                    "status": "ACTIVE",
                    "effective_date": "2026-09-01",
                    "updated_at": datetime(
                        2026, 9, 17, 11, 30, 0
                    ),
                }
            ],
        ]

        extractor = PostgresExtractor("postgresql://test")

        watermark = datetime(
            2026, 9, 17, 10, 0, 0
        )

        # Instruments: FULL extraction.
        instruments = extractor.extract_instruments()

        # Accounts: INCREMENTAL extraction.
        accounts = extractor.extract_accounts_incremental(
            since=watermark
        )

        assert len(instruments) == 1
        assert instruments[0]["symbol"] == "INFY.NS"

        # Instrument records have no updated_at.
        assert "updated_at" not in instruments[0]

        assert len(accounts) == 1
        assert accounts[0]["account_id"] == "ACC001"
        assert accounts[0]["updated_at"] == datetime(
            2026, 9, 17, 11, 30, 0
        )

        # Two separate SQL queries were executed.
        assert mock_cursor.execute.call_count == 2

        instrument_query = mock_cursor.execute.call_args_list[0][0][0]
        account_query = mock_cursor.execute.call_args_list[1][0][0]

        # Instrument query is a full load.
        assert "FROM instruments" in instrument_query
        assert "updated_at" not in instrument_query

        # Account query is incremental.
        assert "FROM accounts a" in account_query
        assert "a.updated_at > %s" in account_query