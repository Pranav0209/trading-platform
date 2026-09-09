package com.zkuba.trading.service;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MarketDataService {
    private final Map<String, BigDecimal> demo = Map.of(
            "AAPL", new BigDecimal("234.12"),
            "MSFT", new BigDecimal("505.10"),
            "NVDA", new BigDecimal("171.40"),
            "TSLA", new BigDecimal("350.00"),
            "GOOGL", new BigDecimal("178.25"),
            "AMZN", new BigDecimal("198.60"),
            "META", new BigDecimal("510.75"));

    public BigDecimal getCurrentPrice(String symbol) {
        BigDecimal p = demo.get(symbol.toUpperCase());
        if (p == null) throw new IllegalArgumentException("No price configured for " + symbol);
        return p;
    }
}
