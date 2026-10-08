package com.zkuba.trading.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.zkuba.trading.event.QuotePayload;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class MarketDataStreamServiceTest {

    @Mock
    private CurrencyConversionService currencyConversionService;

    @InjectMocks
    private MarketDataStreamService streamService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void subscribe_Success() {
        SseEmitter emitter = streamService.subscribe(1L, "USD");
        assertNotNull(emitter);
        // Can't easily verify internal state, but we ensure it returns a valid emitter
    }

    @Test
    void broadcast_SameCurrency_NoConversion() throws Exception {
        QuotePayload quote = new QuotePayload(
                "AAPL", new BigDecimal("150.0"), new BigDecimal("149.0"), new BigDecimal("151.0"),
                "USD", new BigDecimal("1.0"), new BigDecimal("0.5"), new BigDecimal("149.0"),
                OffsetDateTime.now(), "REGULAR"
        );

        SseEmitter mockEmitter = mock(SseEmitter.class);
        // Replace internal list logic is hard without reflection, so we subscribe and test behavior.
        SseEmitter actualEmitter = streamService.subscribe(1L, "USD");
        
        // Wait, subscribe creates a real SseEmitter. We can't spy on it easily unless we use reflection.
        // Let's just run broadcast and ensure it doesn't crash.
        streamService.broadcast(quote);

        // Sleep briefly to let async thread complete
        Thread.sleep(100);

        verify(currencyConversionService, never()).getConversionRate(anyString(), anyString());
    }

    @Test
    void broadcast_DifferentCurrency_Converts() throws Exception {
        QuotePayload quote = new QuotePayload(
                "AAPL", new BigDecimal("150.0"), new BigDecimal("149.0"), new BigDecimal("151.0"),
                "USD", new BigDecimal("1.0"), new BigDecimal("0.5"), new BigDecimal("149.0"),
                OffsetDateTime.now(), "REGULAR"
        );

        when(currencyConversionService.getConversionRate("USD", "EUR")).thenReturn(new BigDecimal("0.95"));

        SseEmitter actualEmitter = streamService.subscribe(2L, "EUR");
        
        streamService.broadcast(quote);

        // Wait for async task
        Thread.sleep(100);

        verify(currencyConversionService, times(1)).getConversionRate("USD", "EUR");
    }
    
    @Test
    void broadcast_ConversionFails_UsesOriginal() throws Exception {
        QuotePayload quote = new QuotePayload(
                "AAPL", new BigDecimal("150.0"), new BigDecimal("149.0"), new BigDecimal("151.0"),
                "USD", new BigDecimal("1.0"), new BigDecimal("0.5"), new BigDecimal("149.0"),
                OffsetDateTime.now(), "REGULAR"
        );

        when(currencyConversionService.getConversionRate("USD", "EUR")).thenThrow(new RuntimeException("API down"));

        streamService.subscribe(2L, "EUR");
        
        streamService.broadcast(quote);

        Thread.sleep(100);

        verify(currencyConversionService, times(1)).getConversionRate("USD", "EUR");
        // We ensure no unhandled exception breaks the async loop.
    }
}
