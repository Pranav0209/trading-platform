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
    private Integer quantity;

    // must be present for limit and not for market
    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 10, fraction = 2)
    private BigDecimal price;

    @NotBlank
    @Size(min = 8, max = 100)
    private String idempotencyKey;

    public PlaceOrderRequest(
            Long accountId,
            String symbol,
            OrderSide side,
            Integer quantity,
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

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    @AssertTrue(message = "for limit it must have price, for limit no price")
    public boolean isPriceConsistentWithOrderType() {
        if (orderType == null) {
            return true;
        }
        return orderType == OrderType.MARKET ? price == null : price != null;
    }

}