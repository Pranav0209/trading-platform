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

    // if 2 threads try to update exact same account at same time, db wil throw
    // OptimisticLockingException, if that happes we tell spring to not crash and
    // just to wait 50ms and try upto 5 times
    @Retryable(retryFor = {
            OptimisticLockException.class }, maxAttempts = 5, backoff = @Backoff(delay = 50, multiplier = 2.0))
    @Transactional
    public Optional<TradeEventPayload> executeOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        // to handle cancelled orders
        if (order == null || order.getStatus() != OrderStatus.NEW) {
            // Already handled, do nothing (idempotency)
            return Optional.empty();
        }

        // validation - fetch account early
        Account account = accountRepository.findById(order.getAccountId()).orElseThrow();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return Optional.of(rejectOrder(order, "ACCOUNT_SUSPENDED"));
        }

        // fetching "live" market data, if price is not available-> reject order
        QuoteResponse quoteResponse = null;
        try {
            quoteResponse = quoteClient.getQuote(order.getSymbol());
        } catch (PricingUnavailableException e) {
            return Optional.of(rejectOrder(order, "PRICING_UNAVAILABLE"));
        }

        // Validate holdings/cash BEFORE checking price - must reject if insufficient holdings/funds
        // even if limit order price hasn't been met yet
        Position position = positionRepository.findByAccountIdAndSymbol(account.getId(), order.getSymbol())
                .orElse(null);

        // Pre-check for bounds - reject immediately if insufficient holdings/funds
        // This prevents limit orders from sitting at NEW when they can never be filled
        if (order.getSide() == OrderSide.SELL) {
            int currentQty = position != null ? position.getQuantity() : 0;
            if (currentQty < order.getQuantity()) {
                return Optional.of(rejectOrder(order, "INSUFFICIENT_HOLDINGS"));
            }
        }
        // For BUY orders, we check cash balance after getting the execution price

        // checks if order can be filled
        FillDecision decision = fillRuleEngine.evaluate(order, quoteResponse.data());

        // check decison, if price not met(limit case), we leave order status as new &
        // come back later, for any other reason we reject directly
        if (!decision.isFilled()) {
            if ("PRICE_NOT_MET".equals(decision.rejectReason())) {
                return Optional.empty();
            }
            return Optional.of(rejectOrder(order, decision.rejectReason()));
        }

        BigDecimal executedPrice = decision.executedPrice();
        BigDecimal totalValueInStockCurrency = executedPrice.multiply(BigDecimal.valueOf(order.getQuantity()));

        BigDecimal conversionRate = BigDecimal.ONE;
        try {
            String quoteCurrency = quoteResponse.data().currency();
            if (quoteCurrency != null && !quoteCurrency.equalsIgnoreCase(account.getCurrency())) {
                conversionRate = currencyConversionClient.getConversionRate(quoteCurrency, account.getCurrency());
            }
        } catch (PricingUnavailableException e) {
            return Optional.of(rejectOrder(order, "PRICING_UNAVAILABLE"));
        }

        BigDecimal totalValueInAccountCurrency = totalValueInStockCurrency.multiply(conversionRate);
        BigDecimal executedPriceInAccountCurrency = totalValueInAccountCurrency.divide(BigDecimal.valueOf(order.getQuantity()), 4, RoundingMode.HALF_UP);

        // Check BUY order has sufficient funds at execution price
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
