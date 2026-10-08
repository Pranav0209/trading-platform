package com.zkuba.trading.dto.request;

import com.zkuba.trading.domain.enums.OrderSide;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InternalPlaceOrderRequest(

        @NotNull
        @Min(1)
        Long userId,

        @NotNull
        @Min(1)
        Long accountId,

        @NotBlank
        String symbol,

        @NotNull
        OrderSide side,

        @Min(1)
        int quantity,

        @NotBlank
        String idempotencyKey
) {}