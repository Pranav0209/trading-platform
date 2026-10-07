package com.zkuba.trading.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.trading.domain.entity.PriceAlert;
import com.zkuba.trading.domain.entity.PriceAlert.AlertState;
import com.zkuba.trading.domain.entity.PriceAlert.DeliveryStatus;
import com.zkuba.trading.domain.entity.PriceAlert.Direction;
import com.zkuba.trading.repository.PriceAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Market Data Consumer for Price Alerts
 * 
 * Consumes quotes from the market-data Kafka topic and evaluates them against
 * active price alerts. When a price crosses a threshold, the alert transitions
 * to TRIGGERED state and a notification is delivered.
 * 
 * Design notes:
 * 1. Consumer group: price-alert-service (distinct from notifications)
 * 2. Hot path: Must efficiently check if any account has alerts for incoming symbol
 * 3. Indexing: (symbol, state) on price_alerts table for fast lookups
 * 4. Threshold crossing logic:
 *    - ABOVE: fires when price crosses upward (price >= threshold)
 *    - BELOW: fires when price crosses downward (price <= threshold)
 * 5. Alert lifecycle: ACTIVE -> TRIGGERED (wait for reset) -> ACTIVE
 * 6. Dormant state: Not used in this iteration; alerts go ACTIVE after reset
 * 7. Currency conversion: prices from Kafka are in USD, thresholds are in account currency
 *    so prices are converted to account currency before comparison
 */
@Service
public class PriceAlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(PriceAlertEvaluator.class);

    private final PriceAlertRepository alertRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final CurrencyConversionService currencyConversionService;

    public PriceAlertEvaluator(
            PriceAlertRepository alertRepository,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            CurrencyConversionService currencyConversionService) {
        this.alertRepository = alertRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.currencyConversionService = currencyConversionService;
    }

    /**
     * Consume market-data quotes and evaluate alerts.
     * 
     * Market-data topic schema:
     * {
     *   "symbol": "AAPL",
     *   "price": 150.25,
     *   "currency": "USD",
     *   "change": 0.50,
     *   "changePercent": 0.33,
     *   ...
     * }
     */
    @KafkaListener(topics = "market-data", groupId = "price-alert-service")
    @Transactional
    public void evaluateAlerts(String message) {
        try {
            Map<String, Object> quoteData = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
            
            String symbol = (String) quoteData.get("symbol");
            Object priceObj = quoteData.get("price");
            String quoteCurrency = (String) quoteData.getOrDefault("currency", "USD");
            
            if (symbol == null || priceObj == null) {
                log.warn("Malformed market data message: missing symbol or price");
                return;
            }

            BigDecimal price = new BigDecimal(priceObj.toString());

            // Find all ACTIVE alerts for this symbol
            List<PriceAlert> alerts = alertRepository.findActiveAlertsForSymbol(symbol);
            
            if (alerts.isEmpty()) {
                log.debug("No active alerts for symbol {}", symbol);
                return;
            }

            log.debug("Evaluating {} alerts for symbol {} at price {} {}", alerts.size(), symbol, price, quoteCurrency);

            // Evaluate each alert
            for (PriceAlert alert : alerts) {
                evaluateAlert(alert, price, quoteCurrency, symbol);
            }

        } catch (Exception e) {
            log.error("Failed to evaluate price alerts from market-data message", e);
            // Don't rethrow; continue processing other messages
        }
    }

    /**
     * Evaluate a single alert against a price, converting to account currency first
     */
    private void evaluateAlert(PriceAlert alert, BigDecimal priceInQuoteCurrency, String quoteCurrency, String symbol) {
        try {
            // Only evaluate ACTIVE alerts; TRIGGERED alerts are waiting for customer reset
            if (alert.getState() != AlertState.ACTIVE) {
                return;
            }

            // Get account currency
            String accountCurrency = alert.getAccount().getCurrency() != null ? 
                    alert.getAccount().getCurrency() : "USD";

            // Convert price to account currency if needed
            BigDecimal priceInAccountCurrency = priceInQuoteCurrency;
            if (!quoteCurrency.equals(accountCurrency)) {
                try {
                    BigDecimal rate = currencyConversionService.getConversionRate(quoteCurrency, accountCurrency);
                    priceInAccountCurrency = priceInQuoteCurrency.multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP);
                    log.debug("Converted price {} {} to {} {} using rate {}", 
                            priceInQuoteCurrency, quoteCurrency, priceInAccountCurrency, accountCurrency, rate);
                } catch (Exception e) {
                    log.warn("Failed to convert price from {} to {}, using original price. Error: {}", 
                            quoteCurrency, accountCurrency, e.getMessage());
                    // Use original price if conversion fails
                }
            }

            boolean thresholdCrossed = false;

            if (alert.getDirection() == Direction.ABOVE) {
                // Alert fires when price rises through threshold
                thresholdCrossed = priceInAccountCurrency.compareTo(alert.getThreshold()) >= 0;
            } else if (alert.getDirection() == Direction.BELOW) {
                // Alert fires when price falls through threshold
                thresholdCrossed = priceInAccountCurrency.compareTo(alert.getThreshold()) <= 0;
            }

            if (thresholdCrossed) {
                log.info("Alert {} triggered for {} at {} {} (threshold: {} {}, direction: {})",
                        alert.getId(), symbol, priceInAccountCurrency, accountCurrency, 
                        alert.getThreshold(), accountCurrency, alert.getDirection());

                // Transition to TRIGGERED
                alert.setState(AlertState.TRIGGERED);
                alert.setLastFiredAt(OffsetDateTime.now());
                alert.setDeliveryStatus(DeliveryStatus.PENDING);
                alert.setUpdatedAt(OffsetDateTime.now());

                // Deliver notification through the notifications module
                deliverAlert(alert, priceInAccountCurrency);

                // Save updated alert
                alertRepository.save(alert);
            }

        } catch (Exception e) {
            log.error("Error evaluating alert {}", alert.getId(), e);
        }
    }

    /**
     * Deliver alert notification through the Customer Notifications module.
     * 
     * This is an in-process call to the notifications delivery interface.
     * The notifications module:
     * 1. Resolves the customer's notification channel from preferences
     * 2. Formats the alert message
     * 3. Sends through EmailJS or other provider
     * 4. Records delivery success/failure
     * 
     * If delivery fails, the alert state still reflects TRIGGERED so the customer
     * can see it and investigate their notification settings.
     */
    private void deliverAlert(PriceAlert alert, BigDecimal currentPrice) {
        try {
            AlertNotificationPayload payload = new AlertNotificationPayload(
                    alert.getId(),
                    alert.getAccount().getId(),
                    alert.getSymbol(),
                    alert.getDirection().name(),
                    alert.getThreshold(),
                    currentPrice,
                    alert.getCreatedAt()
            );

            notificationService.sendPriceAlertNotification(payload);

            alert.setDeliveryStatus(DeliveryStatus.DELIVERED);
            alert.setFailureReason(null);
            log.info("Alert {} notification delivered", alert.getId());

        } catch (Exception e) {
            log.error("Failed to deliver alert {} notification", alert.getId(), e);
            alert.setDeliveryStatus(DeliveryStatus.FAILED);
            alert.setFailureReason(e.getMessage());
        }
    }

    /**
     * Payload for alert notifications sent to the notifications module
     */
    public record AlertNotificationPayload(
            Long alertId,
            Long accountId,
            String symbol,
            String direction,
            BigDecimal threshold,
            BigDecimal currentPrice,
            OffsetDateTime createdAt
    ) {}
}
