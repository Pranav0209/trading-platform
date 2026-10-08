package com.zkuba.executor.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * RealisedPnl represents a locked-in profit or loss from a completed SELL order.
 * Each SELL order creates at most one P&L record.
 *
 * The average_cost is captured BEFORE the SELL order updates the position,
 * and the P&L is calculated as: (sell_price - average_cost) * quantity.
 *
 * When a SELL closes a position (qty becomes 0), the position row is deleted
 * from the positions table, but this P&L record preserves the historical cost basis.
 */
@Entity
@Table(name = "pnl")
public class RealisedPnl {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "sell_price", nullable = false)
    private BigDecimal sellPrice;

    @Column(name = "average_cost", nullable = false)
    private BigDecimal averageCost;

    @Column(name = "realised_pnl", nullable = false)
    private BigDecimal realisedPnl;

    @Column(name = "realised_at", nullable = false)
    private OffsetDateTime realisedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public RealisedPnl() {
    }

    public RealisedPnl(UUID orderId, Long accountId, String symbol, Integer quantity,
            BigDecimal sellPrice, BigDecimal averageCost, BigDecimal realisedPnl,
            OffsetDateTime realisedAt) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.sellPrice = sellPrice;
        this.averageCost = averageCost;
        this.realisedPnl = realisedPnl;
        this.realisedAt = realisedAt;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(BigDecimal sellPrice) {
        this.sellPrice = sellPrice;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }

    public BigDecimal getRealisedPnl() {
        return realisedPnl;
    }

    public void setRealisedPnl(BigDecimal realisedPnl) {
        this.realisedPnl = realisedPnl;
    }

    public OffsetDateTime getRealisedAt() {
        return realisedAt;
    }

    public void setRealisedAt(OffsetDateTime realisedAt) {
        this.realisedAt = realisedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
