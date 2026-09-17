"""Unit tests for incremental trade load pipeline."""

from __future__ import annotations

import json
import uuid
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
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

            assert "dim_date" in table_names
            assert "dim_instrument" in table_names
            assert "dim_account" in table_names
            assert "fact_trades" in table_names
            assert "dead_letter_queue" in table_names
            assert "load_watermarks" in table_names

    def test_dim_date_populated_on_init(self, warehouse_db):
        """Verify dim_date is populated with full date range on initialization."""
        with duckdb.connect(str(warehouse_db)) as conn:
            count = conn.execute("SELECT COUNT(*) FROM dim_date").fetchone()[0]

            # Should have ~7300 days (20 years)
            assert count > 7000

            # Verify date attributes are populated
            row = conn.execute("""
                SELECT date_key, full_date, day, month, year, quarter, day_of_week, day_name, month_name, is_weekday
                FROM dim_date
                LIMIT 1
            """).fetchone()

            assert row[0] >= 20140101  # date_key as YYYYMMDD integer
            assert row[1] is not None  # full_date
            assert row[2] in range(1, 32)  # day
            assert row[3] in range(1, 13)  # month
            assert row[4] >= 2014          # year
            assert row[5] in range(1, 5)   # quarter
            assert row[9] in (True, False)  # is_weekday


class TestValidation:
    """Tests for data validation logic."""

    def test_validate_order_data_valid_filled_order(self):
        """Test validation passes for a valid FILLED order."""
        order_uuid = uuid.uuid4()
        result = validate_order_data(
            order_id=order_uuid,
            account_id=1,
            symbol="INFY.NS",
            side="BUY",
            quantity=100,
            executed_price=2500.50,
            status="FILLED",
            created_at=datetime(2026, 9, 17, 10, 0, 0),
            executed_on=datetime(2026, 9, 17, 10, 0, 5),
        )

        assert result["side"] == "BUY"
        assert result["status"] == "FILLED"
        assert result["quantity"] == 100
        assert result["date_key"] == 20260917
        assert result["price"] == Decimal("2500.50")
        assert result["trade_value"] == Decimal("250050.00")
        assert result["source_order_id"] == str(order_uuid)

    def test_validate_order_data_new_order_with_limit_price(self):
        """Test validation passes for NEW order with limit_price."""
        result = validate_order_data(
            order_id=str(uuid.uuid4()),
            account_id=1,
            symbol="INFY.NS",
            side="SELL",
            quantity=50,
            limit_price=1600.0,
            executed_price=None,
            status="NEW",
            created_at=datetime(2026, 9, 17),
            executed_on=None,
        )

        assert result["status"] == "NEW"
        assert result["price"] == Decimal("1600.0")
        assert result["trade_value"] == Decimal("80000.00")
        assert result["executed_price"] is None

    def test_validate_order_data_invalid_side(self):
        """Test validation fails for invalid side."""
        with pytest.raises(ValidationError) as exc_info:
            validate_order_data(
                order_id=str(uuid.uuid4()),
                account_id=1,
                symbol="INFY.NS",
                side="INVALID",
                quantity=100,
                executed_price=2500.0,
                status="FILLED",
                created_at=datetime.now(),
            )

        assert exc_info.value.reason == "invalid_side"

    def test_validate_order_data_negative_quantity(self):
        """Test validation fails for non-positive quantity."""
        with pytest.raises(ValidationError) as exc_info:
            validate_order_data(
                order_id=str(uuid.uuid4()),
                account_id=1,
                symbol="INFY.NS",
                side="BUY",
                quantity=-10,
                executed_price=2500.0,
                status="FILLED",
                created_at=datetime.now(),
            )

        assert exc_info.value.reason == "invalid_quantity"

    def test_validate_referential_integrity_missing_symbol(self):
        """Test validation fails when symbol not in dim_instrument."""
        with pytest.raises(ValidationError) as exc_info:
            validate_referential_integrity(
                symbol="UNKNOWN.NS",
                account_id=1,
                existing_accounts={1, 2, 3},
                existing_symbols={"INFY.NS", "RELIANCE.NS"},
            )

        assert exc_info.value.reason == "dimension_not_found"
        assert "UNKNOWN.NS" in exc_info.value.detail

    def test_validate_null_and_type_mismatches_with_uuid(self):
        """Test validation passes for valid types including Python UUID."""
        row = {
            "id": uuid.uuid4(),  # Python UUID object as returned by psycopg2
            "account_id": 1,
            "symbol": "INFY.NS",
            "side": "BUY",
            "quantity": 100,
            "status": "FILLED",
            "created_at": datetime.now(),
        }

        # Should not raise type mismatch
        validate_null_and_type_mismatches(row)


