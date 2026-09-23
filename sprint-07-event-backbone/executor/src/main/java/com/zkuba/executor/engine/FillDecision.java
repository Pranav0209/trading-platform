package com.zkuba.executor.engine;

import java.math.BigDecimal;

public record FillDecision(
        boolean isFilled,
        BigDecimal executedPrice,
        String rejectReason
) {
    public static FillDecision filled(BigDecimal price) {
        return new FillDecision(true, price, null);
    }

    public static FillDecision rejected(String reason) {
        return new FillDecision(false, null, reason);
    }
}
