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
public class FauxnanceQuoteClient implements QuoteClient {

    private final RestClient restClient;

    // pulls url and api key from app properies or env
    public FauxnanceQuoteClient(
            @Value("${fauxnance.api.base-url}") String baseUrl,
            @Value("${fauxnance.api.key}") String apiKey) {

        // builds reusable HTTP client
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // fetching single quote, get request to api and converts json to QuoteResponse
    // will wait 1sec before retry, then 2, then 4, exponential backoff
    @Retryable(retryFor = {
            RestClientException.class }, maxAttempts = 5, backoff = @Backoff(delay = 1000, multiplier = 2.0))
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

    // fetching multiple quotes in single request, market poller
    @Retryable(retryFor = {
            RestClientException.class }, maxAttempts = 5, backoff = @Backoff(delay = 1000, multiplier = 2.0))
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
