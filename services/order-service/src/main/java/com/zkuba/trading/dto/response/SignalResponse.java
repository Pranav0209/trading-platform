package com.zkuba.trading.dto.response;

import com.zkuba.trading.domain.enums.SignalSource;
import com.zkuba.trading.domain.enums.SignalType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SignalResponse(
        String symbol,
        SignalSource source,
        SignalType signal,
        Integer score,
        List<String> reasons,
        Map<String, Object> indicatorValues,
        boolean stale,
        Instant priceTimestamp
) {}
