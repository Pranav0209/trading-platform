package com.zkuba.trading.dto.response;

public record PortfolioResponse(Long accountId, String symbol, int quantity, java.math.BigDecimal averageCost) {}
