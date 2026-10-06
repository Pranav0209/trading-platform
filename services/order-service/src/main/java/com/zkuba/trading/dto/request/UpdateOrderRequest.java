package com.zkuba.trading.dto.request;

import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

public record UpdateOrderRequest(
        @Min(1)
        Integer quantity,

        BigDecimal limitPrice
) {}
