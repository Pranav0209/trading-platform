package com.zkuba.trading.service;

import com.zkuba.trading.exception.MarketDataException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@DisplayName("MarketDataService Tests")
class MarketDataServiceTest {

    private MarketDataService marketDataService;

    @BeforeEach
    void setUp() {
        marketDataService = new MarketDataServiceImpl();
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
            "META, 510.75",
            "INFY.NS, 1580.25"
    })
    @DisplayName("getCurrentPrice should return configured price for known symbols regardless of case")
    void getCurrentPrice_KnownSymbols_ReturnsPrice(String symbol, String expectedPriceStr) {
        BigDecimal price = marketDataService.getCurrentPrice(symbol);
        assertThat(price).isEqualByComparingTo(expectedPriceStr);
    }

    @Test
    @DisplayName("getCurrentPrice should throw MarketDataException for unknown symbols")
    void getCurrentPrice_UnknownSymbol_ThrowsMarketDataException() {
        assertThatThrownBy(() -> marketDataService.getCurrentPrice("UNKNOWN"))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("Market data not available for: UNKNOWN");
    }

    @Test
    @DisplayName("getCurrentPrice should handle mixed case symbols")
    void getCurrentPrice_MixedCaseSymbol_ReturnsPrice() {
        BigDecimal price = marketDataService.getCurrentPrice("MsFt");
        assertThat(price).isEqualByComparingTo("505.10");
    }

    @Test
    @DisplayName("getCurrentPrice should throw exception for empty string")
    void getCurrentPrice_EmptyString_ThrowsMarketDataException() {
        assertThatThrownBy(() -> marketDataService.getCurrentPrice(""))
                .isInstanceOf(MarketDataException.class);
    }

    @Test
    @DisplayName("getCurrentPrice should throw exception for whitespace-only string")
    void getCurrentPrice_WhitespaceOnly_ThrowsMarketDataException() {
        assertThatThrownBy(() -> marketDataService.getCurrentPrice("   "))
                .isInstanceOf(MarketDataException.class);
    }
}
