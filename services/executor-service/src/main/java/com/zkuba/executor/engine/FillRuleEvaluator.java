package com.zkuba.executor.engine;

import com.zkuba.executor.client.QuoteResponse;
import com.zkuba.executor.domain.entity.Order;

/**
 * Interface for fill rule engine to enable better testability and mocking.
 * Evaluates whether an order can be filled based on quote data.
 */
public interface FillRuleEvaluator {

    /**
     * Evaluate whether an order can be filled at the given quote
     * @param order the order to evaluate
     * @param quote the quote data for the security
     * @return fill decision with approval/rejection reason
     */
    FillDecision evaluate(Order order, QuoteResponse.Data quote);
}
