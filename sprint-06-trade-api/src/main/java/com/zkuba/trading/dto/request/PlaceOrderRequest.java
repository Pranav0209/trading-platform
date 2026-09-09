package com.zkuba.trading.dto.request;

import com.zkuba.trading.domain.enums.OrderSide;
import com.zkuba.trading.domain.enums.OrderType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PlaceOrderRequest(

        @NotBlank
        String symbol,

        @NotNull
        OrderSide side,

        @NotNull
        OrderType orderType,

        @Min(1)
        int quantity,

        BigDecimal limitPrice,

        @NotBlank
        String idempotencyKey
) {}