class TestDeadLetteringAndWatermarks:
    """Tests for dead-lettering and watermark tracking."""

    def test_dead_letter_records_failed_row(self, warehouse_db):
        """Test that dead_letter stores failed rows with reason and batch ID."""
        order_id = str(uuid.uuid4())
        batch_id = str(uuid.uuid4())

        dead_letter(
            load_batch_id=batch_id,
            error_reason="invalid_quantity",
            error_detail="quantity must be positive",
            order_id=order_id,
            account_id=1,
            symbol="INFY.NS",
            row_data={"id": order_id, "quantity": -10},
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
            assert rows[0][1] == "invalid_quantity"
            assert str(rows[0][2]) == order_id

    def test_watermark_tracking(self, warehouse_db):
        """Test that watermarks are recorded and retrieved."""
        now = datetime.now()
        batch_id = str(uuid.uuid4())

        # Initially no watermark
        assert get_last_watermark("trades_incremental", warehouse_db) is None

        # Record a watermark
        record_watermark(
            load_name="trades_incremental",
            loaded_timestamp=now,
            batch_id=batch_id,
            row_count=100,
            dlq_count=5,
            database_path=warehouse_db,
        )

        # Retrieve watermark
        retrieved = get_last_watermark("trades_incremental", warehouse_db)
        assert retrieved is not None
        assert abs((retrieved - now).total_seconds()) < 1


class TestIncrementalDimensionLoading:
    """Tests for incremental dimension loading (instruments and accounts)."""

    def test_load_instruments_incremental_insert_new(self, warehouse_db):
        """Test incremental load inserts new instruments."""
        from sprint_07_event_backbone_etl.load import DuckDBLoader

        loader = DuckDBLoader(warehouse_db)
        
        instruments = [
            {
                "symbol": "INFY.NS",
                "display_name": "Infosys Limited",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            }
        ]

        inserted, updated = loader.load_instruments_incremental(instruments)
        
        assert inserted == 1
        assert updated == 0

        with duckdb.connect(str(warehouse_db)) as conn:
            row = conn.execute(
                "SELECT symbol, name, exchange FROM dim_instrument WHERE symbol = ?",
                ["INFY.NS"],
            ).fetchone()
            assert row is not None
            assert row[0] == "INFY.NS"
            assert row[1] == "Infosys Limited"
            assert row[2] == "NSE"  # Derived from .NS suffix

    def test_load_instruments_incremental_update_existing(self, warehouse_db):
        """Test incremental load updates existing instruments."""
        from sprint_07_event_backbone_etl.load import DuckDBLoader

        loader = DuckDBLoader(warehouse_db)
        
        # First insert
        instruments_v1 = [
            {
                "symbol": "TCS.NS",
                "display_name": "Tata Consultancy Services",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            }
        ]
        loader.load_instruments_incremental(instruments_v1)

        # Update with new name
        instruments_v2 = [
            {
                "symbol": "TCS.NS",
                "display_name": "TCS (Updated)",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 11, 0, 0),
            }
        ]
        inserted, updated = loader.load_instruments_incremental(instruments_v2)
        
        assert inserted == 0
        assert updated == 1

        with duckdb.connect(str(warehouse_db)) as conn:
            row = conn.execute(
                "SELECT symbol, name FROM dim_instrument WHERE symbol = ?",
                ["TCS.NS"],
            ).fetchone()
            assert row[1] == "TCS (Updated)"

    def test_load_instruments_incremental_empty_list(self, warehouse_db):
        """Test incremental load handles empty list."""
        from sprint_07_event_backbone_etl.load import DuckDBLoader

        loader = DuckDBLoader(warehouse_db)
        inserted, updated = loader.load_instruments_incremental([])
        
        assert inserted == 0
        assert updated == 0

    def test_load_instruments_incremental_multiple_mixed(self, warehouse_db):
        """Test incremental load with mix of inserts and updates."""
        from sprint_07_event_backbone_etl.load import DuckDBLoader

        loader = DuckDBLoader(warehouse_db)
        
        # Initial batch: 2 new instruments
        batch1 = [
            {
                "symbol": "RELIANCE.NS",
                "display_name": "Reliance Industries",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            },
            {
                "symbol": "HDFC.NS",
                "display_name": "HDFC Bank",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            },
        ]
        inserted1, updated1 = loader.load_instruments_incremental(batch1)
        assert inserted1 == 2
        assert updated1 == 0

        # Second batch: 1 update + 1 new
        batch2 = [
            {
                "symbol": "RELIANCE.NS",
                "display_name": "Reliance (Updated)",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 11, 0, 0),
            },
            {
                "symbol": "ITC.NS",
                "display_name": "ITC Limited",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "updated_at": datetime(2026, 9, 17, 11, 0, 0),
            },
        ]
        inserted2, updated2 = loader.load_instruments_incremental(batch2)
        assert inserted2 == 1
        assert updated2 == 1

    def test_load_accounts_incremental_insert_new(self, warehouse_db):
        """Test incremental load inserts new accounts."""
        from sprint_07_event_backbone_etl.load import DuckDBLoader

        loader = DuckDBLoader(warehouse_db)
        
        accounts = [
            {
                "source_id": 1,
                "account_id": "ACC001",
                "holder_name": "John Doe",
                "status": "ACTIVE",
                "effective_date": datetime(2026, 9, 1).date(),
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            }
        ]

        inserted, updated = loader.load_accounts_incremental(accounts)
        
        assert inserted == 1
        assert updated == 0

        with duckdb.connect(str(warehouse_db)) as conn:
            row = conn.execute(
                "SELECT account_id, holder_name, status, is_current FROM dim_account WHERE source_id = ?",
                [1],
            ).fetchone()
            assert row is not None
            assert row[0] == "ACC001"
            assert row[1] == "John Doe"
            assert row[2] == "ACTIVE"
            assert row[3] is True  # is_current

    def test_load_accounts_incremental_status_change_type2_scd(self, warehouse_db):
        """Test Type 2 SCD: account status change creates new version."""
        from sprint_07_event_backbone_etl.load import DuckDBLoader

        loader = DuckDBLoader(warehouse_db)
        
        # Initial insert
        accounts_v1 = [
            {
                "source_id": 2,
                "account_id": "ACC002",
                "holder_name": "Jane Smith",
                "status": "ACTIVE",
                "effective_date": datetime(2026, 9, 1).date(),
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            }
        ]
        loader.load_accounts_incremental(accounts_v1)

        # Status change to SUSPENDED
        accounts_v2 = [
            {
                "source_id": 2,
                "account_id": "ACC002",
                "holder_name": "Jane Smith",
                "status": "SUSPENDED",
                "effective_date": datetime(2026, 9, 17).date(),
                "updated_at": datetime(2026, 9, 17, 11, 0, 0),
            }
        ]
        inserted, updated = loader.load_accounts_incremental(accounts_v2)
        
        assert inserted == 0
        assert updated == 1  # Type 2 SCD treats status change as update

        with duckdb.connect(str(warehouse_db)) as conn:
            # Check old version is expired
            old = conn.execute(
                "SELECT account_key, status, is_current, end_date FROM dim_account WHERE source_id = ? AND status = ?",
                [2, "ACTIVE"],
            ).fetchone()
            assert old is not None
            assert old[2] is False  # is_current = False
            assert old[3] is not None  # end_date is set

            # Check new version is current
            new = conn.execute(
                "SELECT status, is_current FROM dim_account WHERE source_id = ? AND status = ?",
                [2, "SUSPENDED"],
            ).fetchone()
            assert new is not None
            assert new[0] == "SUSPENDED"
            assert new[1] is True  # is_current = True

    def test_load_accounts_incremental_metadata_update(self, warehouse_db):
        """Test incremental load updates account metadata without status change."""
        from sprint_07_event_backbone_etl.load import DuckDBLoader

        loader = DuckDBLoader(warehouse_db)
        
        # Initial insert
        accounts_v1 = [
            {
                "source_id": 3,
                "account_id": "ACC003",
                "holder_name": "Bob Johnson",
                "status": "ACTIVE",
                "effective_date": datetime(2026, 9, 1).date(),
                "updated_at": datetime(2026, 9, 17, 10, 0, 0),
            }
        ]
        loader.load_accounts_incremental(accounts_v1)

        # Update holder_name only (same status)
        accounts_v2 = [
            {
                "source_id": 3,
                "account_id": "ACC003",
                "holder_name": "Robert Johnson",  # Updated name
                "status": "ACTIVE",
                "effective_date": datetime(2026, 9, 1).date(),
                "updated_at": datetime(2026, 9, 17, 11, 0, 0),
            }
        ]
        inserted, updated = loader.load_accounts_incremental(accounts_v2)
        
        assert inserted == 0
        assert updated == 1

        with duckdb.connect(str(warehouse_db)) as conn:
            row = conn.execute(
                "SELECT holder_name FROM dim_account WHERE source_id = ? AND is_current = TRUE",
                [3],
            ).fetchone()
            assert row[0] == "Robert Johnson"

    def test_load_accounts_incremental_empty_list(self, warehouse_db):
        """Test incremental load handles empty account list."""
        from sprint_07_event_backbone_etl.load import DuckDBLoader

        loader = DuckDBLoader(warehouse_db)
        inserted, updated = loader.load_accounts_incremental([])
        
        assert inserted == 0
        assert updated == 0

    def test_dimension_watermark_tracking_instruments(self, warehouse_db):
        """Test that instrument watermarks are recorded correctly."""
        now = datetime.now(timezone.utc)
        batch_id = str(uuid.uuid4())

        # Initially no watermark for instruments
        assert get_last_watermark("dim_instrument_incremental", warehouse_db) is None

        # Record watermark
        record_watermark(
            load_name="dim_instrument_incremental",
            loaded_timestamp=now,
            batch_id=batch_id,
            row_count=5,
            dlq_count=0,
            database_path=warehouse_db,
        )

        # Retrieve watermark
        retrieved = get_last_watermark("dim_instrument_incremental", warehouse_db)
        assert retrieved is not None
        assert abs((retrieved - now).total_seconds()) < 1

    def test_dimension_watermark_tracking_accounts(self, warehouse_db):
        """Test that account watermarks are recorded correctly."""
        now = datetime.now(timezone.utc)
        batch_id = str(uuid.uuid4())

        # Initially no watermark for accounts
        assert get_last_watermark("dim_account_incremental", warehouse_db) is None

        # Record watermark
        record_watermark(
            load_name="dim_account_incremental",
            loaded_timestamp=now,
            batch_id=batch_id,
            row_count=3,
            dlq_count=0,
            database_path=warehouse_db,
        )

        # Retrieve watermark
        retrieved = get_last_watermark("dim_account_incremental", warehouse_db)
        assert retrieved is not None
        assert abs((retrieved - now).total_seconds()) < 1

    def test_multiple_dimension_watermarks_independent(self, warehouse_db):
        """Test that different dimension watermarks are tracked independently."""
        time1 = datetime(2026, 9, 17, 10, 0, 0)
        time2 = datetime(2026, 9, 17, 11, 0, 0)

        # Record instrument watermark
        record_watermark(
            load_name="dim_instrument_incremental",
            loaded_timestamp=time1,
            batch_id="batch1",
            row_count=5,
            dlq_count=0,
            database_path=warehouse_db,
        )

        # Record account watermark
        record_watermark(
            load_name="dim_account_incremental",
            loaded_timestamp=time2,
            batch_id="batch2",
            row_count=3,
            dlq_count=0,
            database_path=warehouse_db,
        )

        # Verify both are independent
        inst_wm = get_last_watermark("dim_instrument_incremental", warehouse_db)
        acc_wm = get_last_watermark("dim_account_incremental", warehouse_db)
        trades_wm = get_last_watermark("trades_incremental", warehouse_db)

        assert inst_wm == time1
        assert acc_wm == time2
        assert trades_wm is None  # Not set
