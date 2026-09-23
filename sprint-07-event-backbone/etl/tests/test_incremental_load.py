"""Unit tests for incremental trade load pipeline."""

from __future__ import annotations

import tempfile
import uuid
from datetime import datetime
from pathlib import Path
from decimal import Decimal

import duckdb
import pytest

from sprint_07_event_backbone_etl.warehouse import (
    dead_letter,
    get_last_watermark,
    initialize_warehouse,
    record_watermark,
)
from sprint_07_event_backbone_etl.validate import (
    ValidationError,
    validate_null_and_type_mismatches,
    validate_order_data,
    validate_referential_integrity,
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
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema NOT IN (
                    'information_schema',
                    'temp',
                    'system'
                )
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
            count = conn.execute(
                "SELECT COUNT(*) FROM dim_date"
            ).fetchone()[0]

            # Should have ~7300 days (20 years)
            assert count > 7000

            row = conn.execute("""
                SELECT
                    date_key,
                    full_date,
                    day,
                    month,
                    year,
                    quarter,
                    day_of_week,
                    day_name,
                    month_name,
                    is_weekday
                FROM dim_date
                LIMIT 1
            """).fetchone()

            assert row[0] >= 20140101
            assert row[1] is not None
            assert row[2] in range(1, 32)
            assert row[3] in range(1, 13)
            assert row[4] >= 2014
            assert row[5] in range(1, 5)
            assert row[9] in (True, False)


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
            "id": uuid.uuid4(),
            "account_id": 1,
            "symbol": "INFY.NS",
            "side": "BUY",
            "quantity": 100,
            "status": "FILLED",
            "created_at": datetime.now(),
        }

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
            row_data={
                "id": order_id,
                "quantity": -10,
            },
            database_path=warehouse_db,
        )

        with duckdb.connect(str(warehouse_db)) as conn:
            rows = conn.execute("""
                SELECT
                    load_batch_id,
                    error_reason,
                    source_order_id,
                    row_data
                FROM dead_letter_queue
                WHERE source_order_id = ?
            """, [order_id]).fetchall()

            assert len(rows) == 1
            assert rows[0][0] == batch_id
            assert rows[0][1] == "invalid_quantity"
            assert str(rows[0][2]) == order_id

    def test_watermark_tracking(self, warehouse_db):
        """Test that trade watermarks are recorded and retrieved."""
        now = datetime.now()
        batch_id = str(uuid.uuid4())

        assert get_last_watermark(
            "trades_incremental",
            warehouse_db,
        ) is None

        record_watermark(
            load_name="trades_incremental",
            loaded_timestamp=now,
            batch_id=batch_id,
            row_count=100,
            dlq_count=5,
            database_path=warehouse_db,
        )

        retrieved = get_last_watermark(
            "trades_incremental",
            warehouse_db,
        )

        assert retrieved is not None
        assert abs((retrieved - now).total_seconds()) < 1


class TestIncrementalDimensionLoading:
    """Tests for dimension loading."""

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
                """
                SELECT
                    account_id,
                    holder_name,
                    status,
                    is_current
                FROM dim_account
                WHERE source_id = ?
                """,
                [1],
            ).fetchone()

            assert row is not None
            assert row[0] == "ACC001"
            assert row[1] == "John Doe"
            assert row[2] == "ACTIVE"
            assert row[3] is True

    def test_load_accounts_incremental_status_change_type2_scd(
        self,
        warehouse_db,
    ):
        """Test Type 2 SCD: status change creates new version."""
        from sprint_07_event_backbone_etl.load import DuckDBLoader

        loader = DuckDBLoader(warehouse_db)

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
        assert updated == 1

        with duckdb.connect(str(warehouse_db)) as conn:
            old = conn.execute(
                """
                SELECT
                    account_key,
                    status,
                    is_current,
                    end_date
                FROM dim_account
                WHERE source_id = ?
                  AND status = ?
                """,
                [2, "ACTIVE"],
            ).fetchone()

            assert old is not None
            assert old[2] is False
            assert old[3] is not None

            new = conn.execute(
                """
                SELECT status, is_current
                FROM dim_account
                WHERE source_id = ?
                  AND status = ?
                """,
                [2, "SUSPENDED"],
            ).fetchone()

            assert new is not None
            assert new[0] == "SUSPENDED"
            assert new[1] is True

    def test_load_accounts_incremental_metadata_update(self, warehouse_db):
        """Test metadata update without status change."""
        from sprint_07_event_backbone_etl.load import DuckDBLoader

        loader = DuckDBLoader(warehouse_db)

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

        accounts_v2 = [
            {
                "source_id": 3,
                "account_id": "ACC003",
                "holder_name": "Robert Johnson",
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
                """
                SELECT holder_name
                FROM dim_account
                WHERE source_id = ?
                  AND is_current = TRUE
                """,
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

    def test_dimension_watermark_tracking_accounts(self, warehouse_db):
        """Test that account watermarks are recorded correctly."""
        now = datetime.now()
        batch_id = str(uuid.uuid4())

        assert get_last_watermark(
            "dim_account_incremental",
            warehouse_db,
        ) is None

        record_watermark(
            load_name="dim_account_incremental",
            loaded_timestamp=now,
            batch_id=batch_id,
            row_count=3,
            dlq_count=0,
            database_path=warehouse_db,
        )

        retrieved = get_last_watermark(
            "dim_account_incremental",
            warehouse_db,
        )

        assert retrieved is not None
        assert abs((retrieved - now).total_seconds()) < 1

    def test_multiple_dimension_watermarks_independent(self, warehouse_db):
        """
        Test that account and trade watermarks are independent.
        Instruments do not have a watermark because they are full-loaded.
        """
        account_time = datetime(2026, 9, 17, 11, 0, 0)

        record_watermark(
            load_name="dim_account_incremental",
            loaded_timestamp=account_time,
            batch_id="batch-account",
            row_count=3,
            dlq_count=0,
            database_path=warehouse_db,
        )

        account_wm = get_last_watermark(
            "dim_account_incremental",
            warehouse_db,
        )

        trades_wm = get_last_watermark(
            "trades_incremental",
            warehouse_db,
        )

        assert account_wm == account_time
        assert trades_wm is None