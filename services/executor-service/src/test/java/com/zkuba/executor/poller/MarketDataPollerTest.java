package com.zkuba.executor.poller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.executor.client.BatchQuoteResponse;
import com.zkuba.executor.client.FauxnanceQuoteClient;
import com.zkuba.executor.client.QuoteResponse;
import com.zkuba.executor.repository.PositionRepository;
import com.zkuba.executor.client.CurrencyConversionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketDataPollerTest {

    private FauxnanceQuoteClient quoteClient;
    private CurrencyConversionClient conversionClient;
    private PositionRepository positionRepository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private MarketDataPoller poller;

    @BeforeEach
    void setUp() {
        quoteClient = mock(FauxnanceQuoteClient.class);
        conversionClient = mock(CurrencyConversionClient.class);
        positionRepository = mock(PositionRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();
        poller = new MarketDataPoller(quoteClient, conversionClient, positionRepository, kafkaTemplate, objectMapper);
    }



    @Test
    void pollMarketData_batchesUpTo25Symbols() {
        List<String> symbols = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> "SYM" + i)
                .collect(Collectors.toList());

        when(positionRepository.findUniqueSymbolsOfInterest()).thenReturn(symbols);

        // Mock empty responses to just verify the batching logic
        when(quoteClient.getBatchQuotes(any())).thenReturn(
                new BatchQuoteResponse(new BatchQuoteResponse.Data(List.of()), null)
        );

        poller.pollMarketData();

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(quoteClient, times(2)).getBatchQuotes(captor.capture());

        List<List<String>> batches = captor.getAllValues();
        assertEquals(2, batches.size());
        assertEquals(25, batches.get(0).size());
        assertEquals(5, batches.get(1).size());
    }

    @Test
    void pollMarketData_publishesOneMessagePerSymbol() {
        when(positionRepository.findUniqueSymbolsOfInterest()).thenReturn(List.of("AAPL", "MSFT"));

        QuoteResponse.Data aaplQuote = new QuoteResponse.Data("AAPL", null, null, null, null, null, null, null, null, null);
        QuoteResponse.Data msftQuote = new QuoteResponse.Data("MSFT", null, null, null, null, null, null, null, null, null);

        BatchQuoteResponse.QuoteItem aaplItem = new BatchQuoteResponse.QuoteItem("AAPL", "mock", false, aaplQuote);
        BatchQuoteResponse.QuoteItem msftItem = new BatchQuoteResponse.QuoteItem("MSFT", "mock", false, msftQuote);

        BatchQuoteResponse response = new BatchQuoteResponse(
                new BatchQuoteResponse.Data(List.of(aaplItem, msftItem)), null);

        when(quoteClient.getBatchQuotes(any())).thenReturn(response);

        poller.pollMarketData();

        verify(kafkaTemplate, times(1)).send(eq("market-data"), eq("AAPL"), anyString());
        verify(kafkaTemplate, times(1)).send(eq("market-data"), eq("MSFT"), anyString());
    }
}
