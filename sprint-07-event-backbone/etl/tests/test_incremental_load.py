"""Unit tests for incremental trade load pipeline."""

from __future__ import annotations

import json
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
import tempfile

import pytest
import duckdb

from sprint_07_event_backbone_etl.warehouse import (
    initialize_warehouse,
    get_last_watermark,
    record_watermark,
    dead_letter,
)
from sprint_07_event_backbone_etl.validate import (
    ValidationError,
    validate_order_data,
    validate_referential_integrity,
    validate_null_and_type_mismatches,
)


@pytest.fixture
def warehouse_db():
    """Create a temporary DuckDB database for testing."""
    with tempfile.TemporaryDirectory() as tmpdir:
        db_path = Path(tmpdir) / "test.duckdb"
        initialize_warehouse(db_path)
        yield db_path


class TestWarehouseSchema:
    """Tests for warehouse schema initialization."""
    
    def test_initialize_warehouse_creates_all_tables(self, warehouse_db):
        """Verify all dimension and fact tables are created."""
        with duckdb.connect(str(warehouse_db)) as conn:
            tables = conn.execute("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema NOT IN ('information_schema', 'temp', 'system')
                ORDER BY table_name
            """).fetchall()
            
            table_names = {row[0].lower() for row in tables}
            
            assert 'dim_date' in table_names
            assert 'dim_instrument' in table_names
            assert 'dim_account' in table_names
            assert 'fact_trades' in table_names
            assert 'dead_letter_queue' in table_names
            assert 'load_watermarks' in table_names
    
    def test_dim_date_populated_on_init(self, warehouse_db):
        """Verify dim_date is populated with full date range on initialization."""
        with duckdb.connect(str(warehouse_db)) as conn:
            count = conn.execute("SELECT COUNT(*) FROM dim_date").fetchone()[0]
            
            # Should have ~7300 days (20 years)
            assert count > 7000
            
            # Verify date attributes are populated
            row = conn.execute("""
                SELECT date_key, year, month, day, quarter, week, day_name, day_of_week, is_weekend
                FROM dim_date
                LIMIT 1
            """).fetchone()
            
            assert row[0] is not None  # date_key
            assert row[1] > 2010      # year
            assert row[2] in range(1, 13)  # month
            assert row[3] in range(1, 32)  # day
            assert row[4] in range(1, 5)   # quarter
            assert row[8] in (True, False)  # is_weekend


class TestValidation:
    """Tests for data validation logic."""
    
    def test_validate_order_data_valid_filled_order(self):
        """Test validation passes for a valid FILLED order."""
        result = validate_order_data(
            order_id=str(uuid.uuid4()),
            account_id=1,
            symbol='INFY.NS',
            side='BUY',
            quantity=100,
            executed_price=2500.50,
            status='FILLED',
            created_at=datetime.now(),
            executed_on=datetime.now(),
        )
        
        assert result['side'] == 'BUY'
        assert result['status'] == 'FILLED'
        assert result['quantity'] == 100
    
    def test_validate_order_data_new_order_no_price(self):
        """Test validation passes for NEW order without executed_price."""
        result = validate_order_data(
            order_id=str(uuid.uuid4()),
            account_id=1,
            symbol='INFY.NS',
            side='SELL',
            quantity=50,
            executed_price=None,
            status='NEW',
            created_at=datetime.now(),
            executed_on=None,
        )
        
        assert result['status'] == 'NEW'
        assert result['trade_value'] is None
        assert result['executed_price'] is None
    
    def test_validate_order_data_invalid_side(self):
        """Test validation fails for invalid side."""
        with pytest.raises(ValidationError) as exc_info:
            validate_order_data(
                order_id=str(uuid.uuid4()),
                account_id=1,
                symbol='INFY.NS',
                side='INVALID',
                quantity=100,
                executed_price=2500.0,
                status='FILLED',
                created_at=datetime.now(),
                executed_on=datetime.now(),
            )
        
        assert exc_info.value.reason == 'invalid_side'
    
    def test_validate_order_data_negative_quantity(self):
        """Test validation fails for non-positive quantity."""
        with pytest.raises(ValidationError) as exc_info:
            validate_order_data(
                order_id=str(uuid.uuid4()),
                account_id=1,
                symbol='INFY.NS',
                side='BUY',
                quantity=-10,
                executed_price=2500.0,
                status='FILLED',
                created_at=datetime.now(),
                executed_on=datetime.now(),
            )
        
        assert exc_info.value.reason == 'invalid_quantity'
    
    def test_validate_referential_integrity_missing_symbol(self):
        """Test validation fails when symbol not in dim_instrument."""
        with pytest.raises(ValidationError) as exc_info:
            validate_referential_integrity(
                symbol='UNKNOWN.NS',
                account_id=1,
                existing_accounts={1, 2, 3},
                existing_symbols={'INFY.NS', 'RELIANCE.NS'},
            )
        
        assert exc_info.value.reason == 'dimension_not_found'
        assert 'UNKNOWN.NS' in exc_info.value.detail
    
    def test_validate_null_and_type_mismatches_valid(self):
        """Test validation passes for valid types."""
        row = {
            'id': str(uuid.uuid4()),
            'account_id': 1,
            'symbol': 'INFY.NS',
            'side': 'BUY',
            'quantity': 100,
            'status': 'FILLED',
            'created_at': datetime.now(),
        }
        
        # Should not raise
        validate_null_and_type_mismatches(row)


class TestDeadLetteringAndWatermarks:
    """Tests for dead-lettering and watermark tracking."""
    
    def test_dead_letter_records_failed_row(self, warehouse_db):
        """Test that dead_letter stores failed rows with reason and batch ID."""
        order_id = str(uuid.uuid4())
        batch_id = str(uuid.uuid4())
        
        dead_letter(
            load_batch_id=batch_id,
            error_reason='invalid_quantity',
            error_detail='quantity must be positive',
            order_id=order_id,
            account_id=1,
            symbol='INFY.NS',
            row_data={'id': order_id, 'quantity': -10},
            database_path=warehouse_db,
        )
        
        with duckdb.connect(str(warehouse_db)) as conn:
            rows = conn.execute("""
                SELECT load_batch_id, error_reason, source_order_id, row_data
                FROM dead_letter_queue
                WHERE source_order_id = ?
            """, [order_id]).fetchall()
            
            assert len(rows) == 1
            assert rows[0][0] == batch_id
            assert rows[0][1] == 'invalid_quantity'
            assert str(rows[0][2]) == order_id
    
    def test_watermark_tracking(self, warehouse_db):
        """Test that watermarks are recorded and retrieved."""
        now = datetime.now(timezone.utc)
        batch_id = str(uuid.uuid4())
        
        # Initially no watermark
        assert get_last_watermark(warehouse_db) is None
        
        # Record a watermark
        record_watermark(
            load_name='trades_incremental',
            loaded_timestamp=now,
            batch_id=batch_id,
            row_count=100,
            dlq_count=5,
            database_path=warehouse_db,
        )
        
        # Retrieve watermark
        retrieved = get_last_watermark(warehouse_db)
        assert retrieved is not None
        # Compare timestamps (allowing for rounding)
        # Handle both naive and offset-aware datetimes
        if retrieved.tzinfo is not None and now.tzinfo is None:
            now = now.replace(tzinfo=timezone.utc)
        elif retrieved.tzinfo is None and now.tzinfo is not None:
            retrieved = retrieved.replace(tzinfo=timezone.utc)
        
        assert abs((retrieved - now).total_seconds()) < 1


class TestIncrementalLoadScenarios:
    """Integration tests for complete load scenarios."""
    
    def test_invalid_row_is_dead_lettered_not_dropped(self, warehouse_db):
        """Test that an invalid row is dead-lettered with reason and batch ID."""
        batch_id = str(uuid.uuid4())
        order_id = str(uuid.uuid4())
        
        # Dead-letter an invalid row
        dead_letter(
            load_batch_id=batch_id,
            error_reason='invalid_quantity',
            error_detail='quantity must be positive integer',
            order_id=order_id,
            account_id=1,
            symbol='INFY.NS',
            row_data={'id': order_id, 'quantity': -100},
            database_path=warehouse_db,
        )
        
        # Verify it's in dead letter queue
        with duckdb.connect(str(warehouse_db)) as conn:
            count = conn.execute(
                "SELECT COUNT(*) FROM dead_letter_queue WHERE load_batch_id = ?",
                [batch_id]
            ).fetchone()[0]
            
            assert count == 1
        
        # Verify nothing is in fact_trades
        with duckdb.connect(str(warehouse_db)) as conn:
            count = conn.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
            assert count == 0
