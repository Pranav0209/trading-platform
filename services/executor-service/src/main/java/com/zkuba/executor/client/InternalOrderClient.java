package com.zkuba.executor.client;

import com.zkuba.executor.domain.enums.OrderSide;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InternalOrderClient {

    private final RestClient restClient;

    public InternalOrderClient(@Value("${order.service.base-url}") String orderServiceBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(orderServiceBaseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public void placeMarketOrder(Long userId, Long accountId, String symbol, OrderSide side, int quantity, String idempotencyKey) {
        restClient.post()
                .uri("/internal/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InternalOrderRequest(userId, accountId, symbol, side, quantity, idempotencyKey))
                .retrieve()
                .toBodilessEntity();
    }

    private record InternalOrderRequest(
            Long userId,
            Long accountId,
            String symbol,
            OrderSide side,
            int quantity,
            String idempotencyKey
    ) {}
}