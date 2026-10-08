package com.zkuba.trading.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.zkuba.trading.service.CurrencyConversionService.FrankfurterResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;
import org.springframework.web.client.RestClient.ResponseSpec;

@ExtendWith(MockitoExtension.class)
class CurrencyConversionServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private ResponseSpec responseSpec;

    private CurrencyConversionService currencyConversionService;

    @BeforeEach
    void setUp() {
        currencyConversionService = new CurrencyConversionService(restClient, "https://api.frankfurter.dev");
    }

    @Test
    void getConversionRate_SameCurrency() {
        BigDecimal rate = currencyConversionService.getConversionRate("USD", "usd");
        assertEquals(BigDecimal.ONE, rate);
    }

    @Test
    void getConversionRate_Success() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), eq("usd"), eq("eur"))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        
        FrankfurterResponse response = new FrankfurterResponse("2026-10-08", "USD", "EUR", new BigDecimal("0.95"));
        when(responseSpec.body(FrankfurterResponse.class)).thenReturn(response);

        BigDecimal rate = currencyConversionService.getConversionRate("USD", "EUR");
        assertEquals(new BigDecimal("0.95"), rate);
        
        // Test caching
        BigDecimal cachedRate = currencyConversionService.getConversionRate("USD", "EUR");
        assertEquals(new BigDecimal("0.95"), cachedRate);
        verify(restClient, times(1)).get(); // Should not call again
    }

    @Test
    void getConversionRate_ApiReturnsNull() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), eq("usd"), eq("gbp"))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        
        when(responseSpec.body(FrankfurterResponse.class)).thenReturn(null);

        BigDecimal rate = currencyConversionService.getConversionRate("USD", "GBP");
        assertEquals(BigDecimal.ONE, rate);
    }

    @Test
    void getConversionRate_ApiError() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), eq("usd"), eq("jpy"))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenThrow(new RuntimeException("API down"));

        BigDecimal rate = currencyConversionService.getConversionRate("USD", "JPY");
        assertEquals(BigDecimal.ONE, rate);
    }
}
