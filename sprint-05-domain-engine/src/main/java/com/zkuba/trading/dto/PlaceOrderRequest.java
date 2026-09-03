package com.zkuba.trading.dto;

import com.zkuba.trading.domain.enums.OrderSide;
import com.zkuba.trading.domain.enums.OrderType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class PlaceOrderRequest {

    @NotNull
    @Min(1)
    private Long accountId;

    @NotBlank
    @Size(min = 1, max = 20)
    private String symbol;

    @NotNull
    private OrderSide side;

    @NotNull
    private OrderType orderType;

    @NotNull
    @Positive
    @Digits(integer = 19, fraction = 0)
    private BigDecimal quantity;

    // required for LIMIT, must be absent for MARKET; see isPriceConsistentWithOrderType()
    @DecimalMin(value = "0.00", inclusive = false)
    @ValidPrice
    private BigDecimal price;

    @NotBlank
    @Size(min = 8, max = 100)
    private String idempotencyKey;

    public PlaceOrderRequest(
            Long accountId,
            String symbol,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal price,
            String idempotencyKey,
            OrderType orderType) {

        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.idempotencyKey = idempotencyKey;
        this.orderType = orderType;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    @AssertTrue(message = "Price is required for LIMIT orders and must be absent for MARKET orders")
    public boolean isPriceConsistentWithOrderType() {
        if (orderType == null) {
            return true; // reported separately by @NotNull on orderType
        }
        return orderType == OrderType.MARKET ? price == null : price != null;
    }

}