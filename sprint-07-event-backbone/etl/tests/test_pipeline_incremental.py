"""Unit tests for incremental trade pipeline loading."""

from __future__ import annotations

from datetime import datetime
from pathlib import Path
from unittest.mock import MagicMock, patch
import tempfile

import pytest

from sprint_07_event_backbone_etl.warehouse import (
    get_last_watermark,
    initialize_warehouse,
)
from sprint_07_event_backbone_etl.pipeline import IncrementalTradePipeline


class TestLoadDimensionsIncremental:
    """Tests for pipeline-level incremental dimension loading."""

    @pytest.fixture
    def pipeline_db(self):
        """Create temporary DuckDB for pipeline tests."""
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = Path(tmpdir) / "test.duckdb"
            initialize_warehouse(db_path)
            yield db_path

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_load_dimensions_incremental_accounts_only(
        self,
        mock_extractor_class,
        pipeline_db,
    ):
        """Test that incremental dimension loading handles accounts only."""
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        mock_extractor.extract_accounts_incremental.return_value = [
            {
                "source_id": 1,
                "account_id": "ACC001",
                "holder_name": "John Doe",
                "status": "ACTIVE",
                "effective_date": datetime(2026, 9, 1).date(),
                "updated_at": datetime(2026, 9, 17, 11, 0, 0),
            }
        ]

        pipeline = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )

        result = pipeline.load_dimensions_incremental()

        assert result["status"] == "success"

        assert pipeline.stats["dim_account_loaded"] == 1
        assert pipeline.stats["dim_account_updated"] == 0

        # Instruments must NOT be loaded by the incremental path.
        mock_extractor.extract_instruments.assert_not_called()

        # Accounts must be loaded incrementally.
        mock_extractor.extract_accounts_incremental.assert_called_once()

        watermark = get_last_watermark(
            "dim_account_incremental",
            pipeline_db,
        )

        assert watermark is not None

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_load_dimensions_incremental_multiple_accounts(
        self,
        mock_extractor_class,
        pipeline_db,
    ):
        """Test incremental loading of multiple accounts."""
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        mock_extractor.extract_accounts_incremental.return_value = [
            {
                "source_id": 1,
                "account_id": "ACC001",
                "holder_name": "John Doe",
                "status": "ACTIVE",
                "effective_date": datetime(2026, 9, 1).date(),
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            },
            {
                "source_id": 2,
                "account_id": "ACC002",
                "holder_name": "Jane Smith",
                "status": "ACTIVE",
                "effective_date": datetime(2026, 9, 1).date(),
                "updated_at": datetime(2026, 9, 17, 11, 0, 0),
            },
        ]

        pipeline = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )

        result = pipeline.load_dimensions_incremental()

        assert result["status"] == "success"

        assert pipeline.stats["dim_account_loaded"] == 2
        assert pipeline.stats["dim_account_updated"] == 0

        mock_extractor.extract_instruments.assert_not_called()
        mock_extractor.extract_accounts_incremental.assert_called_once()

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_load_dimensions_incremental_no_changes(
        self,
        mock_extractor_class,
        pipeline_db,
    ):
        """Test incremental dimension loading when no accounts changed."""
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        mock_extractor.extract_accounts_incremental.return_value = []

        pipeline = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )

        result = pipeline.load_dimensions_incremental()

        assert result["status"] == "success"

        assert pipeline.stats["dim_account_loaded"] == 0
        assert pipeline.stats["dim_account_updated"] == 0

        # No instrument loading in incremental path.
        mock_extractor.extract_instruments.assert_not_called()

        mock_extractor.extract_accounts_incremental.assert_called_once()

        # No account watermark should be created when nothing was loaded.
        assert (
            get_last_watermark(
                "dim_account_incremental",
                pipeline_db,
            )
            is None
        )

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_load_dimensions_incremental_does_not_create_instrument_watermark(
        self,
        mock_extractor_class,
        pipeline_db,
    ):
        """
        Verify that instruments have no incremental watermark.

        Instruments are full-loaded through load_dimensions().
        """
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        mock_extractor.extract_accounts_incremental.return_value = []

        pipeline = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )

        pipeline.load_dimensions_incremental()

        mock_extractor.extract_instruments.assert_not_called()

        # There must never be an instrument incremental watermark.
        assert (
            get_last_watermark(
                "dim_instrument_incremental",
                pipeline_db,
            )
            is None
        )


class TestLoadDimensionsFull:
    """Tests for the full dimension loading path."""

    @pytest.fixture
    def pipeline_db(self):
        """Create temporary DuckDB for pipeline tests."""
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = Path(tmpdir) / "test.duckdb"
            initialize_warehouse(db_path)
            yield db_path

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_load_dimensions_full_loads_instruments_and_accounts(
        self,
        mock_extractor_class,
        pipeline_db,
    ):
        """
        Verify that the full dimension load handles both dimensions.

        Instruments belong to this path.
        """
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        mock_extractor.extract_instruments.return_value = [
            {
                "symbol": "INFY.NS",
                "display_name": "Infosys",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
            }
        ]

        mock_extractor.extract_accounts.return_value = [
            {
                "source_id": 1,
                "account_id": "ACC001",
                "holder_name": "John Doe",
                "status": "ACTIVE",
                "effective_date": datetime(2026, 9, 1).date(),
                "updated_at": datetime(2026, 9, 17, 11, 0, 0),
            }
        ]

        pipeline = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )

        result = pipeline.load_dimensions()

        assert result["status"] == "success"

        assert pipeline.stats["dim_instrument_loaded"] == 1
        assert pipeline.stats["dim_account_loaded"] == 1

        # Full dimension load DOES load instruments.
        mock_extractor.extract_instruments.assert_called_once()

        # Full dimension load also loads accounts.
        mock_extractor.extract_accounts.assert_called_once()

        # Incremental account extraction is not part of load_dimensions().
        mock_extractor.extract_accounts_incremental.assert_not_called()


