package com.zkuba.trading.dto.response;

public record HoldingResponse(String symbol, int quantity, java.math.BigDecimal averageCost) {}
