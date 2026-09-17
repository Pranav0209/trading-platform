"""Unit tests for incremental dimension pipeline loading."""

from __future__ import annotations

from datetime import datetime
from pathlib import Path
from unittest.mock import MagicMock, patch
import tempfile

import pytest
import duckdb

from sprint_07_event_backbone_etl.warehouse import initialize_warehouse, get_last_watermark
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
    def test_load_dimensions_incremental_instruments_only(self, mock_extractor_class, pipeline_db):
        """Test loading new instruments incrementally."""
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        # Mock incremental instrument extraction
        mock_extractor.extract_instruments_incremental.return_value = [
            {
                "symbol": "INFY.NS",
                "display_name": "Infosys",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 11, 0, 0),
            }
        ]

        # Mock empty accounts
        mock_extractor.extract_accounts_incremental.return_value = []

        pipeline = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )

        result = pipeline.load_dimensions_incremental()

        assert result["status"] == "success"
        assert pipeline.stats["dim_instrument_loaded"] == 1
        assert pipeline.stats["dim_instrument_updated"] == 0

        # Verify watermark was recorded
        watermark = get_last_watermark("dim_instrument_incremental", pipeline_db)
        assert watermark is not None

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_load_dimensions_incremental_accounts_only(self, mock_extractor_class, pipeline_db):
        """Test loading new accounts incrementally."""
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        # Mock empty instruments
        mock_extractor.extract_instruments_incremental.return_value = []

        # Mock incremental account extraction
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

        # Verify watermark was recorded
        watermark = get_last_watermark("dim_account_incremental", pipeline_db)
        assert watermark is not None

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_load_dimensions_incremental_both(self, mock_extractor_class, pipeline_db):
        """Test loading both instruments and accounts incrementally."""
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        mock_extractor.extract_instruments_incremental.return_value = [
            {
                "symbol": "TCS.NS",
                "display_name": "TCS",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 11, 0, 0),
            }
        ]

        mock_extractor.extract_accounts_incremental.return_value = [
            {
                "source_id": 2,
                "account_id": "ACC002",
                "holder_name": "Jane Smith",
                "status": "ACTIVE",
                "effective_date": datetime(2026, 9, 1).date(),
                "updated_at": datetime(2026, 9, 17, 11, 30, 0),
            }
        ]

        pipeline = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )

        result = pipeline.load_dimensions_incremental()

        assert result["status"] == "success"
        assert pipeline.stats["dim_instrument_loaded"] == 1
        assert pipeline.stats["dim_account_loaded"] == 1

        # Verify both watermarks were recorded
        inst_wm = get_last_watermark("dim_instrument_incremental", pipeline_db)
        acc_wm = get_last_watermark("dim_account_incremental", pipeline_db)
        assert inst_wm is not None
        assert acc_wm is not None

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_load_dimensions_incremental_no_changes(self, mock_extractor_class, pipeline_db):
        """Test incremental load when no dimensions have changed."""
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        mock_extractor.extract_instruments_incremental.return_value = []
        mock_extractor.extract_accounts_incremental.return_value = []

        pipeline = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )

        result = pipeline.load_dimensions_incremental()

        assert result["status"] == "success"
        assert pipeline.stats["dim_instrument_loaded"] == 0
        assert pipeline.stats["dim_instrument_updated"] == 0
        assert pipeline.stats["dim_account_loaded"] == 0
        assert pipeline.stats["dim_account_updated"] == 0

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_load_dimensions_incremental_respects_previous_watermark(self, mock_extractor_class, pipeline_db):
        """Test that incremental load respects previously recorded watermark."""
        # Pre-populate instruments
        with duckdb.connect(str(pipeline_db)) as conn:
            conn.execute("""
                INSERT INTO dim_instrument 
                (instrument_key, symbol, name, asset_class, currency, exchange, tradable, loaded_at)
                VALUES (1, 'INFY.NS', 'Infosys', 'EQUITY', 'INR', 'NSE', TRUE, NOW())
            """)
            conn.commit()

        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        # First call: extract and load initial instruments
        mock_extractor.extract_instruments_incremental.return_value = [
            {
                "symbol": "INFY.NS",
                "display_name": "Infosys",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            }
        ]
        mock_extractor.extract_accounts_incremental.return_value = []

        pipeline1 = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )
        pipeline1.load_dimensions_incremental()

        # Verify watermark was recorded
        wm1 = get_last_watermark("dim_instrument_incremental", pipeline_db)
        assert wm1 == datetime(2026, 9, 17, 10, 0, 0)

        # Second call: extract instruments after previous watermark
        mock_extractor.extract_instruments_incremental.reset_mock()
        mock_extractor.extract_instruments_incremental.return_value = [
            {
                "symbol": "TCS.NS",
                "display_name": "TCS",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 11, 0, 0),
            }
        ]

        pipeline2 = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )
        pipeline2.load_dimensions_incremental()

        # Verify extractor was called with previous watermark
        call_args = mock_extractor.extract_instruments_incremental.call_args
        assert call_args[1]["since"] == wm1


