package com.zkuba.trading.dto.response;

public record PositionResponse(String symbol, int quantity, java.math.BigDecimal averageCost) {}
