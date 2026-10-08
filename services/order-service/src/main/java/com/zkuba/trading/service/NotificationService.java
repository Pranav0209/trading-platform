package com.zkuba.trading.service;

import com.zkuba.trading.service.PriceAlertEvaluator.AlertNotificationPayload;

/**
 * Notification Service Interface for Alert Delivery
 * 
 * This interface abstracts the notification delivery channel for price alerts.
 * The implementation resolves the customer's configured notification channel
 * from preferences (EMAIL, PUSH, etc.) and delivers the alert accordingly.
 */
public interface NotificationService {

    /**
     * Send a price alert notification to a customer.
     * 
     * The implementation will:
     * 1. Look up the customer's notification channel from preferences
     * 2. Format the alert message
     * 3. Send through the appropriate provider (EmailJS, browser push, etc.)
     * 4. Handle delivery errors gracefully
     * 
     * @param payload Alert notification details
     * @throws Exception if delivery fails (caller handles retry/logging)
     */
    void sendPriceAlertNotification(AlertNotificationPayload payload);

    /**
     * Send an order notification to a customer.
     */
    void sendOrderNotification(OrderNotificationPayload payload);

    record OrderNotificationPayload(
            Long accountId,
            String orderId,
            String side,
            Integer quantity,
            String symbol,
            String status
    ) {}
}
