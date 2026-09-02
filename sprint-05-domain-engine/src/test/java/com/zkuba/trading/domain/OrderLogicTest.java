package com.zkuba.trading.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderLogicTest {
    
    // Mutable order and position for each test
    private Order mutableOrder;
    private Position mutablePosition;

    @BeforeEach
    void setupMutableInstances() {
        mutableOrder = new Order(
                181L,
                "RELIANCE",
                OrderType.LIMIT,
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("1400.00"),
                "idem-12345"
        );
        
        mutablePosition = new Position(
                101L,
                "RELIANCE",
                new BigDecimal("100"),
                new BigDecimal("100.00")
        );
    }

     
    // Order lifecycle should start with 'NEW' 
    @Test
    void newOrderStartsFresh() {
        Order order = new Order(
                181L,
                "RELIANCE",
                OrderType.LIMIT,
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("1400.00"),
                "idem-12345"
        );
        assertEquals(OrderStatus.NEW, order.getStatus());
    }

    // Testing basic methods - use mutable order from @BeforeEach
    @Test
    void fillMovesToFilled() {
        mutableOrder.fill();
        assertEquals(OrderStatus.FILLED, mutableOrder.getStatus());
    }

    @Test
    void rejectMovesToRejected() {
        mutableOrder.reject();
        assertEquals(OrderStatus.REJECTED, mutableOrder.getStatus());
    }

    @Test
    void cancelMovesToCancelled() {
        mutableOrder.cancel();
        assertEquals(OrderStatus.CANCELLED, mutableOrder.getStatus());
    }

    // Illegal status change shouldn't be allowed 
    @Test
    void filledOrderCannotCancel() {
        mutableOrder.fill();
        assertThrows(InvalidOrderTransitionException.class, mutableOrder::cancel);
    }

    @Test
    void rejectedOrderCannotFill() {
        mutableOrder.reject();
        assertThrows(InvalidOrderTransitionException.class, mutableOrder::fill);
    }

    // Constraints based checks
    @Test
    void marketCannotHaveLimitPrice(){
        assertThrows(
                IllegalArgumentException.class,
                () -> new Order(
                181L,
                "RELIANCE.NS",
                OrderType.MARKET,
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("1400.00"),
                "idem-12345"
                )
        );
    }

    @Test
    void limitPriceShouldBePositive(){
        assertThrows(
                IllegalArgumentException.class,
                () -> new Order(
                181L,
                "RELIANCE.NS",
                OrderType.LIMIT,
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("-12"),
                "idem-12345"
                )
        );
    }

    @Test
    void quantityShouldBePositive(){
        assertThrows(
                IllegalArgumentException.class,
                () -> new Order(
                181L,
                "RELIANCE.NS",
                OrderType.LIMIT,
                OrderSide.BUY,
                new BigDecimal("-100"),
                new BigDecimal("1200"),
                "idem-12345"
                )
        );
    }

    // Buy and sell logic based checks 
    @Test
    void buyUpdatesAverageCost() {
        mutablePosition.buy(new BigDecimal("50"), new BigDecimal("130.00"));

        assertEquals(new BigDecimal("150"), mutablePosition.getQuantity());
        // (100 * 100.00 + 50 * 130.00) / 150 = 110.00000000
        assertEquals(0, new BigDecimal("110.00").compareTo(mutablePosition.getAverageCost()));
    }

    @Test
    void sellKeepsAverageCost() {
        mutablePosition.sell(new BigDecimal("40"));

        assertEquals(new BigDecimal("60"), mutablePosition.getQuantity());
        assertEquals(new BigDecimal("100.00"), mutablePosition.getAverageCost());
    }

    @Test
    void sellCannotGoNegative() {
        assertThrows(
                InsufficientHoldingsException.class,
                () -> mutablePosition.sell(new BigDecimal("101"))
        );

        assertEquals(new BigDecimal("100"), mutablePosition.getQuantity());
    }

    @Test
    void sellCanHitZero() {
        mutablePosition.sell(new BigDecimal("100"));

        assertEquals(new BigDecimal("0"), mutablePosition.getQuantity());
    }
}
