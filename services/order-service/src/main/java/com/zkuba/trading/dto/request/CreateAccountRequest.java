package com.zkuba.trading.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateAccountRequest(

                @NotNull Long userId,

                @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,

                @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal initialBalance

) {
}