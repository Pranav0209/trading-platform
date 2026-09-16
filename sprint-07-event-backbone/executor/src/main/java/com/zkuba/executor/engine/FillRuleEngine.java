package com.zkuba.executor.engine;

import com.zkuba.executor.client.QuoteResponse;
import com.zkuba.executor.domain.entity.Order;
import com.zkuba.executor.domain.enums.OrderSide;
import com.zkuba.executor.domain.enums.OrderType;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FillRuleEngine {

    public FillDecision evaluate(Order order, QuoteResponse.Data quote) {
        if (quote == null) {
            return FillDecision.rejected("PRICING_UNAVAILABLE");
        }

        BigDecimal executionPrice;
        if (order.getSide() == OrderSide.BUY) {
            if (quote.ask() == null) {
                return FillDecision.rejected("NO_ASK_PRICE");
            }
            executionPrice = quote.ask().setScale(4, RoundingMode.HALF_UP);
            
            if (order.getOrderType() == OrderType.LIMIT) {
                if (order.getLimitPrice() != null && order.getLimitPrice().compareTo(executionPrice) < 0) {
                    return FillDecision.rejected("PRICE_NOT_MET");
                }
            }
        } else {
            if (quote.bid() == null) {
                return FillDecision.rejected("NO_BID_PRICE");
            }
            executionPrice = quote.bid().setScale(4, RoundingMode.HALF_UP);
            
            if (order.getOrderType() == OrderType.LIMIT) {
                if (order.getLimitPrice() != null && order.getLimitPrice().compareTo(executionPrice) > 0) {
                    return FillDecision.rejected("PRICE_NOT_MET");
                }
            }
        }

        return FillDecision.filled(executionPrice);
    }
}
