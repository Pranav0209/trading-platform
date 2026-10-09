package com.zkuba.trading.service.impl;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Instrument;
import com.zkuba.trading.domain.entity.PriceAlert;
import com.zkuba.trading.domain.entity.PriceAlert.AlertState;
import com.zkuba.trading.domain.entity.PriceAlert.Direction;
import com.zkuba.trading.dto.request.CreateAlertRequest;
import com.zkuba.trading.dto.response.AlertResponse;
import com.zkuba.trading.exception.AlertNotFoundException;
import com.zkuba.trading.exception.AccountNotFoundException;
import com.zkuba.trading.exception.AlertValidationException;
import com.zkuba.trading.exception.AlertLimitExceededException;
import com.zkuba.trading.exception.InstrumentNotFoundException;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.InstrumentRepository;
import com.zkuba.trading.repository.PriceAlertRepository;
import com.zkuba.trading.service.PriceAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PriceAlertServiceImpl implements PriceAlertService {

    private static final Logger log = LoggerFactory.getLogger(PriceAlertServiceImpl.class);
    private static final long ALERT_LIMIT_PER_ACCOUNT = 100L;

    private final PriceAlertRepository alertRepository;
    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;

    public PriceAlertServiceImpl(
            PriceAlertRepository alertRepository,
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository) {
        this.alertRepository = alertRepository;
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts(Long accountId, AlertState stateFilter) {
        // Verify account exists
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        List<PriceAlert> alerts;
        if (stateFilter != null) {
            alerts = alertRepository.findByAccountIdAndState(accountId, stateFilter);
        } else {
            alerts = alertRepository.findByAccountId(accountId);
        }

        return alerts.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AlertResponse getAlert(Long accountId, Long alertId) {
        PriceAlert alert = alertRepository.findByIdAndAccountId(alertId, accountId)
                .orElseThrow(() -> new AlertNotFoundException(alertId));

        return toResponse(alert);
    }

    @Override
    public AlertResponse createAlert(Long accountId, CreateAlertRequest request) {
        // Verify account exists
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        // Verify instrument exists and is tradable
        String normalizedSymbol = request.symbol().toUpperCase();
        Instrument instrument = instrumentRepository.findById(normalizedSymbol)
                .orElseThrow(() -> new InstrumentNotFoundException(normalizedSymbol));

        if (!instrument.isTradable()) {
            throw new InstrumentNotFoundException(normalizedSymbol);
        }

        // Validate threshold
        if (request.threshold().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AlertValidationException("Threshold must be greater than zero");
        }

        // Parse and validate direction
        Direction direction;
        try {
            direction = Direction.valueOf(request.direction().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AlertValidationException("Direction must be ABOVE or BELOW");
        }

        // Check alert limit (100 active/triggered per account)
        long activeAlertCount = alertRepository.countActiveAlertsForAccount(accountId);
        if (activeAlertCount >= ALERT_LIMIT_PER_ACCOUNT) {
            log.warn("Account {} reached alert limit of {}", accountId, ALERT_LIMIT_PER_ACCOUNT);
            throw new AlertLimitExceededException(ALERT_LIMIT_PER_ACCOUNT);
        }

        // Create alert
        PriceAlert alert = new PriceAlert(account, normalizedSymbol, direction, request.threshold());
        alert = alertRepository.save(alert);

        log.info("Created alert {} for account {} on symbol {}", alert.getId(), accountId, normalizedSymbol);

        return toResponse(alert);
    }

    @Override
    public void deleteAlert(Long accountId, Long alertId) {
        PriceAlert alert = alertRepository.findByIdAndAccountId(alertId, accountId)
                .orElseThrow(() -> new AlertNotFoundException(alertId));

        alertRepository.delete(alert);
        log.info("Deleted alert {} for account {}", alertId, accountId);
    }

    @Override
    public AlertResponse resetAlert(Long accountId, Long alertId) {
        PriceAlert alert = alertRepository.findByIdAndAccountId(alertId, accountId)
                .orElseThrow(() -> new AlertNotFoundException(alertId));

        if (alert.getState() != AlertState.TRIGGERED) {
            throw new AlertValidationException("Alert is not in TRIGGERED state");
        }

        // Reset to initial ACTIVE state with PENDING delivery status
        alert.setState(AlertState.ACTIVE);
        alert.setLastFiredAt(null);
        alert.setDeliveryStatus(PriceAlert.DeliveryStatus.PENDING);
        alert.setFailureReason(null);
        alert.setUpdatedAt(OffsetDateTime.now());
        alert = alertRepository.save(alert);

        log.info("Reset alert {} for account {} back to ACTIVE with PENDING delivery status", alertId, accountId);

        return toResponse(alert);
    }

    /**
     * Update delivery status for an alert after notification send attempt.
     * This method is called by the PriceAlertEvaluator in a transactional context
     * to ensure delivery status changes are persisted reliably.
     */
    @Transactional
    public void updateAlertDeliveryStatus(Long alertId, PriceAlert.DeliveryStatus status, String failureReason) {
        Optional<PriceAlert> alertOpt = alertRepository.findById(alertId);
        if (alertOpt.isPresent()) {
            PriceAlert alert = alertOpt.get();
            alert.setDeliveryStatus(status);
            alert.setFailureReason(failureReason);
            alert.setUpdatedAt(OffsetDateTime.now());
            alertRepository.save(alert);
            log.info("Updated alert {} delivery status to {} with reason: {}", 
                    alertId, status, failureReason != null ? failureReason : "none");
        } else {
            log.warn("Alert {} not found when updating delivery status", alertId);
        }
    }

    /**
     * Convert entity to response DTO, fetching display name
     */
    private AlertResponse toResponse(PriceAlert alert) {
        Optional<Instrument> instrument =
                instrumentRepository.findById(alert.getSymbol());
        String displayName = instrument.map(Instrument::getDisplayName)
                .orElse(alert.getSymbol());

        return AlertResponse.fromEntity(alert, displayName);
    }
}
