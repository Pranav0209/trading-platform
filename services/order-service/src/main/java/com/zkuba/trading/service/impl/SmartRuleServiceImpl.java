package com.zkuba.trading.service.impl;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Instrument;
import com.zkuba.trading.domain.entity.SmartRule;
import com.zkuba.trading.domain.entity.SmartRule.ComparisonOperator;
import com.zkuba.trading.domain.entity.SmartRule.MetricType;
import com.zkuba.trading.domain.entity.SmartRule.QuantityMode;
import com.zkuba.trading.domain.entity.SmartRule.RuleAction;
import com.zkuba.trading.dto.request.CreateSmartRuleRequest;
import com.zkuba.trading.dto.response.SmartRuleResponse;
import com.zkuba.trading.exception.AccountNotFoundException;
import com.zkuba.trading.exception.InstrumentNotFoundException;
import com.zkuba.trading.exception.SmartRuleNotFoundException;
import com.zkuba.trading.exception.SmartRuleValidationException;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.InstrumentRepository;
import com.zkuba.trading.repository.SmartRuleRepository;
import com.zkuba.trading.service.SmartRuleService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SmartRuleServiceImpl implements SmartRuleService {

    private final SmartRuleRepository smartRuleRepository;
    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;

    public SmartRuleServiceImpl(
            SmartRuleRepository smartRuleRepository,
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository) {
        this.smartRuleRepository = smartRuleRepository;
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmartRuleResponse> getRules(Long accountId) {
        accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        return smartRuleRepository.findByAccountIdAndParentRuleIsNullOrderByCreatedAtDesc(accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public SmartRuleResponse createRule(Long accountId, CreateSmartRuleRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        SmartRule rule = toEntity(account, null, request);
        SmartRule saved = smartRuleRepository.save(rule);
        return toResponse(saved);
    }

    @Override
    public void deleteRule(Long accountId, Long ruleId) {
        SmartRule rule = smartRuleRepository.findByIdAndAccountId(ruleId, accountId)
                .orElseThrow(() -> new SmartRuleNotFoundException(ruleId));
        smartRuleRepository.delete(rule);
    }

    private SmartRule toEntity(Account account, SmartRule parentRule, CreateSmartRuleRequest request) {
        String normalizedSymbol = request.symbol().trim().toUpperCase(Locale.ROOT);
        Instrument instrument = instrumentRepository.findById(normalizedSymbol)
                .orElseThrow(() -> new InstrumentNotFoundException(normalizedSymbol));
        if (!instrument.isTradable()) {
            throw new InstrumentNotFoundException(normalizedSymbol);
        }

        validateRequest(request, parentRule, normalizedSymbol);

        SmartRule rule = new SmartRule();
        rule.setAccount(account);
        rule.setParentRule(parentRule);
        rule.setSymbol(normalizedSymbol);
        rule.setAction(request.action());
        rule.setQuantityMode(request.quantityMode());
        rule.setQuantityValue(request.quantity());
        rule.setMetricType(request.metricType());
        rule.setComparisonOperator(request.operator());
        rule.setTargetValue(request.targetValue());
        rule.setLookbackPeriod(request.lookbackPeriod());
        rule.setSecondaryLookbackPeriod(request.secondaryLookbackPeriod());
        rule.setStandardDeviationMultiplier(request.standardDeviationMultiplier() != null
                ? request.standardDeviationMultiplier()
                : defaultStandardDeviation(request.metricType()));
        rule.setEnabled(request.enabled() == null || request.enabled());
        rule.setCreatedAt(OffsetDateTime.now());
        rule.setUpdatedAt(OffsetDateTime.now());

        if (request.children() != null) {
            for (CreateSmartRuleRequest childRequest : request.children()) {
                SmartRule child = toEntity(account, rule, childRequest);
                rule.getChildren().add(child);
            }
        }

        return rule;
    }

    private void validateRequest(CreateSmartRuleRequest request, SmartRule parentRule, String normalizedSymbol) {
        if (request.action() == RuleAction.BUY && request.quantityMode() != QuantityMode.FIXED) {
            throw new SmartRuleValidationException("Buy rules must use a fixed quantity");
        }

        if (request.action() == RuleAction.BUY && request.metricType() == MetricType.AVERAGE_COST) {
            throw new SmartRuleValidationException("Average price rules are available only for sell rules");
        }

        if (request.action() == RuleAction.SELL && request.metricType() == MetricType.PRICE) {
            throw new SmartRuleValidationException("Price rules are available only for buy rules");
        }

        if (request.quantityMode() == QuantityMode.FIXED) {
            if (request.quantity() == null || request.quantity() < 1) {
                throw new SmartRuleValidationException("Fixed quantity rules must include a quantity greater than zero");
            }
        } else if (request.quantity() != null) {
            throw new SmartRuleValidationException("Half and all quantity modes must omit explicit quantity");
        }

        if (parentRule != null) {
            if (!normalizedSymbol.equals(parentRule.getSymbol())) {
                throw new SmartRuleValidationException("Child rules must target the same symbol as the parent rule");
            }
            if (request.action() == parentRule.getAction()) {
                throw new SmartRuleValidationException("Child rules must be the opposite side of the parent rule");
            }
        }

        if (request.metricType() == MetricType.MOVING_AVERAGE || request.metricType() == MetricType.BOLLINGER_BAND
                || request.metricType() == MetricType.MOVING_AVERAGE_CROSSOVER) {
            if (request.lookbackPeriod() == null || request.lookbackPeriod() < 2) {
                throw new SmartRuleValidationException("Lookback period must be at least 2 for indicator-based rules");
            }
        }

        if (request.metricType() == MetricType.MOVING_AVERAGE_CROSSOVER) {
            if (request.secondaryLookbackPeriod() == null || request.secondaryLookbackPeriod() < 2) {
                throw new SmartRuleValidationException("Moving-average crossover rules require a secondary lookback period");
            }
            if (request.operator() != ComparisonOperator.CROSS_ABOVE
                    && request.operator() != ComparisonOperator.CROSS_BELOW) {
                throw new SmartRuleValidationException("Moving-average crossover rules must use CROSS_ABOVE or CROSS_BELOW");
            }
        }

        if (request.metricType() == MetricType.BOLLINGER_BAND) {
            if (request.operator() != ComparisonOperator.TOUCH_UPPER
                    && request.operator() != ComparisonOperator.TOUCH_LOWER) {
                throw new SmartRuleValidationException("Bollinger-band rules must use TOUCH_UPPER or TOUCH_LOWER");
            }
        }

        if (request.metricType() == MetricType.STOP_LOSS) {
            if (parentRule == null) {
                throw new SmartRuleValidationException("Stop-loss rules must be chained beneath a parent rule");
            }
            if (request.action() != RuleAction.SELL) {
                throw new SmartRuleValidationException("Stop-loss rules must be sell rules");
            }
        }

        if (requiresTargetValue(request.metricType())) {
            if (request.targetValue() == null) {
                throw new SmartRuleValidationException("This rule type requires a target value");
            }
        }

        if (request.targetValue() != null
                && request.metricType() != MetricType.PERCENT_CHANGE
                && request.targetValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SmartRuleValidationException("Target value must be greater than zero");
        }

        if (request.metricType() == MetricType.PERCENT_CHANGE
                && request.targetValue() != null
                && request.targetValue().compareTo(BigDecimal.ZERO) == 0) {
            throw new SmartRuleValidationException("Percent change target must be non-zero");
        }
    }

    private boolean requiresTargetValue(MetricType metricType) {
        return metricType != MetricType.BOLLINGER_BAND;
    }

    private BigDecimal defaultStandardDeviation(MetricType metricType) {
        if (metricType == MetricType.BOLLINGER_BAND) {
            return BigDecimal.valueOf(2.0);
        }
        return null;
    }

    private SmartRuleResponse toResponse(SmartRule rule) {
        String instrumentName = instrumentRepository.findById(rule.getSymbol())
                .map(Instrument::getDisplayName)
                .orElse(rule.getSymbol());
        List<SmartRuleResponse> children = rule.getChildren().stream()
                .map(this::toResponse)
                .toList();
        return SmartRuleResponse.fromEntity(rule, instrumentName, children);
    }
}