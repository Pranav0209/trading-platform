package com.zkuba.executor.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.executor.event.Envelope;
import com.zkuba.executor.event.OrderPlacedPayload;
import com.zkuba.executor.event.TradeEventPayload;
import com.zkuba.executor.service.TradeExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    @Autowired
    private TradeExecutionService tradeExecutionService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "orders", groupId = "${spring.kafka.consumer.group-id:trade-executor}")
    public void onOrderPlaced(@Payload String message, Acknowledgment ack) throws Exception {
        Envelope<OrderPlacedPayload> envelope = objectMapper.readValue(message, new TypeReference<>() {
        });

        if (!"ORDER_PLACED".equals(envelope.eventType()) && !"ORDER_UPDATED".equals(envelope.eventType())) {
            throw new IllegalArgumentException("Unexpected event type: " + envelope.eventType());
        }

        OrderPlacedPayload payload = envelope.payload();
        if (payload == null || payload.orderId() == null) {
            throw new IllegalArgumentException("Missing order identifier");
        }

        log.info("Received order: {}", payload.orderId());

        Optional<TradeEventPayload> eventPayload = tradeExecutionService.executeOrder(payload.orderId());

        if (eventPayload.isPresent()) {
            publishTradeEvent(eventPayload.get());
        } else {
            log.info("Order {} was already executed or not in NEW state", payload.orderId());
        }

        // Acknowledge after publishing and committing
        ack.acknowledge();
    }

    private void publishTradeEvent(TradeEventPayload payload) {
        String type = "ORDER_FILLED";
        if (payload.status().equals("REJECTED")) {
            type = "ORDER_REJECTED";
        } else if (payload.status().equals("CANCELLED")) {
            type = "ORDER_CANCELLED";
        }

        Envelope<TradeEventPayload> envelope = new Envelope<>(
                UUID.randomUUID().toString(),
                type,
                java.time.Instant.now(),
                "trade-executor",
                1,
                payload);

        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("trade-events", String.valueOf(payload.accountId()), json).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish to trade-events", e);
        }
    }
}