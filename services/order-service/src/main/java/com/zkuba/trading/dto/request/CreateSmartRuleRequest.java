package com.zkuba.trading.dto.request;

import com.zkuba.trading.domain.entity.SmartRule.ComparisonOperator;
import com.zkuba.trading.domain.entity.SmartRule.MetricType;
import com.zkuba.trading.domain.entity.SmartRule.QuantityMode;
import com.zkuba.trading.domain.entity.SmartRule.RuleAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record CreateSmartRuleRequest(

        @NotBlank
        String symbol,

        @NotNull
        RuleAction action,

        @NotNull
        QuantityMode quantityMode,

        Integer quantity,

        @NotNull
        MetricType metricType,

        @NotNull
        ComparisonOperator operator,

        BigDecimal targetValue,

        Integer lookbackPeriod,

        Integer secondaryLookbackPeriod,

        BigDecimal standardDeviationMultiplier,

        Boolean enabled,

        @Valid
        List<CreateSmartRuleRequest> children
) {}