package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Instrument;
import com.zkuba.trading.domain.entity.PriceAlert;
import com.zkuba.trading.domain.entity.PriceAlert.AlertState;
import com.zkuba.trading.domain.entity.PriceAlert.Direction;
import com.zkuba.trading.dto.request.CreateAlertRequest;
import com.zkuba.trading.dto.response.AlertResponse;
import com.zkuba.trading.exception.AccountNotFoundException;
import com.zkuba.trading.exception.AlertNotFoundException;
import com.zkuba.trading.exception.AlertValidationException;
import com.zkuba.trading.exception.AlertLimitExceededException;
import com.zkuba.trading.exception.InstrumentNotFoundException;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.InstrumentRepository;
import com.zkuba.trading.repository.PriceAlertRepository;
import com.zkuba.trading.service.impl.PriceAlertServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PriceAlertServiceTest {

    @Mock
    private PriceAlertRepository alertRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    private PriceAlertService alertService;

    private Account testAccount;
    private Instrument testInstrument;
    private PriceAlert testAlert;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        alertService = new PriceAlertServiceImpl(alertRepository, accountRepository, instrumentRepository);

        // Setup test data
        testAccount = new Account();
        testAccount.setId(1L);

        testInstrument = new Instrument();
        testInstrument.setSymbol("AAPL");
        testInstrument.setDisplayName("Apple Inc.");
        testInstrument.setTradable(true);

        testAlert = new PriceAlert(testAccount, "AAPL", Direction.ABOVE, new BigDecimal("150.00"));
        testAlert.setId(1L);
        testAlert.setCreatedAt(OffsetDateTime.now());
    }

    // ============== CREATE ALERT TESTS ==============

    @Test
    @DisplayName("createAlert should create alert when all validations pass")
    void testCreateAlertSuccess() {
        CreateAlertRequest request = new CreateAlertRequest("AAPL", "ABOVE", new BigDecimal("150.00"));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(testInstrument));
        when(alertRepository.countActiveAlertsForAccount(1L)).thenReturn(5L);
        when(alertRepository.save(any(PriceAlert.class))).thenReturn(testAlert);

        AlertResponse response = alertService.createAlert(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.direction()).isEqualTo("ABOVE");
        verify(alertRepository).save(any(PriceAlert.class));
    }

    @Test
    @DisplayName("createAlert should normalize symbol to uppercase")
    void testCreateAlertNormalizesSymbol() {
        CreateAlertRequest request = new CreateAlertRequest("aapl", "ABOVE", new BigDecimal("150.00"));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(testInstrument));
        when(alertRepository.countActiveAlertsForAccount(1L)).thenReturn(0L);
        when(alertRepository.save(any(PriceAlert.class))).thenReturn(testAlert);

        alertService.createAlert(1L, request);

        verify(instrumentRepository).findById("AAPL");
    }

    @Test
    @DisplayName("createAlert should throw AccountNotFoundException when account not found")
    void testCreateAlertAccountNotFound() {
        CreateAlertRequest request = new CreateAlertRequest("AAPL", "ABOVE", new BigDecimal("150.00"));

        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.createAlert(1L, request))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("createAlert should throw InstrumentNotFoundException when instrument not found")
    void testCreateAlertInstrumentNotFound() {
        CreateAlertRequest request = new CreateAlertRequest("INVALID", "ABOVE", new BigDecimal("150.00"));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(instrumentRepository.findById("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.createAlert(1L, request))
                .isInstanceOf(InstrumentNotFoundException.class);
    }

    @Test
    @DisplayName("createAlert should throw when instrument is not tradable")
    void testCreateAlertInstrumentNotTradable() {
        testInstrument.setTradable(false);
        CreateAlertRequest request = new CreateAlertRequest("AAPL", "ABOVE", new BigDecimal("150.00"));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(testInstrument));

        assertThatThrownBy(() -> alertService.createAlert(1L, request))
                .isInstanceOf(InstrumentNotFoundException.class);
    }

    @Test
    @DisplayName("createAlert should throw AlertValidationException when threshold is zero")
    void testCreateAlertThresholdZero() {
        CreateAlertRequest request = new CreateAlertRequest("AAPL", "ABOVE", BigDecimal.ZERO);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(testInstrument));

        assertThatThrownBy(() -> alertService.createAlert(1L, request))
                .isInstanceOf(AlertValidationException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("createAlert should throw AlertValidationException when threshold is negative")
    void testCreateAlertThresholdNegative() {
        CreateAlertRequest request = new CreateAlertRequest("AAPL", "ABOVE", new BigDecimal("-10.00"));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(testInstrument));

        assertThatThrownBy(() -> alertService.createAlert(1L, request))
                .isInstanceOf(AlertValidationException.class);
    }

    @Test
    @DisplayName("createAlert should throw when direction is invalid")
    void testCreateAlertInvalidDirection() {
        CreateAlertRequest request = new CreateAlertRequest("AAPL", "SIDEWAYS", new BigDecimal("150.00"));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(testInstrument));

        assertThatThrownBy(() -> alertService.createAlert(1L, request))
                .isInstanceOf(AlertValidationException.class)
                .hasMessageContaining("ABOVE or BELOW");
    }

    @Test
    @DisplayName("createAlert should throw AlertLimitExceededException when limit reached")
    void testCreateAlertLimitExceeded() {
        CreateAlertRequest request = new CreateAlertRequest("AAPL", "ABOVE", new BigDecimal("150.00"));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(testInstrument));
        when(alertRepository.countActiveAlertsForAccount(1L)).thenReturn(100L);

        assertThatThrownBy(() -> alertService.createAlert(1L, request))
                .isInstanceOf(AlertLimitExceededException.class);
    }

    // ============== GET ALERTS TESTS ==============

    @Test
    @DisplayName("getAlerts should return all alerts for account")
    void testGetAlertsNoFilter() {
        List<PriceAlert> alerts = List.of(testAlert);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(alertRepository.findByAccountId(1L)).thenReturn(alerts);
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(testInstrument));

        List<AlertResponse> response = alertService.getAlerts(1L, null);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).symbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("getAlerts should filter by state")
    void testGetAlertsWithStateFilter() {
        List<PriceAlert> alerts = List.of(testAlert);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(alertRepository.findByAccountIdAndState(1L, AlertState.ACTIVE)).thenReturn(alerts);
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(testInstrument));

        List<AlertResponse> response = alertService.getAlerts(1L, AlertState.ACTIVE);

        assertThat(response).hasSize(1);
        verify(alertRepository).findByAccountIdAndState(1L, AlertState.ACTIVE);
    }

    @Test
    @DisplayName("getAlerts should throw AccountNotFoundException when account not found")
    void testGetAlertsAccountNotFound() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.getAlerts(1L, null))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ============== GET ALERT TESTS ==============

    @Test
    @DisplayName("getAlert should return specific alert")
    void testGetAlertSuccess() {
        when(alertRepository.findByIdAndAccountId(1L, 1L)).thenReturn(Optional.of(testAlert));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(testInstrument));

        AlertResponse response = alertService.getAlert(1L, 1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.symbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("getAlert should throw AlertNotFoundException when not found")
    void testGetAlertNotFound() {
        when(alertRepository.findByIdAndAccountId(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.getAlert(1L, 1L))
                .isInstanceOf(AlertNotFoundException.class);
    }

    // ============== DELETE ALERT TESTS ==============

    @Test
    @DisplayName("deleteAlert should delete alert when it belongs to account")
    void testDeleteAlertSuccess() {
        when(alertRepository.findByIdAndAccountId(1L, 1L)).thenReturn(Optional.of(testAlert));

        alertService.deleteAlert(1L, 1L);

        verify(alertRepository).delete(testAlert);
    }

    @Test
    @DisplayName("deleteAlert should throw AlertNotFoundException when not found")
    void testDeleteAlertNotFound() {
        when(alertRepository.findByIdAndAccountId(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.deleteAlert(1L, 1L))
                .isInstanceOf(AlertNotFoundException.class);
    }

    // ============== RESET ALERT TESTS ==============

    @Test
    @DisplayName("resetAlert should transition alert from TRIGGERED to ACTIVE")
    void testResetAlertSuccess() {
        testAlert.setState(AlertState.TRIGGERED);

        when(alertRepository.findByIdAndAccountId(1L, 1L)).thenReturn(Optional.of(testAlert));
        when(alertRepository.save(any(PriceAlert.class))).thenReturn(testAlert);
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(testInstrument));

        AlertResponse response = alertService.resetAlert(1L, 1L);

        assertThat(response.state()).isEqualTo("ACTIVE");
        verify(alertRepository).save(argThat(alert -> alert.getState() == AlertState.ACTIVE));
    }

    @Test
    @DisplayName("resetAlert should throw AlertValidationException when alert not TRIGGERED")
    void testResetAlertNotTriggered() {
        testAlert.setState(AlertState.ACTIVE);

        when(alertRepository.findByIdAndAccountId(1L, 1L)).thenReturn(Optional.of(testAlert));

        assertThatThrownBy(() -> alertService.resetAlert(1L, 1L))
                .isInstanceOf(AlertValidationException.class)
                .hasMessageContaining("TRIGGERED");
    }

    @Test
    @DisplayName("resetAlert should throw AlertNotFoundException when not found")
    void testResetAlertNotFound() {
        when(alertRepository.findByIdAndAccountId(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.resetAlert(1L, 1L))
                .isInstanceOf(AlertNotFoundException.class);
    }
}
