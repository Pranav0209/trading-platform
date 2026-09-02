package com.zkuba.trading.domain;

import com.zkuba.trading.domain.enums.OrderSide;
import com.zkuba.trading.domain.enums.OrderStatus;
import com.zkuba.trading.domain.enums.OrderType;
import com.zkuba.trading.domain.exception.InvalidOrderTransitionException;
import com.zkuba.trading.domain.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    // Mutable order for each test
    private Order mutableOrder;

    @BeforeEach
    void setupMutableOrder() {
        mutableOrder = new Order(
                181L,
                "RELIANCE",
                OrderType.LIMIT,
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("1400.00"),
                "idem-12345"
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
    void marketCannotHaveLimitPrice() {
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
    void limitPriceShouldBePositive() {
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
    void quantityShouldBePositive() {
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
}
