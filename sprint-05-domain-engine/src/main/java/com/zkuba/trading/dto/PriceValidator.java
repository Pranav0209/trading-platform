package com.zkuba.trading.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class PriceValidator
        implements ConstraintValidator<ValidPrice, BigDecimal> {

    @Override
    public boolean isValid(
            BigDecimal price,
            ConstraintValidatorContext context) {

        if (price == null) {
            return true; // @NotNull handles the null case
        }

        return price.scale() <= 2;
    }
}
