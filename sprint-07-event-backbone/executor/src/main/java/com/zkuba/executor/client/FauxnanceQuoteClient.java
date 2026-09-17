package com.zkuba.executor.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

@Component
public class FauxnanceQuoteClient {

    private final RestClient restClient;

    public FauxnanceQuoteClient(
            @Value("${fauxnance.api.base-url}") String baseUrl,
            @Value("${fauxnance.api.key}") String apiKey) {
        
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Retryable(
            retryFor = {RestClientException.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public QuoteResponse getQuote(String symbol) {
        try {
            return restClient.get()
                    .uri("/quotes/{symbol}", symbol)
                    .retrieve()
                    .body(QuoteResponse.class);
        } catch (Exception e) {
            throw new PricingUnavailableException("Failed to fetch quote for " + symbol, e);
        }
    }

    @Retryable(
            retryFor = {RestClientException.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public BatchQuoteResponse getBatchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return new BatchQuoteResponse(new BatchQuoteResponse.Data(Collections.emptyList()), null);
        }
        String symbolsParam = String.join(",", symbols);
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/quotes")
                            .queryParam("symbols", symbolsParam)
                            .build())
                    .retrieve()
                    .body(BatchQuoteResponse.class);
        } catch (Exception e) {
            throw new PricingUnavailableException("Failed to fetch batch quotes for " + symbolsParam, e);
        }
    }
}
