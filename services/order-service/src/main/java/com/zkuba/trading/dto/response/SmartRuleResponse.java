package com.zkuba.trading.dto.response;

import com.zkuba.trading.domain.entity.SmartRule;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record SmartRuleResponse(
        Long id,
        String symbol,
        String instrumentName,
        String action,
        String quantityMode,
        Integer quantity,
        String metricType,
        String operator,
        BigDecimal targetValue,
        Integer lookbackPeriod,
        Integer secondaryLookbackPeriod,
        BigDecimal standardDeviationMultiplier,
        boolean enabled,
        BigDecimal activationReferencePrice,
        Integer activatedQuantity,
        OffsetDateTime activatedAt,
        OffsetDateTime lastTriggeredAt,
        List<SmartRuleResponse> children
) {
    public static SmartRuleResponse fromEntity(SmartRule rule, String instrumentName, List<SmartRuleResponse> children) {
        return new SmartRuleResponse(
                rule.getId(),
                rule.getSymbol(),
                instrumentName,
                rule.getAction().name(),
                rule.getQuantityMode().name(),
                rule.getQuantityValue(),
                rule.getMetricType().name(),
                rule.getComparisonOperator().name(),
                rule.getTargetValue(),
                rule.getLookbackPeriod(),
                rule.getSecondaryLookbackPeriod(),
                rule.getStandardDeviationMultiplier(),
                rule.isEnabled(),
                rule.getActivationReferencePrice(),
                rule.getActivatedQuantity(),
                rule.getActivatedAt(),
                rule.getLastTriggeredAt(),
                children);
    }
}