"""Validation checks for trade data before loading into fact table."""

from __future__ import annotations

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
    order_id: str,
    account_id: int,
    symbol: str,
    side: str,
    quantity: int,
    executed_price: Optional[float],
    status: str,
    created_at: datetime,
    executed_on: Optional[datetime],
) -> dict[str, Any]:
    """Run all validation checks on a trade record.
    
    Checks:
    1. Valid side (BUY or SELL)
    2. Valid status (NEW, FILLED, CANCELLED, REJECTED)
    3. Positive quantity
    4. Positive executed_price (when FILLED)
    5. Computed trade value (quantity * executed_price for FILLED orders)
    
    Args:
        order_id: UUID of the order
        account_id: Account ID
        symbol: Instrument symbol
        side: BUY or SELL
        quantity: Order quantity
        executed_price: Price per unit (nullable for non-FILLED orders)
        status: Order status
        created_at: Order creation timestamp
        executed_on: Execution timestamp
        
    Returns:
        Dictionary with validated and computed fields
        
    Raises:
        ValidationError: If any validation fails
    """
    
    # Validate side
    if side not in ('BUY', 'SELL'):
        raise ValidationError(
            'invalid_side',
            f"side must be BUY or SELL, got '{side}'"
        )
    
    # Validate status
    if status not in ('NEW', 'FILLED', 'CANCELLED', 'REJECTED'):
        raise ValidationError(
            'invalid_status',
            f"status must be NEW, FILLED, CANCELLED, or REJECTED, got '{status}'"
        )
    
    # Validate quantity
    if not isinstance(quantity, int) or quantity <= 0:
        raise ValidationError(
            'invalid_quantity',
            f"quantity must be positive integer, got '{quantity}' ({type(quantity).__name__})"
        )
    
    # Validate executed_price and trade_value for FILLED orders
    trade_value = None
    if status == 'FILLED':
        if executed_price is None:
            raise ValidationError(
                'missing_executed_price',
                f"FILLED order {order_id} has no executed_price"
            )
        
        try:
            price_value = float(executed_price)
        except (TypeError, ValueError):
            raise ValidationError(
                'invalid_executed_price',
                f"executed_price must be numeric, got '{executed_price}' ({type(executed_price).__name__})"
            )
        
        if price_value <= 0:
            raise ValidationError(
                'negative_price',
                f"executed_price must be positive, got {price_value}"
            )
        
        # Compute trade value: quantity * price
        trade_value = Decimal(str(quantity)) * Decimal(str(price_value))
    
    # For non-FILLED orders, executed_price and trade_value should be None
    # This is acceptable - don't fail
    
    return {
        'order_id': order_id,
        'account_id': account_id,
        'symbol': symbol,
        'side': side,
        'quantity': quantity,
        'executed_price': executed_price,
        'status': status,
        'trade_value': trade_value,
        'created_at': created_at,
        'executed_on': executed_on,
    }


def validate_referential_integrity(
    symbol: str,
    account_id: int,
    existing_accounts: set[int],
    existing_symbols: set[str],
) -> None:
    """Validate that referenced dimensions exist.
    
    Args:
        symbol: Instrument symbol
        account_id: Account ID
        existing_accounts: Set of account IDs in DIM_ACCOUNT
        existing_symbols: Set of symbols in DIM_INSTRUMENT
        
    Raises:
        ValidationError: If dimension record is missing
    """
    
    if symbol not in existing_symbols:
        raise ValidationError(
            'dimension_not_found',
            f"symbol '{symbol}' not found in dim_instrument (did you load dimensions?)"
        )
    
    if account_id not in existing_accounts:
        raise ValidationError(
            'dimension_not_found',
            f"account_id {account_id} not found in dim_account (did you load dimensions?)"
        )


def validate_null_and_type_mismatches(row: dict[str, Any]) -> None:
    """Validate that required fields are present and have correct types.
    
    Args:
        row: Dictionary of order fields
        
    Raises:
        ValidationError: If validation fails
    """
    
    required_fields = {
        'id': (str, 'UUID'),
        'account_id': (int, 'integer'),
        'symbol': (str, 'string'),
        'side': (str, 'string'),
        'quantity': (int, 'integer'),
        'status': (str, 'string'),
        'created_at': (datetime, 'datetime'),
    }
    
    for field, (expected_type, type_name) in required_fields.items():
        if field not in row:
            raise ValidationError(
                'missing_required_field',
                f"required field '{field}' is missing"
            )
        
        value = row[field]
        if value is None:
            raise ValidationError(
                'null_required_field',
                f"required field '{field}' cannot be None"
            )
        
        if not isinstance(value, expected_type):
            raise ValidationError(
                'type_mismatch',
                f"field '{field}' should be {type_name}, got {type(value).__name__}"
            )
