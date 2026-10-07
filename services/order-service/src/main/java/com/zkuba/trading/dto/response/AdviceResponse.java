package com.zkuba.trading.dto.response;

import java.time.Instant;
import java.util.List;

public record AdviceResponse(
        Long accountId,
        Instant generatedAt,
        List<SignalResponse> signals
) {}
