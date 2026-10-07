package com.zkuba.trading.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "price_alerts", indexes = {
    @Index(name = "idx_price_alerts_account_state", columnList = "account_id,state"),
    @Index(name = "idx_price_alerts_account_symbol", columnList = "account_id,symbol"),
    @Index(name = "idx_price_alerts_account", columnList = "account_id"),
    @Index(name = "idx_price_alerts_created", columnList = "created_at DESC")
})
public class PriceAlert {

    public enum Direction {
        ABOVE, BELOW
    }

    public enum AlertState {
        ACTIVE,      // Monitoring prices, will fire when threshold is crossed
        TRIGGERED,   // Threshold was crossed, notification sent, awaiting reset
        DORMANT      // After triggering, paused until customer resets
    }

    public enum DeliveryStatus {
        PENDING,     // Notification delivery attempt pending
        DELIVERED,   // Notification successfully delivered
        FAILED       // Notification delivery failed
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 5)
    private Direction direction;

    @Column(name = "threshold", nullable = false)
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private AlertState state = AlertState.ACTIVE;

    @Column(name = "last_fired_at")
    private OffsetDateTime lastFiredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    // Constructors
    public PriceAlert() {}

    public PriceAlert(Account account, String symbol, Direction direction, BigDecimal threshold) {
        this.account = account;
        this.symbol = symbol;
        this.direction = direction;
        this.threshold = threshold;
        this.state = AlertState.ACTIVE;
        this.deliveryStatus = DeliveryStatus.PENDING;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
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

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public void setThreshold(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public AlertState getState() {
        return state;
    }

    public void setState(AlertState state) {
        this.state = state;
    }

    public OffsetDateTime getLastFiredAt() {
        return lastFiredAt;
    }

    public void setLastFiredAt(OffsetDateTime lastFiredAt) {
        this.lastFiredAt = lastFiredAt;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
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
