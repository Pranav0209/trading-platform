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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    // Cache lightweight DTOs to prevent database starvation from high-frequency ticks.
    // NOTE: We intentionally cache DTOs for 2 seconds. A newly created alert may take
    // up to 2 seconds to be evaluated.
    private final ConcurrentHashMap<String, CacheEntry> alertCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 2000;

    private static class CacheEntry {
        final List<PriceAlertData> alerts;
        final long timestamp;
        CacheEntry(List<PriceAlertData> alerts, long timestamp) {
            this.alerts = alerts;
            this.timestamp = timestamp;
        }
    }

    /**
     * Lightweight immutable DTO for in-memory alert evaluation
     */
    private record PriceAlertData(
            Long id,
            Long accountId,
            String accountCurrency,
            String symbol,
            Direction direction,
            BigDecimal threshold,
            AlertState state,
            OffsetDateTime createdAt
    ) {
        static PriceAlertData fromEntity(PriceAlert alert) {
            return new PriceAlertData(
                    alert.getId(),
                    alert.getAccount().getId(),
                    alert.getAccount().getCurrency() != null ? alert.getAccount().getCurrency() : "USD",
                    alert.getSymbol(),
                    alert.getDirection(),
                    alert.getThreshold(),
                    alert.getState(),
                    alert.getCreatedAt()
            );
        }
    }

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

            // Check cache first to avoid DB queries for every tick
            long now = System.currentTimeMillis();
            CacheEntry cacheEntry = alertCache.get(symbol);
            List<PriceAlertData> alerts;
            
            if (cacheEntry != null && (now - cacheEntry.timestamp) < CACHE_TTL_MS) {
                alerts = cacheEntry.alerts;
            } else {
                alerts = alertRepository.findActiveAlertsForSymbol(symbol)
                        .stream()
                        .map(PriceAlertData::fromEntity)
                        .collect(Collectors.toList());
                alertCache.put(symbol, new CacheEntry(alerts, now));
            }
            
            if (alerts.isEmpty()) {
                log.debug("No active alerts for symbol {}", symbol);
                return;
            }

            log.debug("Evaluating {} alerts for symbol {} at price {} {}", alerts.size(), symbol, price, quoteCurrency);

            // Evaluate each alert
            for (PriceAlertData alert : alerts) {
                evaluateAlert(alert, price, quoteCurrency, symbol);
            }

        } catch (Exception e) {
            log.error("Failed to evaluate price alerts from market-data message", e);
            // Don't rethrow; continue processing other messages
        }
    }

    /**
     * Evaluate a single alert DTO against a price, converting to account currency first
     */
    private void evaluateAlert(PriceAlertData alert, BigDecimal priceInQuoteCurrency, String quoteCurrency, String symbol) {
        try {
            // Only evaluate ACTIVE alerts
            if (alert.state() != AlertState.ACTIVE) {
                return;
            }

            // Convert price to account currency if needed
            BigDecimal priceInAccountCurrency = priceInQuoteCurrency;
            if (!quoteCurrency.equals(alert.accountCurrency())) {
                try {
                    BigDecimal rate = currencyConversionService.getConversionRate(quoteCurrency, alert.accountCurrency());
                    priceInAccountCurrency = priceInQuoteCurrency.multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP);
                } catch (Exception e) {
                    log.warn("Failed to convert price from {} to {}, using original price. Error: {}", 
                            quoteCurrency, alert.accountCurrency(), e.getMessage());
                }
            }

            boolean thresholdCrossed = false;
            if (alert.direction() == Direction.ABOVE) {
                thresholdCrossed = priceInAccountCurrency.compareTo(alert.threshold()) >= 0;
            } else if (alert.direction() == Direction.BELOW) {
                thresholdCrossed = priceInAccountCurrency.compareTo(alert.threshold()) <= 0;
            }

            if (thresholdCrossed) {
                // Short DB transaction: fetch actual entity and update
                Optional<PriceAlert> entityOpt = alertRepository.findById(alert.id());
                if (entityOpt.isEmpty() || entityOpt.get().getState() != AlertState.ACTIVE) {
                    return; // Rule was deleted or already triggered
                }
                
                PriceAlert entity = entityOpt.get();
                entity.setState(AlertState.TRIGGERED);
                entity.setLastFiredAt(OffsetDateTime.now());
                entity.setDeliveryStatus(DeliveryStatus.PENDING);
                entity.setUpdatedAt(OffsetDateTime.now());

                // Delivery runs outside of a transaction context because evaluateAlerts isn't @Transactional
                deliverAlert(entity, priceInAccountCurrency);

                alertRepository.save(entity);
            }

        } catch (Exception e) {
            log.error("Error evaluating alert {}", alert.id(), e);
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
