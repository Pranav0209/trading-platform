package com.zkuba.trading.service;

import com.zkuba.trading.client.CandleResponse.Candle;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IndicatorCalculatorTest {

    private final IndicatorCalculator calculator = new IndicatorCalculator();

    @Test
    void testInsufficientData() {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            candles.add(new Candle("2023-01-01", 10.0, 10.0, 10.0, 10.0, 10.0, 100L, false));
        }
        IndicatorCalculator.Indicators result = calculator.calculate(candles);
        assertNull(result);
    }

    @Test
    void testCalculateIndicators() {
        List<Candle> candles = new ArrayList<>();
        // Generate 60 candles with closing prices increasing from 100 to 159
        for (int i = 0; i < 60; i++) {
            double price = 100 + i;
            candles.add(new Candle("2023-01-01", price, price + 1, price - 1, price, price, 1000L, false));
        }

        IndicatorCalculator.Indicators result = calculator.calculate(candles);

        assertNotNull(result);
        assertEquals(159.0, result.currentPrice());
        
        // SMA20 of 140 to 159 -> sum is (140+159)*20/2 = 2990 -> avg is 149.5
        assertEquals(149.5, result.sma20());
        
        // SMA50 of 110 to 159 -> sum is (110+159)*50/2 = 6725 -> avg is 134.5
        assertEquals(134.5, result.sma50());

        // RSI should be 100 since all gains
        assertEquals(100.0, result.rsi14(), 0.1);

        // Volume
        assertEquals(1000L, result.currentVolume());
        assertEquals(1000.0, result.averageVolume20d(), 0.1);
        
        // Momentum 5 day
        // current is 159, past is 6 days ago (i.e. index 54) which is 154
        double expectedReturn5d = (159.0 - 154.0) / 154.0;
        assertEquals(expectedReturn5d, result.return5d(), 0.001);
    }
}
