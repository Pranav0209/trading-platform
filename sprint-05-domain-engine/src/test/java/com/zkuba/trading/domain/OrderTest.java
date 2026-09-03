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

    // Mutable order
    private Order mutableOrder;

    @BeforeEach
    void setupMutableOrder() {
        mutableOrder = new Order(
                181L,
                "RELIANCE",
                OrderType.LIMIT,
                OrderSide.BUY,
                100,
                new BigDecimal("1400.00"),
                "idem-12345"
        );
    }

    // Order lifecycle should starting as new
    @Test
    void newOrderStartsFresh() {
        Order order = new Order(
                181L,
                "RELIANCE",
                OrderType.LIMIT,
                OrderSide.BUY,
                100,
                new BigDecimal("1400.00"),
                "idem-12345"
        );
        assertEquals(OrderStatus.NEW, order.getStatus());
    }

    // checking after new to filled
    @Test
    void fillMovesToFilled() {
        mutableOrder.fill(new BigDecimal("1399.50"));
        assertEquals(OrderStatus.FILLED, mutableOrder.getStatus());
        assertEquals(new BigDecimal("1399.50"), mutableOrder.getExecutedPrice());
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

    // filled order cannot be cancelled
    @Test
    void filledOrderCannotCancel() {
        mutableOrder.fill(new BigDecimal("1399.50"));
        assertThrows(InvalidOrderTransitionException.class, mutableOrder::cancel);
    }

    // rejected orders cannot be filled
    @Test
    void rejectedOrderCannotFill() {
        mutableOrder.reject();
        assertThrows(InvalidOrderTransitionException.class, () -> mutableOrder.fill(new BigDecimal("1399.50")));
    }

    // market order should not have a limit price
    @Test
    void marketOrderCannotHaveLimitPrice() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Order(
                        181L,
                        "RELIANCE.NS",
                        OrderType.MARKET,
                        OrderSide.BUY,
                        100,
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
                        100,
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
                        -100,
                        new BigDecimal("1200"),
                        "idem-12345"
                )
        );
    }
}
