"""Unit tests for incremental extract layer (instruments and accounts)."""

from __future__ import annotations

from datetime import datetime
from unittest.mock import MagicMock, patch

import pytest

from sprint_07_event_backbone_etl.extract import PostgresExtractor


class TestExtractInstrumentsIncremental:
    """Tests for incremental instrument extraction."""

    @patch("sprint_07_event_backbone_etl.extract.psycopg2.connect")
    def test_extract_instruments_incremental_with_watermark(self, mock_connect):
        """Test extracting instruments since a watermark."""
        # Mock the database connection and cursor
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_connect.return_value.__enter__.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

        # Mock query results
        mock_cursor.fetchall.return_value = [
            {
                "symbol": "INFY.NS",
                "display_name": "Infosys",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 11, 0, 0),
            },
            {
                "symbol": "TCS.NS",
                "display_name": "TCS",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 11, 30, 0),
            },
        ]

        extractor = PostgresExtractor("postgresql://test")
        watermark = datetime(2026, 9, 17, 10, 0, 0)
        
        result = extractor.extract_instruments_incremental(since=watermark)

        assert len(result) == 2
        assert result[0]["symbol"] == "INFY.NS"
        assert result[1]["symbol"] == "TCS.NS"
        assert result[0]["updated_at"] == datetime(2026, 9, 17, 11, 0, 0)

        # Verify query was called with watermark
        mock_cursor.execute.assert_called_once()
        call_args = mock_cursor.execute.call_args
        assert "updated_at > %s" in call_args[0][0]
        assert call_args[0][1] == (watermark,)

    @patch("sprint_07_event_backbone_etl.extract.psycopg2.connect")
    def test_extract_instruments_incremental_without_watermark(self, mock_connect):
        """Test extracting all instruments when no watermark provided."""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_connect.return_value.__enter__.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

        mock_cursor.fetchall.return_value = [
            {
                "symbol": "RELIANCE.NS",
                "display_name": "Reliance",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            }
        ]

        extractor = PostgresExtractor("postgresql://test")
        result = extractor.extract_instruments_incremental(since=None)

        assert len(result) == 1
        assert result[0]["symbol"] == "RELIANCE.NS"

        # Verify query was called without WHERE clause
        mock_cursor.execute.assert_called_once()
        call_args = mock_cursor.execute.call_args
        assert "updated_at > %s" not in call_args[0][0]

    @patch("sprint_07_event_backbone_etl.extract.psycopg2.connect")
    def test_extract_instruments_incremental_empty_result(self, mock_connect):
        """Test extracting instruments when no new instruments exist."""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_connect.return_value.__enter__.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

        mock_cursor.fetchall.return_value = []

        extractor = PostgresExtractor("postgresql://test")
        watermark = datetime(2026, 9, 18, 10, 0, 0)
        
        result = extractor.extract_instruments_incremental(since=watermark)

        assert len(result) == 0


class TestExtractAccountsIncremental:
    """Tests for incremental account extraction."""

    @patch("sprint_07_event_backbone_etl.extract.psycopg2.connect")
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
        
        result = extractor.extract_accounts_incremental(since=watermark)

        assert len(result) == 2
        assert result[0]["account_id"] == "ACC001"
        assert result[1]["account_id"] == "ACC002"
        assert result[0]["updated_at"] == datetime(2026, 9, 17, 11, 0, 0)

        # Verify query includes JOIN and watermark filter
        mock_cursor.execute.assert_called_once()
        call_args = mock_cursor.execute.call_args
        assert "JOIN users u" in call_args[0][0]
        assert "a.updated_at > %s" in call_args[0][0]
        assert call_args[0][1] == (watermark,)

    @patch("sprint_07_event_backbone_etl.extract.psycopg2.connect")
    def test_extract_accounts_incremental_without_watermark(self, mock_connect):
        """Test extracting all accounts when no watermark provided."""
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
        result = extractor.extract_accounts_incremental(since=None)

        assert len(result) == 1
        assert result[0]["source_id"] == 1

        # Verify query was called without WHERE clause
        mock_cursor.execute.assert_called_once()
        call_args = mock_cursor.execute.call_args
        assert "a.updated_at > %s" not in call_args[0][0]

    @patch("sprint_07_event_backbone_etl.extract.psycopg2.connect")
    def test_extract_accounts_incremental_includes_user_details(self, mock_connect):
        """Test that account extraction includes user details via JOIN."""
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
        result = extractor.extract_accounts_incremental(since=None)

        assert len(result) == 1
        assert result[0]["holder_name"] == "Alice Wong"
        assert result[0]["source_id"] == 5

    @patch("sprint_07_event_backbone_etl.extract.psycopg2.connect")
    def test_extract_accounts_incremental_empty_result(self, mock_connect):
        """Test extracting accounts when no new accounts exist."""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_connect.return_value.__enter__.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

        mock_cursor.fetchall.return_value = []

        extractor = PostgresExtractor("postgresql://test")
        watermark = datetime(2026, 9, 18, 10, 0, 0)
        
        result = extractor.extract_accounts_incremental(since=watermark)

        assert len(result) == 0


class TestExtractInstrumentsVsAccounts:
    """Tests for comparing incremental extraction behavior."""

    @patch("sprint_07_event_backbone_etl.extract.psycopg2.connect")
    def test_instruments_and_accounts_extracted_independently(self, mock_connect):
        """Test that instruments and accounts can be extracted at different times."""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_connect.return_value.__enter__.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

        # Set up cursor to return different results on consecutive calls
        mock_cursor.fetchall.side_effect = [
            [{"symbol": "INFY.NS", "display_name": "Infosys", "asset_class": "EQUITY", 
              "currency": "INR", "tradable": True, "updated_at": datetime(2026, 9, 17, 11, 0, 0)}],
            [{"source_id": 1, "account_id": "ACC001", "holder_name": "John", 
              "status": "ACTIVE", "effective_date": "2026-09-01", "updated_at": datetime(2026, 9, 17, 11, 30, 0)}],
        ]

        extractor = PostgresExtractor("postgresql://test")
        watermark = datetime(2026, 9, 17, 10, 0, 0)
        
        instruments = extractor.extract_instruments_incremental(since=watermark)
        accounts = extractor.extract_accounts_incremental(since=watermark)

        assert len(instruments) == 1
        assert instruments[0]["symbol"] == "INFY.NS"
        
        assert len(accounts) == 1
        assert accounts[0]["account_id"] == "ACC001"
