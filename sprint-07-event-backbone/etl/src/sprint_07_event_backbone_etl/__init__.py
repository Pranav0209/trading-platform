"""Sprint 7 incremental trade data ETL package.

Structured into distinct layers:
- extract: PostgreSQL extraction with watermark filtering
- transform: 11-point data validation, exchange derivation, financial calculations
- load: DuckDB star schema loading, dimension management, and dead-lettering
- warehouse: Schema DDL, surrogate key helpers, watermark tracking
- pipeline: End-to-end orchestration
- analytics: Integrity verification and analytical reporting
"""

from .pipeline import IncrementalTradePipeline, IncrementalTradeLoader
from .extract import PostgresExtractor
from .transform import (
    TradeTransformer,
    ValidationError,
    validate_order_data,
    validate_referential_integrity,
    validate_null_and_type_mismatches,
    derive_exchange,
)
from .load import DuckDBLoader
from .warehouse import (
    initialize_warehouse,
    get_last_watermark,
    record_watermark,
    dead_letter,
)
from .analytics import run_warehouse_analytics

__all__ = [
    "IncrementalTradePipeline",
    "IncrementalTradeLoader",
    "PostgresExtractor",
    "TradeTransformer",
    "DuckDBLoader",
    "ValidationError",
    "validate_order_data",
    "validate_referential_integrity",
    "validate_null_and_type_mismatches",
    "derive_exchange",
    "initialize_warehouse",
    "get_last_watermark",
    "record_watermark",
    "dead_letter",
    "run_warehouse_analytics",
]
