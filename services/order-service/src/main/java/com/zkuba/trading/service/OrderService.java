package com.zkuba.trading.service;

import com.zkuba.trading.dto.request.PlaceOrderRequest;
import com.zkuba.trading.dto.request.UpdateOrderRequest;
import com.zkuba.trading.dto.response.OrderResponse;
import java.util.UUID;

public interface OrderService {

    OrderResponse place(Long userId, PlaceOrderRequest request);

    OrderResponse cancel(Long userId, UUID orderId);

    OrderResponse update(Long userId, UUID orderId, UpdateOrderRequest request);
}

