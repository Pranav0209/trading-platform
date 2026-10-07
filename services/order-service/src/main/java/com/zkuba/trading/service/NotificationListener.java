package com.zkuba.trading.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.User;
import com.zkuba.trading.event.Envelope;
import com.zkuba.trading.event.OrderPlacedPayload;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.zkuba.trading.event.TradeEventPayload;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);
    
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationListener(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "orders", groupId = "order-service-notifications-group")
    public void handleOrderEvent(String message) {
        try {
            Envelope<OrderPlacedPayload> envelope = objectMapper.readValue(message, new TypeReference<Envelope<OrderPlacedPayload>>() {});
            
            if ("ORDER_UPDATED".equals(envelope.eventType())) {
                OrderPlacedPayload payload = envelope.payload();
                
                String status = payload.status();
                if ("CANCELLED".equals(status)) {
                    log.info("Processing notification for cancelled order {}", payload.orderId());
                    processNotificationAsync(payload.accountId(), payload.orderId().toString(), payload.side(), payload.quantity(), payload.symbol(), status);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process order event for notifications", e);
        }
    }

    @KafkaListener(topics = "trade-events", groupId = "order-service-notifications-group")
    public void handleTradeEvent(String message) {
        try {
            Envelope<TradeEventPayload> envelope = objectMapper.readValue(message, new TypeReference<Envelope<TradeEventPayload>>() {});
            
            if ("ORDER_FILLED".equals(envelope.eventType()) || "ORDER_REJECTED".equals(envelope.eventType())) {
                TradeEventPayload payload = envelope.payload();
                
                log.info("Processing notification for filled/rejected order {}", payload.orderId());
                processNotificationAsync(payload.accountId(), payload.orderId().toString(), payload.side(), payload.quantity(), payload.symbol(), payload.status());
            }
        } catch (Exception e) {
            log.error("Failed to process trade event for notifications", e);
        }
    }

    private void processNotificationAsync(Long accountId, String orderId, String side, Integer quantity, String symbol, String status) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            notificationService.sendOrderNotification(new NotificationService.OrderNotificationPayload(
                    accountId, orderId, side, quantity, symbol, status
            ));
        });
    }
}
