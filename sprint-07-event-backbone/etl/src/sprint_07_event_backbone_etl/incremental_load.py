"""Compatibility module: re-exports IncrementalTradePipeline as IncrementalTradeLoader."""

from .pipeline import IncrementalTradePipeline, IncrementalTradeLoader
from .transform import derive_exchange

__all__ = ["IncrementalTradePipeline", "IncrementalTradeLoader", "derive_exchange"]