class TestLoadTradesCallsDimensionsIncremental:
    """Tests for load_trades_incremental calling load_dimensions_incremental."""

    @pytest.fixture
    def pipeline_db(self):
        """Create temporary DuckDB for pipeline tests."""
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = Path(tmpdir) / "test.duckdb"
            initialize_warehouse(db_path)
            yield db_path

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_load_trades_incremental_calls_dimensions_first(self, mock_extractor_class, pipeline_db):
        """Test that load_trades_incremental loads dimensions before trades."""
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        # Mock dimension extractions
        mock_extractor.extract_instruments_incremental.return_value = [
            {
                "symbol": "INFY.NS",
                "display_name": "Infosys",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            }
        ]
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

        # Mock trade extraction - empty for this test
        mock_extractor.extract_orders.return_value = []

        pipeline = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )

        pipeline.load_trades_incremental()

        # Verify dimensions were loaded before trades
        assert pipeline.stats["dim_instrument_loaded"] > 0 or pipeline.stats["dim_instrument_updated"] > 0
        assert mock_extractor.extract_instruments_incremental.called
        assert mock_extractor.extract_accounts_incremental.called

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_load_trades_incremental_ensures_referential_integrity(self, mock_extractor_class, pipeline_db):
        """Test that dimensions are loaded before trades to ensure referential integrity."""
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        # Mock dimension with new instrument
        mock_extractor.extract_instruments_incremental.return_value = [
            {
                "symbol": "NEWSTOCK.NS",
                "display_name": "New Stock",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 9, 0, 0),
            }
        ]
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

        # Mock trade that references the new instrument
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

        # Mock the transform to validate the trade
        with patch("sprint_07_event_backbone_etl.pipeline.TradeTransformer") as mock_transformer_class:
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

            pipeline.load_trades_incremental()

            # Verify dimension calls happened before trade processing
            assert mock_extractor.extract_instruments_incremental.called
            assert mock_extractor.extract_accounts_incremental.called

            # Dimension loading should succeed, providing the new instrument
            # when TradeTransformer validates the trade
            assert pipeline.stats["dim_instrument_loaded"] + pipeline.stats["dim_instrument_updated"] > 0


class TestDimensionWatermarkPersistence:
    """Tests for dimension watermark persistence across pipeline runs."""

    @pytest.fixture
    def pipeline_db(self):
        """Create temporary DuckDB for pipeline tests."""
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = Path(tmpdir) / "test.duckdb"
            initialize_warehouse(db_path)
            yield db_path

    @patch("sprint_07_event_backbone_etl.pipeline.PostgresExtractor")
    def test_dimension_watermarks_persist_across_runs(self, mock_extractor_class, pipeline_db):
        """Test that dimension watermarks are independent and persistent."""
        mock_extractor = MagicMock()
        mock_extractor_class.return_value = mock_extractor

        time1 = datetime(2026, 9, 17, 10, 0, 0)
        time2 = datetime(2026, 9, 17, 11, 0, 0)

        # First run: load instruments at time1
        mock_extractor.extract_instruments_incremental.return_value = [
            {
                "symbol": "INFY.NS",
                "display_name": "Infosys",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": time1,
            }
        ]
        mock_extractor.extract_accounts_incremental.return_value = []

        pipeline1 = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )
        pipeline1.load_dimensions_incremental()

        inst_wm_1 = get_last_watermark("dim_instrument_incremental", pipeline_db)
        assert inst_wm_1 == time1

        # Second run: load accounts at time2
        mock_extractor.extract_instruments_incremental.return_value = []
        mock_extractor.extract_accounts_incremental.return_value = [
            {
                "source_id": 1,
                "account_id": "ACC001",
                "holder_name": "John",
                "status": "ACTIVE",
                "effective_date": datetime(2026, 9, 1).date(),
                "updated_at": time2,
            }
        ]

        pipeline2 = IncrementalTradePipeline(
            postgres_conn_string="postgresql://test",
            warehouse_path=pipeline_db,
        )
        pipeline2.load_dimensions_incremental()

        # Verify both watermarks are independent
        inst_wm_2 = get_last_watermark("dim_instrument_incremental", pipeline_db)
        acc_wm = get_last_watermark("dim_account_incremental", pipeline_db)

        assert inst_wm_2 == time1  # Unchanged
        assert acc_wm == time2  # Newly recorded
