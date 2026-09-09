package com.zkuba.trading.service;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketDataServiceTest {

    private MarketDataService marketDataService;

    @BeforeEach
    void setUp() {
        marketDataService = new MarketDataService();
    }

    @ParameterizedTest
    @CsvSource({
            "aapl, 234.12",
            "AAPL, 234.12",
            "MSFT, 505.10",
            "NVDA, 171.40",
            "TSLA, 350.00",
            "GOOGL, 178.25",
            "AMZN, 198.60",
            "META, 510.75"
    })
    @DisplayName("getCurrentPrice should return configured price for known symbols regardless of case")
    void getCurrentPrice_KnownSymbols_ReturnsPrice(String symbol, String expectedPriceStr) {
        BigDecimal price = marketDataService.getCurrentPrice(symbol);
        assertThat(price).isEqualTo(new BigDecimal(expectedPriceStr));
    }

    @Test
    @DisplayName("getCurrentPrice should throw IllegalArgumentException for unknown symbols")
    void getCurrentPrice_UnknownSymbol_ThrowsException() {
        assertThatThrownBy(() -> marketDataService.getCurrentPrice("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No price configured for UNKNOWN");
    }
}
