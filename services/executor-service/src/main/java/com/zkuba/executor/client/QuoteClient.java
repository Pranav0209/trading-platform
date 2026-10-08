package com.zkuba.executor.client;

import java.util.List;

/**
 * Interface for quote client to enable better testability and mocking.
 * Abstracts the quote retrieval logic from the implementation details.
 */
public interface QuoteClient {

    /**
     * Fetch a single quote for a given symbol
     * @param symbol the stock symbol
     * @return quote response with price data
     * @throws PricingUnavailableException if quote cannot be fetched
     */
    QuoteResponse getQuote(String symbol);

    /**
     * Fetch multiple quotes in a single batch request
     * @param symbols list of stock symbols
     * @return batch quote response with all prices
     * @throws PricingUnavailableException if quotes cannot be fetched
     */
    BatchQuoteResponse getBatchQuotes(List<String> symbols);
}