class TestLoadTradesCallsDimensions:
    """Tests for load_trades_incremental calling incremental dimensions first."""

    @pytest.fixture
    def pipeline_db(self):
        """Create temporary DuckDB for pipeline tests."""
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = Path(tmpdir) / "test.duckdb"
            initialize_warehouse(db_path)
            yield db_path

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_load_trades_incremental_calls_account_dimensions_first(
        self,
        mock_extractor_class,
        pipeline_db,
    ):
        """
        Test that account dimensions are loaded before trades.

        Instruments are intentionally not loaded here because they belong
        to the full dimension load.
        """
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        mock_extractor.extract_accounts_incremental.return_value = [
            {
                "source_id": 1,
                "account_id": "ACC001",
                "holder_name": "John",
                "status": "ACTIVE",
                "effective_date": datetime(2026, 9, 1).date(),
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            }
        ]

        mock_extractor.extract_orders.return_value = []

        pipeline = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )

        pipeline.load_trades_incremental()

        # Accounts are loaded by the incremental trade path.
        assert pipeline.stats["dim_account_loaded"] == 1

        mock_extractor.extract_accounts_incremental.assert_called_once()

        # Instruments must NOT be loaded here.
        mock_extractor.extract_instruments.assert_not_called()

        # No trade extraction result means no trade rows.
        mock_extractor.extract_orders.assert_called_once()

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_load_trades_incremental_does_not_load_instruments(
        self,
        mock_extractor_class,
        pipeline_db,
    ):
        """
        Verify that trade incremental loading does not load instruments.

        Instrument loading belongs exclusively to load_dimensions().
        """
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        mock_extractor.extract_accounts_incremental.return_value = [
            {
                "source_id": 1,
                "account_id": "ACC001",
                "holder_name": "John",
                "status": "ACTIVE",
                "effective_date": datetime(2026, 9, 1).date(),
                "updated_at": datetime(2026, 9, 17, 9, 0, 0),
            }
        ]

        mock_extractor.extract_orders.return_value = []

        pipeline = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )

        pipeline.load_trades_incremental()

        # Incremental trade path loads accounts.
        assert pipeline.stats["dim_account_loaded"] == 1

        # But never instruments.
        mock_extractor.extract_instruments.assert_not_called()

        # No instrument incremental stats should exist.
        assert "dim_instrument_updated" not in pipeline.stats

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_load_trades_incremental_processes_trade(
        self,
        mock_extractor_class,
        pipeline_db,
    ):
        """
        Test that an incremental trade is extracted and transformed.

        The test assumes the required instrument already exists in the
        warehouse because instruments are loaded by load_dimensions().
        """
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        mock_extractor.extract_accounts_incremental.return_value = [
            {
                "source_id": 1,
                "account_id": "ACC001",
                "holder_name": "John",
                "status": "ACTIVE",
                "effective_date": datetime(2026, 9, 1).date(),
                "updated_at": datetime(2026, 9, 17, 9, 0, 0),
            }
        ]

        mock_extractor.extract_orders.return_value = [
            {
                "id": "order-1",
                "account_id": 1,
                "symbol": "NEWSTOCK.NS",
                "side": "BUY",
                "quantity": 100,
                "limit_price": 500.0,
                "executed_price": 500.0,
                "executed_on": datetime(2026, 9, 17, 10, 0, 0),
                "status": "FILLED",
                "created_at": datetime(2026, 9, 17, 10, 0, 0),
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            }
        ]

        pipeline = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )

        with patch(
            "sprint_07_event_backbone_etl.pipeline.TradeTransformer"
        ) as mock_transformer_class:

            mock_transformer = MagicMock()
            mock_transformer_class.return_value = mock_transformer

            mock_transformer.transform_order.return_value = {
                "source_order_id": "order-1",
                "account_id": 1,
                "symbol": "NEWSTOCK.NS",
                "side": "BUY",
                "quantity": 100,
                "price": 500.0,
                "status": "FILLED",
                "executed_price": 500.0,
                "trade_value": 50000.0,
                "date_key": 20260917,
                "created_at": datetime(2026, 9, 17, 10, 0, 0),
                "executed_on": datetime(2026, 9, 17, 10, 0, 0),
            }

            # Mock the actual trade upsert.
            with patch.object(
                pipeline.loader,
                "upsert_trades",
                return_value=(1, 0),
            ) as mock_upsert:

                pipeline.load_trades_incremental()

                # Account dimension was loaded first.
                mock_extractor.extract_accounts_incremental.assert_called_once()

                # Instruments are NOT loaded by this path.
                mock_extractor.extract_instruments.assert_not_called()

                # Orders were extracted.
                mock_extractor.extract_orders.assert_called_once()

                # Order was transformed.
                mock_transformer.transform_order.assert_called_once()

                # Transformed trade was sent to the loader.
                mock_upsert.assert_called_once()

                # One trade was inserted.
                assert pipeline.stats["fact_trades_loaded"] == 1
                assert pipeline.stats["fact_trades_updated"] == 0

                # Instrument incremental stat does not exist.
                assert "dim_instrument_updated" not in pipeline.stats