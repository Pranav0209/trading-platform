package com.zkuba.trading.domain.model;

import com.zkuba.trading.domain.enums.OrderSide;
import com.zkuba.trading.domain.enums.OrderStatus;
import com.zkuba.trading.domain.enums.OrderType;
import com.zkuba.trading.domain.exception.InvalidOrderTransitionException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

// Annotations document the invariants; validate() below is what actually enforces them.
public class Order {

    @NotNull
    @Positive
    private final Long accountId;
    @NotBlank
    private final String symbol;
    @NotNull
    private final OrderType type;
    @NotNull
    private final OrderSide side;
    @NotNull
    @Positive
    private final Integer quantity;
    private final BigDecimal limitPrice;
    @NotBlank
    private final String idempotencyKey;
    private final UUID orderId = UUID.randomUUID();
    @NotNull
    private OrderStatus status;
    private BigDecimal executedPrice;

    public Order(
            Long accountId,
            String symbol,
            OrderType type,
            OrderSide side,
            Integer quantity,
            BigDecimal limitPrice,
            String idempotencyKey) {

        this.accountId = Objects.requireNonNull(accountId);
        this.symbol = Objects.requireNonNull(symbol);
        this.type = Objects.requireNonNull(type);
        this.side = Objects.requireNonNull(side);
        this.quantity = Objects.requireNonNull(quantity);
        this.limitPrice = limitPrice;           // null is valid for MARKET orders
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.status = OrderStatus.NEW;
        this.executedPrice = null;              // null until order is FILLED
        validate();
    }

    /** Transitions the order to FILLED. */
    public void fill(BigDecimal executedPrice) {
        ensureTransitionAllowed("fill");
        this.executedPrice = Objects.requireNonNull(executedPrice);
        status = OrderStatus.FILLED;
    }

    /** Transitions the order to REJECTED. */
    public void reject() {
        ensureTransitionAllowed("reject");
        status = OrderStatus.REJECTED;
    }

    /** Transitions the order to CANCELLED. */
    public void cancel() {
        ensureTransitionAllowed("cancel");
        status = OrderStatus.CANCELLED;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderType getType() {
        return type;
    }

    public OrderSide getSide() {
        return side;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getExecutedPrice() {
        return executedPrice;
    }

    // Rules 4 and 5: re-checked here so a caller that skips the DTO validator can't bypass them.
    private void validate() {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero");
        }

        if (type == OrderType.LIMIT) {
            if (limitPrice == null || limitPrice.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Limit price must be greater than zero");
            }
        }

        if (type == OrderType.MARKET && limitPrice != null) {
            throw new IllegalArgumentException(
                    "Market order cannot have a limit price");
        }
    }

    private void ensureTransitionAllowed(String operation) {
        if (status != OrderStatus.NEW) {
            throw new InvalidOrderTransitionException(orderId, status, operation);
        }
    }
}

