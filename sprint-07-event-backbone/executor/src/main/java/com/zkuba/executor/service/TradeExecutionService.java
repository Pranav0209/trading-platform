package com.zkuba.executor.service;

import com.zkuba.executor.client.FauxnanceQuoteClient;
import com.zkuba.executor.client.PricingUnavailableException;
import com.zkuba.executor.client.QuoteResponse;
import com.zkuba.executor.domain.entity.Account;
import com.zkuba.executor.domain.entity.Order;
import com.zkuba.executor.domain.entity.Position;
import com.zkuba.executor.engine.FillDecision;
import com.zkuba.executor.engine.FillRuleEngine;
import com.zkuba.executor.event.TradeEventPayload;
import com.zkuba.executor.repository.AccountRepository;
import com.zkuba.executor.repository.OrderRepository;
import com.zkuba.executor.repository.PositionRepository;
import com.zkuba.executor.domain.enums.AccountStatus;
import com.zkuba.executor.domain.enums.OrderSide;
import com.zkuba.executor.domain.enums.OrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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
    private FauxnanceQuoteClient quoteClient;

    @Autowired
    private FillRuleEngine fillRuleEngine;

    @Retryable(
            retryFor = {OptimisticLockException.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 50, multiplier = 2.0)
    )
    @Transactional
    public Optional<TradeEventPayload> executeOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.NEW) {
            // Already handled, do nothing (idempotency)
            return Optional.empty();
        }

        QuoteResponse quoteResponse = null;
        try {
            quoteResponse = quoteClient.getQuote(order.getSymbol());
        } catch (PricingUnavailableException e) {
            return Optional.of(rejectOrder(order, "PRICING_UNAVAILABLE"));
        }

        FillDecision decision = fillRuleEngine.evaluate(order, quoteResponse.data());

        if (!decision.isFilled()) {
            return Optional.of(rejectOrder(order, decision.rejectReason()));
        }

        Account account = accountRepository.findById(order.getAccountId()).orElseThrow();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return Optional.of(rejectOrder(order, "ACCOUNT_SUSPENDED"));
        }

        BigDecimal executedPrice = decision.executedPrice();
        BigDecimal totalValue = executedPrice.multiply(BigDecimal.valueOf(order.getQuantity()));

        Position position = positionRepository.findByAccountIdAndSymbol(account.getId(), order.getSymbol()).orElse(null);

        if (order.getSide() == OrderSide.BUY) {
            if (account.getCashBalance().compareTo(totalValue) < 0) {
                return Optional.of(rejectOrder(order, "INSUFFICIENT_FUNDS"));
            }
            BigDecimal newBalance = account.getCashBalance().subtract(totalValue);
            updateAccount(account, newBalance);
            updateBuyPosition(account.getId(), order.getSymbol(), order.getQuantity(), executedPrice, position);
        } else {
            int currentQty = position != null ? position.getQuantity() : 0;
            if (currentQty < order.getQuantity()) {
                return Optional.of(rejectOrder(order, "INSUFFICIENT_HOLDINGS"));
            }
            BigDecimal newBalance = account.getCashBalance().add(totalValue);
            updateAccount(account, newBalance);
            updateSellPosition(position, order.getQuantity());
        }

        // Guarded state transition
        int updated = orderRepository.executeOrder(order.getId(), OrderStatus.FILLED, executedPrice);
        if (updated == 0) {
            // Another thread/delivery got here first
            throw new OptimisticLockException("Order already executed");
        }

        order.setStatus(OrderStatus.FILLED);
        order.setExecutedPrice(executedPrice);

        return Optional.of(buildFilledEvent(order, account.getCashBalance(), position));
    }

    private void updateAccount(Account account, BigDecimal newBalance) {
        account.setCashBalance(newBalance);
    }

    private void updateBuyPosition(Long accountId, String symbol, int quantity, BigDecimal executedPrice, Position position) {
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
        int updated = orderRepository.executeOrder(order.getId(), OrderStatus.REJECTED, null);
        if (updated == 0) {
            throw new OptimisticLockException("Order already executed");
        }
        order.setStatus(OrderStatus.REJECTED);
        
        Account account = accountRepository.findById(order.getAccountId()).orElseThrow();
        Position position = positionRepository.findByAccountIdAndSymbol(order.getAccountId(), order.getSymbol()).orElse(null);

        return buildRejectedEvent(order, reason, account.getCashBalance(), position);
    }

    private TradeEventPayload buildFilledEvent(Order order, BigDecimal newCashBalance, Position position) {
        BigDecimal cashDelta = order.getSide() == OrderSide.BUY 
            ? order.getExecutedPrice().multiply(BigDecimal.valueOf(order.getQuantity())).negate()
            : order.getExecutedPrice().multiply(BigDecimal.valueOf(order.getQuantity()));

        int qtyAfter = position != null ? position.getQuantity() : 0;
        BigDecimal avgCostAfter = position != null && position.getQuantity() > 0 ? position.getAverageCost() : BigDecimal.ZERO;

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
                OffsetDateTime.now()
        );
    }

    private TradeEventPayload buildRejectedEvent(Order order, String reason, BigDecimal currentCashBalance, Position position) {
        int qtyAfter = position != null ? position.getQuantity() : 0;
        BigDecimal avgCostAfter = position != null && position.getQuantity() > 0 ? position.getAverageCost() : BigDecimal.ZERO;

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
                OffsetDateTime.now()
        );
    }
}
