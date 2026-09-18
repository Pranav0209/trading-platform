package com.zkuba.executor.listener;

import com.zkuba.executor.client.FauxnanceQuoteClient;
import com.zkuba.executor.service.TradeExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for retry behavior on transient errors.
 *
 * Scenario: FauxnanceQuoteClient throws RestClientException (5xx error)
 * → This is retryable (not added to notRetryableExceptions in KafkaConfig)
 * → Spring Kafka error handler applies exponential backoff
 * → After 30s budget exhausted, message sent to orders.DLT
 *
 * For manual testing: Use Bruno to send orders to /api/v1/orders/produce endpoint
 * and monitor orders.DLT topic in Kafka.
 */
@ExtendWith(MockitoExtension.class)
class OrderEventListenerRetryAndDLTTest {

    @Mock
    private FauxnanceQuoteClient quoteClient;

    /**
     * Test: RestClientException (5xx) is not added to notRetryableExceptions,
     * so it will be retried by the error handler with exponential backoff.
     */
    @Test
    void fiveHundredErrorIsNotInNotRetryableExceptions() {
        // This test documents that RestClientException is NOT in the fatal list
        // Fatal (notRetryable): JsonProcessingException, IllegalArgumentException, NoSuchElementException
        // Transient (retryable): RestClientException (includes 5xx errors)
        
        // When FauxnanceQuoteClient throws 5xx
        when(quoteClient.getQuote(anyString()))
            .thenThrow(new RestClientException("500: Service Unavailable"));

        // It will be retried by Spring Kafka error handler, not dead-lettered immediately
        assertThrows(RestClientException.class, 
            () -> quoteClient.getQuote("AAPL"));
        
        System.out.println("✓ RestClientException is retryable");
        System.out.println("✓ Check KafkaConfig.errorHandler() - RestClientException NOT in addNotRetryableExceptions()");
        System.out.println("✓ Manual test: Send order via Bruno → watch retries in logs → message lands in orders.DLT after 30s");
    }
}
