package com.zkuba.trading.controller;

import com.zkuba.trading.dto.request.PlaceOrderRequest;
import com.zkuba.trading.dto.response.OrderResponse;
import com.zkuba.trading.security.JwtAuthenticationFilter.AuthenticatedUser;
import com.zkuba.trading.service.OrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @PostMapping
    public OrderResponse place(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PlaceOrderRequest request) {
        return orderService.place(user.userId(), request);
    }

    @GetMapping
    public java.util.List<OrderResponse> history(@AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.getOrderHistory(user.userId());
    }

    @DeleteMapping("/{id}")
    public OrderResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        String cleanId = id.startsWith("ORD-") ? id.substring(4) : id;
        try {
            return orderService.cancel(user.userId(), UUID.fromString(cleanId));
        } catch (IllegalArgumentException e) {
            throw new com.zkuba.trading.domain.exception.InvalidOrderException("Invalid order ID: " + id);
        }
    }
}

