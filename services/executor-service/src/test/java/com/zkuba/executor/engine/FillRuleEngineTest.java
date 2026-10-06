package com.zkuba.executor.engine;

import com.zkuba.executor.client.QuoteResponse;
import com.zkuba.executor.domain.entity.Order;
import com.zkuba.executor.domain.enums.OrderSide;
import com.zkuba.executor.domain.enums.OrderType;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class FillRuleEngineTest {

    private final FillRuleEngine engine = new FillRuleEngine();

    @Test
    void buyLimitAtOrAboveQuoteFillsAtQuote() {
        Order order = new Order();
        order.setSide(OrderSide.BUY);
        order.setOrderType(OrderType.LIMIT);
        order.setLimitPrice(new BigDecimal("150.00"));

        QuoteResponse.Data quote = new QuoteResponse.Data(
            "AAPL", new BigDecimal("149.00"), new BigDecimal("148.95"), new BigDecimal("149.05"),
            "USD", null, null, null, null, null
        );

        FillDecision decision = engine.evaluate(order, quote);

        assertTrue(decision.isFilled());
        assertEquals(new BigDecimal("149.0500"), decision.executedPrice());
    }

    @Test
    void sellLimitAtOrBelowQuoteFillsAtQuote() {
        Order order = new Order();
        order.setSide(OrderSide.SELL);
        order.setOrderType(OrderType.LIMIT);
        order.setLimitPrice(new BigDecimal("148.00"));

        QuoteResponse.Data quote = new QuoteResponse.Data(
            "AAPL", new BigDecimal("149.00"), new BigDecimal("148.95"), new BigDecimal("149.05"),
            "USD", null, null, null, null, null
        );

        FillDecision decision = engine.evaluate(order, quote);

        assertTrue(decision.isFilled());
        assertEquals(new BigDecimal("148.9500"), decision.executedPrice());
    }

    @Test
    void orderOutsideMarketableRangeIsRejected() {
        Order order = new Order();
        order.setSide(OrderSide.BUY);
        order.setOrderType(OrderType.LIMIT);
        order.setLimitPrice(new BigDecimal("140.00"));

        QuoteResponse.Data quote = new QuoteResponse.Data(
            "AAPL", new BigDecimal("149.00"), new BigDecimal("148.95"), new BigDecimal("149.05"),
            "USD", null, null, null, null, null
        );

        FillDecision decision = engine.evaluate(order, quote);

        assertFalse(decision.isFilled());
        assertEquals("PRICE_NOT_MET", decision.rejectReason());
    }
}
