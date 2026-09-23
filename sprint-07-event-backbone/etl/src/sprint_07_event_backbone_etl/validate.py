"""Validation checks for trade data before loading into fact table."""

from __future__ import annotations

import uuid
from datetime import datetime
from decimal import Decimal
from typing import Any, Optional


class ValidationError(Exception):
    """Raised when a row fails validation."""

    def __init__(self, reason: str, detail: str = ""):
        self.reason = reason
        self.detail = detail
        super().__init__(f"{reason}: {detail}" if detail else reason)


def validate_order_data(
    order_id: str | uuid.UUID,
    account_id: int,
    symbol: str,
    side: str,
    quantity: int,
    limit_price: Optional[float | Decimal] = None,
    executed_price: Optional[float | Decimal] = None,
    status: str = "NEW",
    created_at: Optional[datetime] = None,
    executed_on: Optional[datetime] = None,
) -> dict[str, Any]:
    """Run all validation checks on a trade record."""
    str_order_id = str(order_id).strip()

    # 1. Validate side
    if side not in ("BUY", "SELL"):
        raise ValidationError("invalid_side", f"side must be BUY or SELL, got '{side}'")

    # 2. Validate status
    if status not in ("NEW", "FILLED", "CANCELLED", "REJECTED"):
        raise ValidationError("invalid_status", f"status must be NEW, FILLED, CANCELLED, or REJECTED, got '{status}'")

    # 3. Validate quantity
    if not isinstance(quantity, int) or quantity <= 0:
        raise ValidationError("invalid_quantity", f"quantity must be positive integer, got '{quantity}'")

    # 4 & 5. Validate executed_price, order price, and trade_value
    dec_qty = Decimal(str(quantity))
    dec_exec_price: Optional[Decimal] = None
    dec_price: Decimal = Decimal("0.00")

    if executed_price is not None:
        try:
            dec_exec_price = Decimal(str(executed_price))
        except Exception:
            raise ValidationError("invalid_executed_price", f"executed_price must be numeric, got '{executed_price}'")

    if limit_price is not None:
        try:
            dec_price = Decimal(str(limit_price))
        except Exception:
            raise ValidationError("invalid_limit_price", f"limit_price must be numeric, got '{limit_price}'")

    if status == "FILLED":
        if dec_exec_price is None:
            raise ValidationError("missing_executed_price", f"FILLED order {str_order_id} has no executed_price")
        if dec_exec_price <= Decimal("0"):
            raise ValidationError("negative_price", f"executed_price must be positive, got {dec_exec_price}")
        dec_price = dec_exec_price
        trade_value = (dec_qty * dec_exec_price).quantize(Decimal("0.01"))
    else:
        if dec_exec_price is not None and dec_exec_price > Decimal("0"):
            dec_price = dec_exec_price
        trade_value = (dec_qty * dec_price).quantize(Decimal("0.01"))

    # Compute integer date_key in YYYYMMDD format
    if created_at is None:
        created_at = datetime.now()
    date_key = created_at.year * 10000 + created_at.month * 100 + created_at.day

    return {
        "source_order_id": str_order_id,
        "account_id": account_id,
        "symbol": symbol,
        "side": side,
        "quantity": quantity,
        "price": dec_price,
        "executed_price": dec_exec_price,
        "status": status,
        "trade_value": trade_value,
        "created_at": created_at,
        "executed_on": executed_on,
        "date_key": date_key,
    }


def validate_referential_integrity(
    symbol: str,
    account_id: int,
    existing_accounts: set[int],
    existing_symbols: set[str],
) -> None:
    """Validate that referenced dimensions exist."""
    if symbol not in existing_symbols:
        raise ValidationError("dimension_not_found", f"symbol '{symbol}' not found in dim_instrument")

    if account_id not in existing_accounts:
        raise ValidationError("dimension_not_found", f"account_id {account_id} not found in dim_account")


def validate_null_and_type_mismatches(row: dict[str, Any]) -> None:
    """Validate that required fields are present and have correct types."""
    required_fields = {
        "id": ((str, uuid.UUID), "UUID or string"),
        "account_id": (int, "integer"),
        "symbol": (str, "string"),
        "side": (str, "string"),
        "quantity": (int, "integer"),
        "status": (str, "string"),
        "created_at": (datetime, "datetime"),
    }

    for field, (expected_types, type_name) in required_fields.items():
        if field not in row:
            raise ValidationError("missing_required_field", f"required field '{field}' is missing")
        value = row[field]
        if value is None:
            raise ValidationError("null_required_field", f"required field '{field}' cannot be None")
        if not isinstance(value, expected_types):
            raise ValidationError("type_mismatch", f"field '{field}' should be {type_name}, got {type(value).__name__}")