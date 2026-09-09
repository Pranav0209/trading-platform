package com.zkuba.trading.service;

import com.zkuba.trading.dto.request.PlaceOrderRequest;
import com.zkuba.trading.dto.response.OrderResponse;
import java.util.List;
import java.util.UUID;

public interface OrderService {

    OrderResponse place(Long userId, PlaceOrderRequest request);

    default OrderResponse placeOrder(Long userId, PlaceOrderRequest request) {
        return place(userId, request);
    }

    OrderResponse cancel(Long userId, UUID orderId);

    default OrderResponse cancelOrder(Long userId, UUID orderId) {
        return cancel(userId, orderId);
    }

    List<OrderResponse> getOrderHistory(Long userId);
}
