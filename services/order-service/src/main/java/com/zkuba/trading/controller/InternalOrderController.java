package com.zkuba.trading.controller;

import com.zkuba.trading.domain.enums.OrderType;
import com.zkuba.trading.dto.request.InternalPlaceOrderRequest;
import com.zkuba.trading.dto.request.PlaceOrderRequest;
import com.zkuba.trading.dto.response.OrderResponse;
import com.zkuba.trading.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final OrderService orderService;
    
    @Value("${jwt.secret}")
    private String internalSecret;

    public InternalOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(
            @RequestHeader("X-Internal-Secret") String secret,
            @Valid @RequestBody InternalPlaceOrderRequest request) {
        
        if (!internalSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal secret");
        }
        
        PlaceOrderRequest placeOrderRequest = new PlaceOrderRequest(
                request.accountId(),
                request.symbol(),
                request.side(),
                OrderType.MARKET,
                request.quantity(),
                null,
                request.idempotencyKey());

        return orderService.place(request.userId(), placeOrderRequest);
    }
}