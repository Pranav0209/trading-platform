package com.zkuba.trading.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateAlertRequest(
        @NotBlank(message = "Symbol is required")
        @Size(min = 1, max = 20, message = "Symbol must be between 1 and 20 characters")
        String symbol,

        @NotNull(message = "Direction is required")
        String direction,

        @NotNull(message = "Threshold is required")
        @DecimalMin(value = "0", inclusive = false, message = "Threshold must be greater than zero")
        BigDecimal threshold
) {}
