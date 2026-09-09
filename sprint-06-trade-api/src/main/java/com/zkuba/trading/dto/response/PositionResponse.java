package com.zkuba.trading.dto.response;

public record PositionResponse(Long accountId, String symbol, int quantity, java.math.BigDecimal averageCost) {}
