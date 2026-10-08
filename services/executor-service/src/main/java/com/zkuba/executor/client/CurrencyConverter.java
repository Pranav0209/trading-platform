package com.zkuba.executor.client;

import java.math.BigDecimal;

/**
 * Interface for currency conversion operations.
 * Enables interface-based mocking and abstraction from concrete implementation details.
 */
public interface CurrencyConverter {

    /**
     * Get the exchange rate from base currency to quote currency.
     *
     * @param base the base currency code (e.g., "USD")
     * @param quote the quote currency code (e.g., "EUR")
     * @return the exchange rate
     * @throws PricingUnavailableException if the conversion rate cannot be fetched
     */
    BigDecimal getConversionRate(String base, String quote) throws PricingUnavailableException;
}
