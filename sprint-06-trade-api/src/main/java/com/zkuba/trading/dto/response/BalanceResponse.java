package com.zkuba.trading.dto.response;

import java.math.BigDecimal;

public record BalanceResponse(
        Long accountId,
        BigDecimal cashBalance,
        String currency,
        String asOf
) {}
