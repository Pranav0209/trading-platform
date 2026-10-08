package com.zkuba.executor.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.executor.domain.entity.Order;
import com.zkuba.executor.domain.enums.OrderStatus;
import com.zkuba.executor.repository.OrderRepository;
import com.zkuba.executor.service.TradeExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LimitOrderEvaluator {

    private static final Logger log = LoggerFactory.getLogger(LimitOrderEvaluator.class);

    private final OrderRepository orderRepository;
    private final TradeExecutionService tradeExecutionService;
    private final ObjectMapper objectMapper;

    public LimitOrderEvaluator(OrderRepository orderRepository, TradeExecutionService tradeExecutionService, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.tradeExecutionService = tradeExecutionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "market-data", groupId = "executor-limit-order-service")
    public void evaluateLimitOrders(String message) {
        try {
            Map<String, Object> quoteData = objectMapper.readValue(message, new TypeReference<>() {});
            String symbol = (String) quoteData.get("symbol");
            if (symbol == null) return;

            List<Order> pendingOrders = orderRepository.findBySymbolAndStatus(symbol, OrderStatus.NEW);
            for (Order order : pendingOrders) {
                try {
                    tradeExecutionService.executeOrder(order.getId());
                } catch (Exception e) {
                    log.error("Failed to execute limit order {}", order.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse market data for limit orders", e);
        }
    }
}
