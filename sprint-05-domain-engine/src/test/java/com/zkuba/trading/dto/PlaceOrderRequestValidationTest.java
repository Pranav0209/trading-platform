package com.zkuba.trading.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PlaceOrderRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;
    
    //immutable
    private static PlaceOrderRequest validPOR;

    @BeforeAll
    static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        validPOR = new PlaceOrderRequest(
                181L,
                "RELIANCE",
                OrderSide.BUY,
                100,
                new BigDecimal("1400.00"),
                "idem-12345",
                OrderType.LIMIT
        );
    }

    @AfterAll
    static void cleanup() {
        factory.close();
    }

    @Test
    void validRequestPasses() {
        assertTrue(validator.validate(validPOR).isEmpty());
    }

    // DTO validation checks
    @Test
    void nullAccountIdFails() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                null, "RELIANCE", OrderSide.BUY, 100, new BigDecimal("1400.00"), "idem-12345", OrderType.LIMIT);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void zeroAccountIdFails() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                0L, "RELIANCE", OrderSide.BUY, 100, new BigDecimal("1400.00"), "idem-12345", OrderType.LIMIT);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void emptySymbolFails() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "", OrderSide.BUY, 100, new BigDecimal("1400.00"), "idem-12345", OrderType.LIMIT);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void longSymbolFails() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "ABCDEFGHIJKLMNOPQRSTU", OrderSide.BUY, 100, new BigDecimal("1400.00"), "idem-12345", OrderType.LIMIT);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void nullSideFails() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "RELIANCE", null, 100, new BigDecimal("1400.00"), "idem-12345", OrderType.LIMIT);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void nullQuantityFails() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "RELIANCE", OrderSide.BUY, null, new BigDecimal("1400.00"), "idem-12345", OrderType.LIMIT);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void zeroQuantityFails() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "RELIANCE", OrderSide.BUY, 0, new BigDecimal("1400.00"), "idem-12345", OrderType.LIMIT);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void oneQuantityPasses() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "RELIANCE", OrderSide.BUY, 1, new BigDecimal("1400.00"), "idem-12345", OrderType.LIMIT);
        assertTrue(validator.validate(r).isEmpty());
    }

    @Test
    void nullPriceFails() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "RELIANCE", OrderSide.BUY, 100, null, "idem-12345", OrderType.LIMIT);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void zeroPriceFails() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "RELIANCE", OrderSide.BUY, 100, BigDecimal.ZERO, "idem-12345", OrderType.LIMIT);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void oneCentPricePasses() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "RELIANCE", OrderSide.BUY, 100, new BigDecimal("0.01"), "idem-12345", OrderType.LIMIT);
        assertTrue(validator.validate(r).isEmpty());
    }

    @Test
    void extraPriceScaleFails() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "RELIANCE", OrderSide.BUY, 100, new BigDecimal("25.555"), "idem-12345", OrderType.LIMIT);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void shortKeyFails() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "RELIANCE", OrderSide.BUY, 100, new BigDecimal("1400.00"), "1234567", OrderType.LIMIT);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void goodKeyPasses() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "RELIANCE", OrderSide.BUY, 100, new BigDecimal("1400.00"), "12345678", OrderType.LIMIT);
        assertTrue(validator.validate(r).isEmpty());
    }

    @Test
    void longKeyFails() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "RELIANCE", OrderSide.BUY, 100, new BigDecimal("1400.00"), "a".repeat(101), OrderType.LIMIT);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void nullOrderTypeFails() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "RELIANCE", OrderSide.BUY, 100, new BigDecimal("1400.00"), "idem-12345", null);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void limitOrderPasses() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "RELIANCE", OrderSide.BUY, 100, new BigDecimal("1400.00"), "idem-12345", OrderType.LIMIT);
        assertTrue(validator.validate(r).isEmpty());
    }

    @Test
    void marketOrderWithoutPricePasses() {
        PlaceOrderRequest r = new PlaceOrderRequest(
                1L, "RELIANCE", OrderSide.BUY, 100, null, "idem-12345", OrderType.MARKET);
        assertTrue(validator.validate(r).isEmpty());
    }
}

