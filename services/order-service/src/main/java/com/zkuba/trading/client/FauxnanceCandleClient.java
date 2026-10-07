package com.zkuba.trading.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class FauxnanceCandleClient {

    private final RestClient restClient;

    public FauxnanceCandleClient(
            @Value("${fauxnance.api.base-url}") String baseUrl,
            @Value("${fauxnance.api.key}") String apiKey) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Retryable(retryFor = {
            RestClientException.class }, maxAttempts = 5, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public CandleResponse getCandles(String symbol) {
        try {
            return restClient.get()
                    .uri("/candles/{symbol}", symbol)
                    .retrieve()
                    .body(CandleResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch candles for " + symbol, e);
        }
    }
}
