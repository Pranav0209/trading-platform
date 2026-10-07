package com.zkuba.trading.dto.response;

import com.zkuba.trading.domain.entity.PriceAlert;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AlertResponse(
        Long id,
        Long accountId,
        String symbol,
        String displayName,
        String direction,
        BigDecimal threshold,
        String state,
        OffsetDateTime lastFiredAt,
        String deliveryStatus,
        String failureReason,
        OffsetDateTime createdAt
) {
    public static AlertResponse fromEntity(PriceAlert alert, String displayName) {
        return new AlertResponse(
                alert.getId(),
                alert.getAccount().getId(),
                alert.getSymbol(),
                displayName,
                alert.getDirection().name(),
                alert.getThreshold(),
                alert.getState().name(),
                alert.getLastFiredAt(),
                alert.getDeliveryStatus().name(),
                alert.getFailureReason(),
                alert.getCreatedAt()
        );
    }
}
