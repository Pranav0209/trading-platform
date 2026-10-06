package com.zkuba.executor.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class FauxnanceQuoteClientTest {

    private RestClient restClient;
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private RestClient.RequestHeadersSpec requestHeadersSpec;
    private RestClient.ResponseSpec responseSpec;

    private FauxnanceQuoteClient client;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        client = new FauxnanceQuoteClient(
                "http://localhost:9999",
                "test-api-key"
        );

        injectRestClient(client, restClient);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
    }

    // ---------------------------------------------------------
    // getQuote()
    // ---------------------------------------------------------

    @Test
    void getQuote_success_returnsQuoteResponse() {

        QuoteResponse expected = createQuoteResponse("AAPL");

        doReturn(requestHeadersSpec)
                .when(requestHeadersUriSpec)
                .uri(eq("/quotes/{symbol}"), eq("AAPL"));

        when(requestHeadersSpec.retrieve())
                .thenReturn(responseSpec);

        when(responseSpec.body(QuoteResponse.class))
                .thenReturn(expected);

        QuoteResponse result = client.getQuote("AAPL");

        assertNotNull(result);
        assertEquals("AAPL", result.data().symbol());
        assertEquals(
                new BigDecimal("150.00"),
                result.data().price()
        );

        verify(restClient).get();
        verify(requestHeadersUriSpec)
                .uri("/quotes/{symbol}", "AAPL");
        verify(requestHeadersSpec).retrieve();
        verify(responseSpec).body(QuoteResponse.class);
    }

    @Test
    void getQuote_restClientFailure_throwsPricingUnavailableException() {

        doReturn(requestHeadersSpec)
                .when(requestHeadersUriSpec)
                .uri(eq("/quotes/{symbol}"), eq("AAPL"));

        when(requestHeadersSpec.retrieve())
                .thenThrow(new RuntimeException("Connection failed"));

        PricingUnavailableException exception =
                assertThrows(
                        PricingUnavailableException.class,
                        () -> client.getQuote("AAPL")
                );

        assertTrue(
                exception.getMessage()
                        .contains("Failed to fetch quote for AAPL")
        );

        assertNotNull(exception.getCause());
        assertEquals(
                "Connection failed",
                exception.getCause().getMessage()
        );
    }

    @Test
    void getQuote_usesRequestedSymbol() {

        QuoteResponse expected = createQuoteResponse("MSFT");

        doReturn(requestHeadersSpec)
                .when(requestHeadersUriSpec)
                .uri(eq("/quotes/{symbol}"), eq("MSFT"));

        when(requestHeadersSpec.retrieve())
                .thenReturn(responseSpec);

        when(responseSpec.body(QuoteResponse.class))
                .thenReturn(expected);

        QuoteResponse result = client.getQuote("MSFT");

        assertNotNull(result);
        assertEquals("MSFT", result.data().symbol());

        verify(requestHeadersUriSpec)
                .uri("/quotes/{symbol}", "MSFT");
    }

    // ---------------------------------------------------------
    // getBatchQuotes()
    // ---------------------------------------------------------

    @Test
    void getBatchQuotes_nullSymbols_returnsEmptyResult() {

        BatchQuoteResponse result =
                client.getBatchQuotes(null);

        assertNotNull(result);
        assertNotNull(result.data());
        assertNotNull(result.data().quotes());
        assertTrue(result.data().quotes().isEmpty());

        verifyNoInteractions(restClient);
    }

    @Test
    void getBatchQuotes_emptySymbols_returnsEmptyResult() {

        BatchQuoteResponse result =
                client.getBatchQuotes(List.of());

        assertNotNull(result);
        assertNotNull(result.data());
        assertNotNull(result.data().quotes());
        assertTrue(result.data().quotes().isEmpty());

        verifyNoInteractions(restClient);
    }

    @Test
    void getBatchQuotes_success_returnsBatchResponse() {

        QuoteResponse.Data appleQuote =
                new QuoteResponse.Data(
                        "AAPL",
                        new BigDecimal("150.00"),
                        new BigDecimal("149.50"),
                        new BigDecimal("150.50"),
                        "USD",
                        null,
                        null,
                        null,
                        OffsetDateTime.now(),
                        "OPEN"
                );

        BatchQuoteResponse.QuoteItem apple =
                new BatchQuoteResponse.QuoteItem(
                        "AAPL",
                        "Fauxnance",
                        false,
                        appleQuote
                );

        BatchQuoteResponse expected =
                new BatchQuoteResponse(
                        new BatchQuoteResponse.Data(
                                List.of(apple)
                        ),
                        null
                );

        doReturn(requestHeadersSpec)
                .when(requestHeadersUriSpec)
                .uri(any(java.util.function.Function.class));

        when(requestHeadersSpec.retrieve())
                .thenReturn(responseSpec);

        when(responseSpec.body(BatchQuoteResponse.class))
                .thenReturn(expected);

        BatchQuoteResponse result =
                client.getBatchQuotes(List.of("AAPL"));

        assertNotNull(result);
        assertNotNull(result.data());
        assertEquals(1, result.data().quotes().size());

        BatchQuoteResponse.QuoteItem quote =
                result.data().quotes().get(0);

        assertEquals("AAPL", quote.symbol());
        assertEquals("Fauxnance", quote.source());
        assertFalse(quote.stale());
        assertEquals(
                new BigDecimal("150.00"),
                quote.quote().price()
        );

        verify(restClient).get();
        verify(requestHeadersUriSpec)
                .uri(any(java.util.function.Function.class));
        verify(requestHeadersSpec).retrieve();
        verify(responseSpec)
                .body(BatchQuoteResponse.class);
    }

    @Test
    void getBatchQuotes_restClientFailure_throwsPricingUnavailableException() {

        doReturn(requestHeadersSpec)
                .when(requestHeadersUriSpec)
                .uri(any(java.util.function.Function.class));

        when(requestHeadersSpec.retrieve())
                .thenThrow(new RuntimeException("Connection failed"));

        PricingUnavailableException exception =
                assertThrows(
                        PricingUnavailableException.class,
                        () -> client.getBatchQuotes(
                                List.of("AAPL", "MSFT")
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains(
                                "Failed to fetch batch quotes for AAPL,MSFT"
                        )
        );

        assertNotNull(exception.getCause());
        assertEquals(
                "Connection failed",
                exception.getCause().getMessage()
        );
    }

    @Test
    void getBatchQuotes_passesSymbolsToRequest() {

        BatchQuoteResponse expected =
                new BatchQuoteResponse(
                        new BatchQuoteResponse.Data(
                                List.of()
                        ),
                        null
                );

        doReturn(requestHeadersSpec)
                .when(requestHeadersUriSpec)
                .uri(any(java.util.function.Function.class));

        when(requestHeadersSpec.retrieve())
                .thenReturn(responseSpec);

        when(responseSpec.body(BatchQuoteResponse.class))
                .thenReturn(expected);

        BatchQuoteResponse result =
                client.getBatchQuotes(
                        List.of("AAPL", "MSFT", "GOOG")
                );

        assertNotNull(result);

        verify(requestHeadersUriSpec)
                .uri(any(java.util.function.Function.class));
    }

    // ---------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------

    private QuoteResponse createQuoteResponse(String symbol) {

        QuoteResponse.Data data =
                new QuoteResponse.Data(
                        symbol,
                        new BigDecimal("150.00"),
                        new BigDecimal("149.50"),
                        new BigDecimal("150.50"),
                        "USD",
                        null,
                        null,
                        null,
                        OffsetDateTime.now(),
                        "OPEN"
                );

        QuoteResponse.Meta meta =
                new QuoteResponse.Meta(
                        OffsetDateTime.now(),
                        null,
                        symbol,
                        "Fauxnance",
                        false,
                        null
                );

        return new QuoteResponse(data, meta);
    }

    private static void injectRestClient(
            FauxnanceQuoteClient client,
            RestClient restClient) {

        try {
            var field =
                    FauxnanceQuoteClient.class
                            .getDeclaredField("restClient");

            field.setAccessible(true);
            field.set(client, restClient);

        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "Unable to inject mocked RestClient",
                    e
            );
        }
    }
}