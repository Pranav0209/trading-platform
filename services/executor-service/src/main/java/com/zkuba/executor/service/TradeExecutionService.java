package com.zkuba.executor.service;

import com.zkuba.executor.client.CurrencyConverter;
import com.zkuba.executor.client.QuoteClient;
import com.zkuba.executor.client.PricingUnavailableException;
import com.zkuba.executor.client.QuoteResponse;
import com.zkuba.executor.domain.entity.Account;
import com.zkuba.executor.domain.entity.Order;
import com.zkuba.executor.domain.entity.Position;
import com.zkuba.executor.domain.entity.RealisedPnl;
import com.zkuba.executor.engine.FillDecision;
import com.zkuba.executor.engine.FillRuleEvaluator;
import com.zkuba.executor.event.TradeEventPayload;
import com.zkuba.executor.repository.AccountRepository;
import com.zkuba.executor.repository.OrderRepository;
import com.zkuba.executor.repository.PositionRepository;
import com.zkuba.executor.repository.RealisedPnlRepository;
import com.zkuba.executor.domain.enums.AccountStatus;
import com.zkuba.executor.domain.enums.OrderSide;
import com.zkuba.executor.domain.enums.OrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.dao.OptimisticLockingFailureException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class TradeExecutionService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private RealisedPnlRepository realisedPnlRepository;

    @Autowired
    private QuoteClient quoteClient;

    @Autowired
    private FillRuleEvaluator fillRuleEngine;

    @Autowired
    private CurrencyConverter currencyConversionClient;

    @Autowired
    @Lazy
    private TradeExecutionService self;

    void setSelf(TradeExecutionService self) {
        this.self = self;
    }

    public Optional<TradeEventPayload> executeOrder(UUID orderId) {
        return executeLimitOrder(orderId, null);
    }

    public Optional<TradeEventPayload> executeLimitOrder(UUID orderId, QuoteResponse.Data quoteData) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.NEW) {
            return Optional.empty();
        }

        Account account = accountRepository.findById(order.getAccountId()).orElseThrow();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return self.rejectInTransaction(order, "ACCOUNT_SUSPENDED");
        }

        if (quoteData == null) {
            try {
                QuoteResponse quoteResponse = quoteClient.getQuote(order.getSymbol());
                quoteData = quoteResponse.data();
            } catch (PricingUnavailableException e) {
                return self.rejectInTransaction(order, "PRICING_UNAVAILABLE");
            }
        }

        BigDecimal conversionRate = BigDecimal.ONE;
        try {
            String quoteCurrency = quoteData.currency();
            if (quoteCurrency != null && !quoteCurrency.equalsIgnoreCase(account.getCurrency())) {
                conversionRate = currencyConversionClient.getConversionRate(quoteCurrency, account.getCurrency());
            }
        } catch (PricingUnavailableException e) {
            return self.rejectInTransaction(order, "PRICING_UNAVAILABLE");
        }

        return self.processExecutionInTransaction(orderId, quoteData, conversionRate);
    }

    @Transactional
    public Optional<TradeEventPayload> rejectInTransaction(Order order, String reason) {
        return Optional.of(rejectOrder(order, reason));
    }

    @Retryable(retryFor = {
            OptimisticLockException.class, OptimisticLockingFailureException.class }, maxAttempts = 5, backoff = @Backoff(delay = 50, multiplier = 2.0))
    @Transactional
    public Optional<TradeEventPayload> processExecutionInTransaction(UUID orderId, QuoteResponse.Data quoteData, BigDecimal conversionRate) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.NEW) {
            return Optional.empty();
        }

        Account account = accountRepository.findById(order.getAccountId()).orElseThrow();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return Optional.of(rejectOrder(order, "ACCOUNT_SUSPENDED"));
        }

        Position position = positionRepository.findByAccountIdAndSymbol(account.getId(), order.getSymbol())
                .orElse(null);

        if (order.getSide() == OrderSide.SELL) {
            int currentQty = position != null ? position.getQuantity() : 0;
            if (currentQty < order.getQuantity()) {
                return Optional.of(rejectOrder(order, "INSUFFICIENT_HOLDINGS"));
            }
        }

        FillDecision decision = fillRuleEngine.evaluate(order, quoteData);

        if (!decision.isFilled()) {
            if ("PRICE_NOT_MET".equals(decision.rejectReason())) {
                return Optional.empty();
            }
            return Optional.of(rejectOrder(order, decision.rejectReason()));
        }

        BigDecimal executedPrice = decision.executedPrice();
        BigDecimal totalValueInStockCurrency = executedPrice.multiply(BigDecimal.valueOf(order.getQuantity()));
        BigDecimal totalValueInAccountCurrency = totalValueInStockCurrency.multiply(conversionRate);
        BigDecimal executedPriceInAccountCurrency = totalValueInAccountCurrency.divide(BigDecimal.valueOf(order.getQuantity()), 4, RoundingMode.HALF_UP);

        if (order.getSide() == OrderSide.BUY && account.getCashBalance().compareTo(totalValueInAccountCurrency) < 0) {
            return Optional.of(rejectOrder(order, "INSUFFICIENT_FUNDS"));
        }

        // GUARDED STATE TRANSITION: Must be the FIRST write in the transaction
        OffsetDateTime executedOn = OffsetDateTime.now();
        int updated = orderRepository.executeOrder(order.getId(), OrderStatus.FILLED, executedPriceInAccountCurrency, executedOn, null);
        if (updated == 0) {
            // Another thread/delivery got here first
            throw new OptimisticLockException("Order already executed");
        }

        order.setStatus(OrderStatus.FILLED);
        order.setExecutedPrice(executedPriceInAccountCurrency);
        order.setExecutedOn(executedOn);

        if (order.getSide() == OrderSide.BUY) {
            BigDecimal newBalance = account.getCashBalance().subtract(totalValueInAccountCurrency);
            updateAccount(account, newBalance);
            updateBuyPosition(account.getId(), order.getSymbol(), order.getQuantity(), executedPriceInAccountCurrency, position);
        } else {
            // For SELL orders: capture average cost BEFORE updating position
            // (position row is deleted when qty reaches 0, so we must record P&L now)
            BigDecimal averageCostBeforeSell = position != null ? position.getAverageCost() : BigDecimal.ZERO;
            
            BigDecimal newBalance = account.getCashBalance().add(totalValueInAccountCurrency);
            updateAccount(account, newBalance);
            updateSellPosition(position, order.getQuantity());
            
            // Record realised P&L in same transaction
            BigDecimal realisedPnl = executedPriceInAccountCurrency.subtract(averageCostBeforeSell)
                    .multiply(BigDecimal.valueOf(order.getQuantity()));
            RealisedPnl pnlRecord = new RealisedPnl(
                    order.getId(),
                    account.getId(),
                    order.getSymbol(),
                    order.getQuantity(),
                    executedPriceInAccountCurrency,
                    averageCostBeforeSell,
                    realisedPnl,
                    executedOn
            );
            realisedPnlRepository.save(pnlRecord);
        }

        return Optional.of(buildFilledEvent(order, account.getCashBalance(), position, totalValueInAccountCurrency));
    }

    private void updateAccount(Account account, BigDecimal newBalance) {
        account.setCashBalance(newBalance);
    }

    // update position based on existing position if exists,else create new
    private void updateBuyPosition(Long accountId, String symbol, int quantity, BigDecimal executedPrice,
            Position position) {
        if (position == null) {
            position = new Position();
            position.setAccountId(accountId);
            position.setSymbol(symbol);
            position.setQuantity(quantity);
            position.setAverageCost(executedPrice);
        } else {
            int oldQty = position.getQuantity();
            int newQty = oldQty + quantity;
            BigDecimal totalCost = position.getAverageCost().multiply(BigDecimal.valueOf(oldQty))
                    .add(executedPrice.multiply(BigDecimal.valueOf(quantity)));
            BigDecimal newAvgCost = totalCost.divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);
            position.setQuantity(newQty);
            position.setAverageCost(newAvgCost);
        }
        positionRepository.save(position);
    }

    // substract units from existing and update , remove if 0
    private void updateSellPosition(Position position, int quantity) {
        int remaining = position.getQuantity() - quantity;
        if (remaining == 0) {
            positionRepository.delete(position);
            position.setQuantity(0);
        } else {
            position.setQuantity(remaining);
            positionRepository.save(position);
        }
    }

    private TradeEventPayload rejectOrder(Order order, String reason) {
        OffsetDateTime executedOn = OffsetDateTime.now();
        int updated = orderRepository.executeOrder(order.getId(), OrderStatus.REJECTED, null, executedOn, reason);
        if (updated == 0) {
            throw new OptimisticLockException("Order already executed");
        }
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectReason(reason);
        order.setExecutedOn(executedOn);

        Account account = accountRepository.findById(order.getAccountId()).orElseThrow();
        Position position = positionRepository.findByAccountIdAndSymbol(order.getAccountId(), order.getSymbol())
                .orElse(null);

        return buildRejectedEvent(order, reason, account.getCashBalance(), position);
    }

    private TradeEventPayload buildFilledEvent(Order order, BigDecimal newCashBalance, Position position, BigDecimal totalValueInAccountCurrency) {
        BigDecimal cashDelta = order.getSide() == OrderSide.BUY
                ? totalValueInAccountCurrency.negate()
                : totalValueInAccountCurrency;

        int qtyAfter = position != null ? position.getQuantity() : 0;
        BigDecimal avgCostAfter = position != null && position.getQuantity() > 0 ? position.getAverageCost()
                : BigDecimal.ZERO;

        return new TradeEventPayload(
                order.getId(),
                order.getAccountId(),
                order.getSymbol(),
                order.getSide().name(),
                order.getQuantity(),
                order.getLimitPrice(),
                order.getExecutedPrice(),
                order.getStatus().name(),
                null,
                cashDelta,
                qtyAfter,
                avgCostAfter,
                OffsetDateTime.now());
    }

    private TradeEventPayload buildRejectedEvent(Order order, String reason, BigDecimal currentCashBalance,
            Position position) {
        int qtyAfter = position != null ? position.getQuantity() : 0;
        BigDecimal avgCostAfter = position != null && position.getQuantity() > 0 ? position.getAverageCost()
                : BigDecimal.ZERO;

        return new TradeEventPayload(
                order.getId(),
                order.getAccountId(),
                order.getSymbol(),
                order.getSide().name(),
                order.getQuantity(),
                order.getLimitPrice(),
                null,
                order.getStatus().name(),
                reason,
                BigDecimal.ZERO,
                qtyAfter,
                avgCostAfter,
                OffsetDateTime.now());
    }
}
