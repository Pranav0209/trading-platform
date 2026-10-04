package com.zkuba.executor.client;

import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;

@Component
public class CurrencyConversionClient {

    private final RestClient restClient;

    public CurrencyConversionClient(@Value("${frankfurter.api.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Retryable(retryFor = {
            RestClientException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public BigDecimal getConversionRate(String base, String quote) {
        if (base == null || quote == null) {
            return BigDecimal.ONE;
        }
        if (base.equalsIgnoreCase(quote)) {
            return BigDecimal.ONE;
        }

        try {
            FrankfurterResponse response = restClient.get()
                    .uri("/v2/rate/{base}/{quote}", base.toLowerCase(), quote.toLowerCase())
                    .retrieve()
                    .body(FrankfurterResponse.class);
            return response != null ? response.rate() : BigDecimal.ONE;
        } catch (Exception e) {
            throw new PricingUnavailableException("Failed to fetch conversion rate from " + base + " to " + quote, e);
        }
    }

    public record FrankfurterResponse(String date, String base, String quote, BigDecimal rate) {}
}
