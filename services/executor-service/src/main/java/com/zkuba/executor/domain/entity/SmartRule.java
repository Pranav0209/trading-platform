package com.zkuba.executor.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "smart_rules")
public class SmartRule {

    public enum RuleAction {
        BUY, SELL
    }

    public enum QuantityMode {
        FIXED, HALF, ALL
    }

    public enum MetricType {
        PRICE,
        AVERAGE_COST,
        MOVING_AVERAGE,
        PERCENT_CHANGE,
        MOVING_AVERAGE_CROSSOVER,
        BOLLINGER_BAND,
        STOP_LOSS
    }

    public enum ComparisonOperator {
        GREATER_THAN,
        LESS_THAN,
        EQUAL_TO,
        CROSS_ABOVE,
        CROSS_BELOW,
        TOUCH_UPPER,
        TOUCH_LOWER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_rule_id")
    private SmartRule parentRule;

    @OneToMany(mappedBy = "parentRule", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<SmartRule> children = new ArrayList<>();

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private RuleAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "quantity_mode", nullable = false)
    private QuantityMode quantityMode;

    @Column(name = "quantity_value")
    private Integer quantityValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false)
    private MetricType metricType;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_operator", nullable = false)
    private ComparisonOperator comparisonOperator;

    @Column(name = "target_value")
    private BigDecimal targetValue;

    @Column(name = "lookback_period")
    private Integer lookbackPeriod;

    @Column(name = "secondary_lookback_period")
    private Integer secondaryLookbackPeriod;

    @Column(name = "std_deviation_multiplier")
    private BigDecimal standardDeviationMultiplier;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "condition_met", nullable = false)
    private boolean conditionMet;

    @Column(name = "activation_reference_price")
    private BigDecimal activationReferencePrice;

    @Column(name = "activated_quantity")
    private Integer activatedQuantity;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "last_triggered_at")
    private OffsetDateTime lastTriggeredAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public SmartRule getParentRule() {
        return parentRule;
    }

    public void setParentRule(SmartRule parentRule) {
        this.parentRule = parentRule;
    }

    public List<SmartRule> getChildren() {
        return children;
    }

    public void setChildren(List<SmartRule> children) {
        this.children = children;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public RuleAction getAction() {
        return action;
    }

    public void setAction(RuleAction action) {
        this.action = action;
    }

    public QuantityMode getQuantityMode() {
        return quantityMode;
    }

    public void setQuantityMode(QuantityMode quantityMode) {
        this.quantityMode = quantityMode;
    }

    public Integer getQuantityValue() {
        return quantityValue;
    }

    public void setQuantityValue(Integer quantityValue) {
        this.quantityValue = quantityValue;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public void setMetricType(MetricType metricType) {
        this.metricType = metricType;
    }

    public ComparisonOperator getComparisonOperator() {
        return comparisonOperator;
    }

    public void setComparisonOperator(ComparisonOperator comparisonOperator) {
        this.comparisonOperator = comparisonOperator;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(BigDecimal targetValue) {
        this.targetValue = targetValue;
    }

    public Integer getLookbackPeriod() {
        return lookbackPeriod;
    }

    public void setLookbackPeriod(Integer lookbackPeriod) {
        this.lookbackPeriod = lookbackPeriod;
    }

    public Integer getSecondaryLookbackPeriod() {
        return secondaryLookbackPeriod;
    }

    public void setSecondaryLookbackPeriod(Integer secondaryLookbackPeriod) {
        this.secondaryLookbackPeriod = secondaryLookbackPeriod;
    }

    public BigDecimal getStandardDeviationMultiplier() {
        return standardDeviationMultiplier;
    }

    public void setStandardDeviationMultiplier(BigDecimal standardDeviationMultiplier) {
        this.standardDeviationMultiplier = standardDeviationMultiplier;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isConditionMet() {
        return conditionMet;
    }

    public void setConditionMet(boolean conditionMet) {
        this.conditionMet = conditionMet;
    }

    public BigDecimal getActivationReferencePrice() {
        return activationReferencePrice;
    }

    public void setActivationReferencePrice(BigDecimal activationReferencePrice) {
        this.activationReferencePrice = activationReferencePrice;
    }

    public Integer getActivatedQuantity() {
        return activatedQuantity;
    }

    public void setActivatedQuantity(Integer activatedQuantity) {
        this.activatedQuantity = activatedQuantity;
    }

    public OffsetDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(OffsetDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public OffsetDateTime getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public void setLastTriggeredAt(OffsetDateTime lastTriggeredAt) {
        this.lastTriggeredAt = lastTriggeredAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}