package com.zkuba.trading.domain;

import com.zkuba.trading.domain.exception.InsufficientHoldingsException;
import com.zkuba.trading.domain.model.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PositionTest {

    // Mutable position for each test
    private Position mutablePosition;

    @BeforeEach
    void setupMutablePosition() {
        mutablePosition = new Position(
                101L,
                "RELIANCE",
                new BigDecimal("100"),
                new BigDecimal("100.00")
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
