package com.zkuba.trading.event;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.zkuba.trading.domain.entity.Order;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final String TOPIC_ORDERS = "orders";
    private static final String SOURCE = "trade-api";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        Order order = event.order();
        
        OrderPlacedPayload payload = new OrderPlacedPayload(
                order.getId(),
                order.getAccountId(),
                order.getSymbol(),
                order.getSide().name(),
                order.getQuantity(),
                order.getLimitPrice(),
                order.getIdempotencyKey(),
                order.getCreatedAt() != null ? order.getCreatedAt().toInstant() : Instant.now()
        );

        Envelope<OrderPlacedPayload> envelope = new Envelope<>(
                UUID.randomUUID().toString(),
                "ORDER_PLACED",
                Instant.now(),
                SOURCE,
                1,
                payload
        );

        String key = String.valueOf(order.getAccountId());
        
        log.debug("Publishing ORDER_PLACED event to orders topic for accountId={}", key);
        
        kafkaTemplate.send(TOPIC_ORDERS, key, envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ORDER_PLACED event for orderId={}", order.getId(), ex);
                        // The event is lost here, but as per instructions:
                        // "an order that committed and was never published can be replayed from the order table"
                        // So the recoverable failure is expected.
                    } else {
                        log.debug("Successfully published ORDER_PLACED event for orderId={}", order.getId());
                    }
                });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderUpdated(OrderUpdatedEvent event) {
        Order order = event.order();
        
        OrderPlacedPayload payload = new OrderPlacedPayload(
                order.getId(),
                order.getAccountId(),
                order.getSymbol(),
                order.getSide().name(),
                order.getQuantity(),
                order.getLimitPrice(),
                order.getIdempotencyKey(),
                order.getCreatedAt() != null ? order.getCreatedAt().toInstant() : Instant.now()
        );

        Envelope<OrderPlacedPayload> envelope = new Envelope<>(
                UUID.randomUUID().toString(),
                "ORDER_UPDATED",
                Instant.now(),
                SOURCE,
                1,
                payload
        );

        String key = String.valueOf(order.getAccountId());
        
        log.debug("Publishing ORDER_UPDATED event to orders topic for accountId={}", key);
        
        kafkaTemplate.send(TOPIC_ORDERS, key, envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ORDER_UPDATED event for orderId={}", order.getId(), ex);
                    } else {
                        log.debug("Successfully published ORDER_UPDATED event for orderId={}", order.getId());
                    }
                });
    }
}
