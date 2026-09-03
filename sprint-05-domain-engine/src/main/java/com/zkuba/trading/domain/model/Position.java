package com.zkuba.trading.domain.model;

import com.zkuba.trading.domain.exception.InsufficientHoldingsException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
public class Position {

    @NotNull
    @Positive
    private final Long accountId;
    @NotBlank
    private final String symbol;
    @NotNull
    @PositiveOrZero
    private BigDecimal quantity;
    @NotNull
    @Positive
    private BigDecimal averageCost;

    public Position(
            Long accountId,
            String symbol,
            BigDecimal quantity,
            BigDecimal averageCost) {

        this.accountId = Objects.requireNonNull(accountId);
        this.symbol = Objects.requireNonNull(symbol);
        this.quantity = Objects.requireNonNull(quantity);
        this.averageCost = Objects.requireNonNull(averageCost);

        if (quantity.signum() < 0) {
            throw new IllegalArgumentException(
                    "Position quantity cannot be negative");
        }

        if (averageCost.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Average cost must be positive");
        }
    }

    // buy update logic, take old position and new position to obtain new average price
    public void buy(BigDecimal units, BigDecimal price) {

        requirePositive(units);
        requirePositive(price);

        BigDecimal oldValue = quantity.multiply(averageCost);
        BigDecimal newValue = units.multiply(price);
        BigDecimal totalQuantity = quantity.add(units);

        averageCost = oldValue
                .add(newValue)
                .divide(totalQuantity, 8, RoundingMode.HALF_UP);

        quantity = totalQuantity;
    }

    // sell must retain average price and just quantity gets reduced
    // 7. insuffient holdings in sell
    public void sell(BigDecimal units) {

        requirePositive(units);

        if (quantity.compareTo(units) < 0) {
            throw new InsufficientHoldingsException(accountId, symbol);
        }

        quantity = quantity.subtract(units);
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    private void requirePositive(BigDecimal value) {
        Objects.requireNonNull(value);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Value must be positive");
        }
    }
}

