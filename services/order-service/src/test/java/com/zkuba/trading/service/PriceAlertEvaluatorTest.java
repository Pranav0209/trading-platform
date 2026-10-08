package com.zkuba.trading.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.PriceAlert;
import com.zkuba.trading.domain.entity.PriceAlert.AlertState;
import com.zkuba.trading.domain.entity.PriceAlert.Direction;
import com.zkuba.trading.repository.PriceAlertRepository;
import com.zkuba.trading.service.PriceAlertEvaluator.AlertNotificationPayload;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceAlertEvaluatorTest {

    @Mock
    private PriceAlertRepository alertRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PriceAlertService priceAlertService;
    @Mock
    private CurrencyConversionService currencyConversionService;

    // Use a real ObjectMapper since it's just JSON parsing
    private ObjectMapper objectMapper = new ObjectMapper();

    private PriceAlertEvaluator evaluator;

    private PriceAlert alert;
    private Account account;

    @BeforeEach
    void setUp() {
        evaluator = new PriceAlertEvaluator(alertRepository, notificationService, priceAlertService, objectMapper, currencyConversionService);
        
        account = new Account();
        account.setId(1L);
        account.setCurrency("USD");

        alert = new PriceAlert();
        alert.setId(10L);
        alert.setAccount(account);
        alert.setSymbol("AAPL");
        alert.setState(AlertState.ACTIVE);
        alert.setDirection(Direction.ABOVE);
        alert.setThreshold(new BigDecimal("150.00"));
        alert.setCreatedAt(OffsetDateTime.now());
    }

    @Test
    void evaluateAlerts_AboveThreshold_Triggers() throws Exception {
        when(alertRepository.findActiveAlertsForSymbol("AAPL")).thenReturn(List.of(alert));
        when(alertRepository.findById(10L)).thenReturn(Optional.of(alert));

        String json = "{\"symbol\":\"AAPL\",\"price\":151.0,\"currency\":\"USD\"}";
        evaluator.evaluateAlerts(json);

        verify(alertRepository, times(1)).save(any(PriceAlert.class));
        verify(notificationService, timeout(1000).times(1)).sendPriceAlertNotification(any(AlertNotificationPayload.class));
    }

    @Test
    void evaluateAlerts_BelowThreshold_DoesNotTrigger() throws Exception {
        when(alertRepository.findActiveAlertsForSymbol("AAPL")).thenReturn(List.of(alert));

        String json = "{\"symbol\":\"AAPL\",\"price\":149.0,\"currency\":\"USD\"}";
        evaluator.evaluateAlerts(json);

        verify(alertRepository, never()).findById(anyLong());
        verify(alertRepository, never()).save(any(PriceAlert.class));
        verify(notificationService, never()).sendPriceAlertNotification(any(AlertNotificationPayload.class));
    }

    @Test
    void evaluateAlerts_WithCurrencyConversion() throws Exception {
        account.setCurrency("EUR");
        alert.setThreshold(new BigDecimal("140.00"));

        when(alertRepository.findActiveAlertsForSymbol("AAPL")).thenReturn(List.of(alert));
        when(alertRepository.findById(10L)).thenReturn(Optional.of(alert));
        when(currencyConversionService.getConversionRate("USD", "EUR")).thenReturn(new BigDecimal("0.95"));

        // 150 * 0.95 = 142.5 >= 140
        String json = "{\"symbol\":\"AAPL\",\"price\":150.0,\"currency\":\"USD\"}";
        evaluator.evaluateAlerts(json);

        verify(alertRepository, times(1)).save(any(PriceAlert.class));
        verify(notificationService, timeout(1000).times(1)).sendPriceAlertNotification(any(AlertNotificationPayload.class));
    }
    
    @Test
    void evaluateAlerts_DirectionBelow_Triggers() throws Exception {
        alert.setDirection(Direction.BELOW);
        when(alertRepository.findActiveAlertsForSymbol("AAPL")).thenReturn(List.of(alert));
        when(alertRepository.findById(10L)).thenReturn(Optional.of(alert));

        String json = "{\"symbol\":\"AAPL\",\"price\":149.0,\"currency\":\"USD\"}";
        evaluator.evaluateAlerts(json);

        verify(alertRepository, times(1)).save(any(PriceAlert.class));
        verify(notificationService, timeout(1000).times(1)).sendPriceAlertNotification(any(AlertNotificationPayload.class));
    }

    @Test
    void evaluateAlerts_MalformedMessage() {
        String json = "{\"invalid\":\"data\"}";
        evaluator.evaluateAlerts(json);

        verify(alertRepository, never()).findActiveAlertsForSymbol(anyString());
    }
}
