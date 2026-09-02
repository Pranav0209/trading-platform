package com.zkuba.trading.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PriceValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPrice {

    String message() default "Price must have at most two decimal places";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
