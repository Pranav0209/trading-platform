package com.zkuba.executor.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.executor.client.CandleResponse;
import com.zkuba.executor.client.FauxnanceCandleClient;
import com.zkuba.executor.client.InternalOrderClient;
import com.zkuba.executor.domain.entity.Position;
import com.zkuba.executor.domain.entity.SmartRule;
import com.zkuba.executor.domain.entity.SmartRule.ComparisonOperator;
import com.zkuba.executor.domain.entity.SmartRule.MetricType;
import com.zkuba.executor.domain.entity.SmartRule.QuantityMode;
import com.zkuba.executor.domain.entity.SmartRule.RuleAction;
import com.zkuba.executor.repository.PositionRepository;
import com.zkuba.executor.repository.SmartRuleRepository;
import com.zkuba.executor.service.SmartRuleIndicatorCalculator;
import com.zkuba.executor.service.SmartRuleIndicatorCalculator.BollingerBandValues;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SmartRuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SmartRuleEvaluator.class);

    private final SmartRuleRepository smartRuleRepository;
    private final PositionRepository positionRepository;
    private final FauxnanceCandleClient candleClient;
    private final InternalOrderClient internalOrderClient;
    private final SmartRuleIndicatorCalculator indicatorCalculator;
    private final ObjectMapper objectMapper;

    public SmartRuleEvaluator(
            SmartRuleRepository smartRuleRepository,
            PositionRepository positionRepository,
            FauxnanceCandleClient candleClient,
            InternalOrderClient internalOrderClient,
            SmartRuleIndicatorCalculator indicatorCalculator,
            ObjectMapper objectMapper) {
        this.smartRuleRepository = smartRuleRepository;
        this.positionRepository = positionRepository;
        this.candleClient = candleClient;
        this.internalOrderClient = internalOrderClient;
        this.indicatorCalculator = indicatorCalculator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "market-data", groupId = "smart-rule-service")
    @Transactional
    public void evaluateRules(String message) {
        try {
            Map<?, ?> quote = objectMapper.readValue(message, Map.class);
            String symbol = (String) quote.get("symbol");
            Object priceObj = quote.get("price");
            Object previousCloseObj = quote.get("previousClose");

            if (symbol == null || priceObj == null) {
                return;
            }

            BigDecimal currentPrice = new BigDecimal(priceObj.toString()).setScale(4, RoundingMode.HALF_UP);
            BigDecimal previousClose = previousCloseObj != null
                    ? new BigDecimal(previousCloseObj.toString()).setScale(4, RoundingMode.HALF_UP)
                    : null;

            List<SmartRule> rules = smartRuleRepository.findBySymbolAndEnabledTrue(symbol);
            if (rules.isEmpty()) {
                return;
            }

            CandleResponse candleResponse = needsCandleData(rules) ? candleClient.getCandles(symbol) : null;
            List<CandleResponse.Candle> candles = candleResponse != null && candleResponse.data() != null
                    ? candleResponse.data().candles()
                    : null;

            for (SmartRule rule : rules) {
                evaluateRule(rule, currentPrice, previousClose, candles);
            }
        } catch (Exception exception) {
            log.error("Failed to evaluate smart rules from market-data message", exception);
        }
    }

    private void evaluateRule(SmartRule rule, BigDecimal currentPrice, BigDecimal previousClose, List<CandleResponse.Candle> candles) {
        if (!isEligible(rule)) {
            return;
        }

        Position position = positionRepository.findByAccountIdAndSymbol(rule.getAccount().getId(), rule.getSymbol())
                .orElse(null);
        boolean matches = conditionMatches(rule, currentPrice, previousClose, position, candles);

        if (matches && !rule.isConditionMet()) {
            triggerRule(rule, currentPrice, position);
        }

        rule.setConditionMet(matches);
        rule.setUpdatedAt(OffsetDateTime.now());
        smartRuleRepository.save(rule);
    }

    private boolean isEligible(SmartRule rule) {
        if (!rule.isEnabled()) {
            return false;
        }
        if (rule.getParentRule() == null) {
            return true;
        }
        return rule.getActivatedAt() != null && rule.getActivatedQuantity() != null && rule.getActivatedQuantity() > 0;
    }

    private boolean conditionMatches(
            SmartRule rule,
            BigDecimal currentPrice,
            BigDecimal previousClose,
            Position position,
            List<CandleResponse.Candle> candles) {
        return switch (rule.getMetricType()) {
            case PRICE -> compare(currentPrice, rule.getComparisonOperator(), rule.getTargetValue());
            case AVERAGE_COST -> position != null && position.getAverageCost() != null
                    && compare(position.getAverageCost(), rule.getComparisonOperator(), rule.getTargetValue());
            case PERCENT_CHANGE -> previousClose != null && previousClose.signum() > 0
                    && compare(percentChange(currentPrice, previousClose), rule.getComparisonOperator(), rule.getTargetValue());
            case MOVING_AVERAGE -> matchesMovingAverage(rule, currentPrice, candles);
            case MOVING_AVERAGE_CROSSOVER -> matchesMovingAverageCrossover(rule, candles);
            case BOLLINGER_BAND -> matchesBollingerBand(rule, currentPrice, candles);
            case STOP_LOSS -> matchesStopLoss(rule, currentPrice);
        };
    }

    private boolean matchesMovingAverage(SmartRule rule, BigDecimal currentPrice, List<CandleResponse.Candle> candles) {
        if (candles == null || rule.getLookbackPeriod() == null) {
            return false;
        }
        Double movingAverage = indicatorCalculator.calculateSma(candles, rule.getLookbackPeriod());
        return movingAverage != null
                && compare(BigDecimal.valueOf(movingAverage), rule.getComparisonOperator(), rule.getTargetValue() != null ? rule.getTargetValue() : currentPrice);
    }

    private boolean matchesMovingAverageCrossover(SmartRule rule, List<CandleResponse.Candle> candles) {
        if (candles == null || rule.getLookbackPeriod() == null || rule.getSecondaryLookbackPeriod() == null) {
            return false;
        }

        Double currentFast = indicatorCalculator.calculateSma(candles, rule.getLookbackPeriod());
        Double previousFast = indicatorCalculator.calculatePreviousSma(candles, rule.getLookbackPeriod());
        Double currentSlow = indicatorCalculator.calculateSma(candles, rule.getSecondaryLookbackPeriod());
        Double previousSlow = indicatorCalculator.calculatePreviousSma(candles, rule.getSecondaryLookbackPeriod());

        if (currentFast == null || previousFast == null || currentSlow == null || previousSlow == null) {
            return false;
        }

        if (rule.getComparisonOperator() == ComparisonOperator.CROSS_ABOVE) {
            return previousFast <= previousSlow && currentFast > currentSlow;
        }
        return previousFast >= previousSlow && currentFast < currentSlow;
    }

    private boolean matchesBollingerBand(SmartRule rule, BigDecimal currentPrice, List<CandleResponse.Candle> candles) {
        if (candles == null || rule.getLookbackPeriod() == null) {
            return false;
        }

        BigDecimal multiplier = rule.getStandardDeviationMultiplier() != null
                ? rule.getStandardDeviationMultiplier()
                : BigDecimal.valueOf(2.0);
        BollingerBandValues bollinger = indicatorCalculator.calculateBollingerBands(
                candles,
                rule.getLookbackPeriod(),
                multiplier.doubleValue());
        if (bollinger == null) {
            return false;
        }

        if (rule.getComparisonOperator() == ComparisonOperator.TOUCH_LOWER) {
            return currentPrice.compareTo(BigDecimal.valueOf(bollinger.lower())) <= 0;
        }
        return currentPrice.compareTo(BigDecimal.valueOf(bollinger.upper())) >= 0;
    }

    private boolean matchesStopLoss(SmartRule rule, BigDecimal currentPrice) {
        if (rule.getActivationReferencePrice() == null || rule.getTargetValue() == null) {
            return false;
        }
        BigDecimal stopPrice = rule.getActivationReferencePrice()
                .multiply(BigDecimal.ONE.subtract(rule.getTargetValue().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
        return currentPrice.compareTo(stopPrice) <= 0;
    }

    private void triggerRule(SmartRule rule, BigDecimal currentPrice, Position position) {
        int quantity = resolveQuantity(rule, position);
        if (quantity <= 0) {
            log.debug("Skipping smart rule {} because no executable quantity is available", rule.getId());
            return;
        }

        try {
            internalOrderClient.placeMarketOrder(
                    rule.getAccount().getUserId(),
                    rule.getAccount().getId(),
                    rule.getSymbol(),
                    rule.getAction() == RuleAction.BUY ? com.zkuba.executor.domain.enums.OrderSide.BUY : com.zkuba.executor.domain.enums.OrderSide.SELL,
                    quantity,
                    "smart-rule-" + rule.getId() + "-" + UUID.randomUUID());
            rule.setLastTriggeredAt(OffsetDateTime.now());

            if (rule.getParentRule() != null && rule.getAction() == RuleAction.SELL) {
                int remainingQuantity = Math.max(0, (rule.getActivatedQuantity() != null ? rule.getActivatedQuantity() : 0) - quantity);
                rule.setActivatedQuantity(remainingQuantity == 0 ? null : remainingQuantity);
                if (remainingQuantity == 0) {
                    rule.setActivatedAt(null);
                    rule.setActivationReferencePrice(null);
                    rule.setConditionMet(false);
                }
            }

            for (SmartRule child : rule.getChildren()) {
                child.setActivatedAt(OffsetDateTime.now());
                child.setActivationReferencePrice(currentPrice);
                child.setActivatedQuantity(quantity);
                child.setConditionMet(false);
                child.setUpdatedAt(OffsetDateTime.now());
            }

            log.info("Triggered smart rule {} for {} {} shares of {}", rule.getId(), rule.getAction(), quantity, rule.getSymbol());
        } catch (Exception exception) {
            log.error("Failed to place internal order for smart rule {}", rule.getId(), exception);
        }
    }

    private int resolveQuantity(SmartRule rule, Position position) {
        if (rule.getAction() == RuleAction.BUY) {
            return rule.getQuantityValue() != null ? rule.getQuantityValue() : 0;
        }

        int availableQuantity = 0;
        if (rule.getActivatedQuantity() != null && rule.getActivatedQuantity() > 0) {
            availableQuantity = rule.getActivatedQuantity();
        } else if (position != null) {
            availableQuantity = position.getQuantity();
        }

        if (availableQuantity <= 0) {
            return 0;
        }

        return switch (rule.getQuantityMode()) {
            case ALL -> availableQuantity;
            case HALF -> Math.max(1, availableQuantity / 2);
            case FIXED -> rule.getQuantityValue() != null && rule.getQuantityValue() <= availableQuantity
                    ? rule.getQuantityValue()
                    : 0;
        };
    }

    private boolean compare(BigDecimal left, ComparisonOperator operator, BigDecimal target) {
        if (left == null || target == null) {
            return false;
        }
        return switch (operator) {
            case GREATER_THAN -> left.compareTo(target) > 0;
            case LESS_THAN -> left.compareTo(target) < 0;
            case EQUAL_TO -> left.compareTo(target) == 0;
            default -> false;
        };
    }

    private BigDecimal percentChange(BigDecimal currentPrice, BigDecimal previousClose) {
        return currentPrice.subtract(previousClose)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousClose, 4, RoundingMode.HALF_UP);
    }

    private boolean needsCandleData(List<SmartRule> rules) {
        return rules.stream().anyMatch(rule -> rule.getMetricType() == MetricType.MOVING_AVERAGE
                || rule.getMetricType() == MetricType.MOVING_AVERAGE_CROSSOVER
                || rule.getMetricType() == MetricType.BOLLINGER_BAND);
    }
}