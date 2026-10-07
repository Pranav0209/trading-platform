package com.zkuba.trading.service;

import com.zkuba.trading.domain.enums.SignalSource;
import com.zkuba.trading.domain.enums.SignalType;
import com.zkuba.trading.dto.response.SignalResponse;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class SignalEngineTest {

    private final SignalEngine engine = new SignalEngine();

    @Test
    void testInsufficientData() {
        SignalResponse response = engine.generateSignal("ACME", SignalSource.HOLDING, null, Instant.now(), false);
        assertEquals(SignalType.INSUFFICIENT_DATA, response.signal());
        assertTrue(response.reasons().get(0).contains("Not enough historical data"));
    }

    @Test
    void testBullishSignal() {
        IndicatorCalculator.Indicators ind = new IndicatorCalculator.Indicators(
                150.0, 140.0, 130.0, 60.0, 2.0, 1.0, 1.0, 160.0, 140.0, 120.0, 0.05, 0.10, 2000L, 1000.0
        );

        SignalResponse response = engine.generateSignal("ACME", SignalSource.WATCHLIST, ind, Instant.now(), false);
        assertEquals(SignalType.BUY, response.signal());
        assertTrue(response.score() >= 3);
    }

    @Test
    void testBearishSignal() {
        IndicatorCalculator.Indicators ind = new IndicatorCalculator.Indicators(
                120.0, 130.0, 140.0, 40.0, -2.0, -1.0, -1.0, 150.0, 130.0, 110.0, -0.05, -0.10, 2000L, 1000.0
        );

        SignalResponse response = engine.generateSignal("ACME", SignalSource.BOTH, ind, Instant.now(), false);
        assertEquals(SignalType.SELL, response.signal());
        assertTrue(response.score() <= -3);
    }
}
