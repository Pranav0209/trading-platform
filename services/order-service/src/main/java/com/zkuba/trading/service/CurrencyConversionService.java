package com.zkuba.trading.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CurrencyConversionService {
    private static final Logger log = LoggerFactory.getLogger(CurrencyConversionService.class);
    
    private final RestClient restClient;
    private final String frankfurterApiUrl;
    
    // Simple cache for conversion rates (in production, use Redis or similar)
    private final Map<String, BigDecimal> conversionRateCache = new ConcurrentHashMap<>();

    public CurrencyConversionService(RestClient restClient, 
                                     @Value("${frankfurter.api.base-url:https://api.frankfurter.dev}") String frankfurterApiUrl) {
        this.restClient = restClient;
        this.frankfurterApiUrl = frankfurterApiUrl;
    }

    /**
     * Get conversion rate from one currency to another
     * Uses Frankfurter API with caching
     * Example: getConversionRate("USD", "INR") returns 83.5
     */
    public BigDecimal getConversionRate(String base, String quote) {
        // Same currency - no conversion needed
        if (base == null || quote == null || base.equalsIgnoreCase(quote)) {
            return BigDecimal.ONE;
        }

        String cacheKey = base.toUpperCase() + "_" + quote.toUpperCase();
        
        // Check cache first
        if (conversionRateCache.containsKey(cacheKey)) {
            return conversionRateCache.get(cacheKey);
        }

        try {
            // Call Frankfurter API to get conversion rate
            String url = frankfurterApiUrl + "/v2/rate/{base}/{quote}";
            FrankfurterResponse response = restClient.get()
                    .uri(url, base.toLowerCase(), quote.toLowerCase())
                    .retrieve()
                    .body(FrankfurterResponse.class);

            if (response != null && response.rate() != null) {
                conversionRateCache.put(cacheKey, response.rate());
                return response.rate();
            }

            log.warn("No rate found in Frankfurter response for {}/{}", base, quote);
            return BigDecimal.ONE;

        } catch (Exception e) {
            log.warn("Failed to get conversion rate from {} to {}. Using rate of 1.0.", base, quote, e);
            // Fallback to 1.0 if conversion fails (like executor-service does)
            return BigDecimal.ONE;
        }
    }

    /**
     * Record returned by Frankfurter API (same as executor-service)
     */
    public record FrankfurterResponse(String date, String base, String quote, BigDecimal rate) {}
}
