package com.zkuba.trading.controller;

import com.zkuba.trading.dto.request.PlaceOrderRequest;
import com.zkuba.trading.dto.response.OrderResponse;
import com.zkuba.trading.dto.request.UpdateOrderRequest;
import com.zkuba.trading.exception.InvalidOrderException;
import com.zkuba.trading.security.JwtAuthenticationFilter.AuthenticatedUser;
import com.zkuba.trading.service.OrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PlaceOrderRequest request) {
        return orderService.place(user.userId(), request);
    }

    @PutMapping("/{id}")
    public OrderResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id,
            @Valid @RequestBody UpdateOrderRequest request) {
        if (id != null && id.startsWith("ORD-")) {
            throw new InvalidOrderException(
                    "Order ID must be a raw UUID without 'ORD-' prefix: " + id);
        }
        try {
            return orderService.update(user.userId(), UUID.fromString(id), request);
        } catch (IllegalArgumentException e) {
            throw new InvalidOrderException("Invalid order ID format: " + id);
        }
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancelPost(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id) {
        return handleCancel(user, id);
    }

    private OrderResponse handleCancel(AuthenticatedUser user, String id) {
        if (id != null && id.startsWith("ORD-")) {
            throw new InvalidOrderException(
                    "Order ID must be a raw UUID without 'ORD-' prefix: " + id);
        }
        try {
            return orderService.cancel(user.userId(), UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new InvalidOrderException("Invalid order ID format: " + id);
        }
    }
}
