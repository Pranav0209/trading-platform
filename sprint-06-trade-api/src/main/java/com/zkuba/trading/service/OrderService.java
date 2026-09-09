package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Instrument;
import com.zkuba.trading.domain.entity.Order;
import com.zkuba.trading.domain.entity.Position;
import com.zkuba.trading.domain.enums.AccountStatus;
import com.zkuba.trading.domain.enums.OrderSide;
import com.zkuba.trading.domain.enums.OrderStatus;
import com.zkuba.trading.domain.enums.OrderType;
import com.zkuba.trading.domain.exception.AccountNotActiveException;
import com.zkuba.trading.domain.exception.AccountNotFoundException;
import com.zkuba.trading.domain.exception.DuplicateOrderException;
import com.zkuba.trading.domain.exception.ForbiddenException;
import com.zkuba.trading.domain.exception.InstrumentNotFoundException;
import com.zkuba.trading.domain.exception.InsufficientFundsException;
import com.zkuba.trading.domain.exception.InsufficientHoldingsException;
import com.zkuba.trading.domain.exception.InvalidOrderException;
import com.zkuba.trading.domain.exception.InvalidOrderTransitionException;
import com.zkuba.trading.dto.request.PlaceOrderRequest;
import com.zkuba.trading.dto.response.OrderResponse;
import com.zkuba.trading.mapper.AccountMapper;
import com.zkuba.trading.mapper.PositionMapper;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.InstrumentRepository;
import com.zkuba.trading.repository.OrderRepository;
import com.zkuba.trading.repository.PositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final AccountMapper accountMapper;
    private final AccountRepository accounts;
    private final InstrumentRepository instruments;
    private final OrderRepository orders;
    private final PositionRepository positions;
    private final PositionMapper positionMapper;
    private final MarketDataService marketDataService;

    public OrderService(
            AccountMapper accountMapper,
            AccountRepository accounts,
            InstrumentRepository instruments,
            OrderRepository orders,
            PositionRepository positions,
            PositionMapper positionMapper,
            MarketDataService marketDataService) {
        this.accountMapper = accountMapper;
        this.accounts = accounts;
        this.instruments = instruments;
        this.orders = orders;
        this.positions = positions;
        this.positionMapper = positionMapper;
        this.marketDataService = marketDataService;
    }

    @Transactional
    public OrderResponse place(Long userId, PlaceOrderRequest request) {
        Account account = accounts.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException(userId));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getId());
        }

        String symbol = request.symbol().toUpperCase();
        Instrument instrument = instruments.findById(symbol)
                .orElseThrow(() -> new InstrumentNotFoundException(symbol));
        if (!instrument.isTradable()) {
            throw new InvalidOrderException("Instrument is not tradable");
        }
        if (orders.findByIdempotencyKey(request.idempotencyKey()).isPresent()) {
            throw new DuplicateOrderException(request.idempotencyKey());
        }
        validate(request);

        BigDecimal price = marketDataService.getCurrentPrice(symbol);
        if (request.orderType() == OrderType.LIMIT && !isTriggered(request.side(), price, request.limitPrice())) {
            return savePending(account, request, symbol);
        }

        BigDecimal totalValue = price.multiply(BigDecimal.valueOf(request.quantity()));
        Position position = positions.findByAccountIdAndSymbol(account.getId(), symbol);
        if (request.side() == OrderSide.BUY) {
            if (account.getCashBalance().compareTo(totalValue) < 0) {
                throw new InsufficientFundsException(account.getId());
            }
            updateCash(account, account.getCashBalance().subtract(totalValue));
            int oldQuantity = position == null ? 0 : position.getQuantity();
            BigDecimal oldCost = position == null ? BigDecimal.ZERO : position.getAverageCost();
            int newQuantity = oldQuantity + request.quantity();
            BigDecimal averageCost = oldQuantity == 0
                    ? price
                    : oldCost.multiply(BigDecimal.valueOf(oldQuantity))
                            .add(totalValue)
                            .divide(BigDecimal.valueOf(newQuantity), 8, RoundingMode.HALF_UP);
            positionMapper.upsert(account.getId(), symbol, newQuantity, averageCost);
        } else {
            if (position == null || position.getQuantity() < request.quantity()) {
                throw new InsufficientHoldingsException(account.getId(), symbol);
            }
            positionMapper.upsert(
                    account.getId(),
                    symbol,
                    position.getQuantity() - request.quantity(),
                    position.getAverageCost());
            updateCash(account, account.getCashBalance().add(totalValue));
        }

        Order order = buildOrder(account, request, symbol);
        order.setExecutedPrice(price);
        order.setStatus(OrderStatus.FILLED);
        insert(order);

        return response(order, "Order executed");
    }

    private void validate(PlaceOrderRequest request) {
        if (request.quantity() <= 0) {
            throw new InvalidOrderException("Quantity must be positive");
        }
        if (request.orderType() == OrderType.LIMIT && request.limitPrice() == null) {
            throw new InvalidOrderException("Limit price is required for limit orders");
        }
        if (request.orderType() == OrderType.MARKET && request.limitPrice() != null) {
            throw new InvalidOrderException("Limit price must be omitted for market orders");
        }
        if (request.limitPrice() != null && request.limitPrice().signum() <= 0) {
            throw new InvalidOrderException("Limit price must be positive");
        }
    }

    private boolean isTriggered(OrderSide side, BigDecimal price, BigDecimal limitPrice) {
        return side == OrderSide.BUY ? price.compareTo(limitPrice) <= 0 : price.compareTo(limitPrice) >= 0;
    }

    private OrderResponse savePending(Account account, PlaceOrderRequest request, String symbol) {
        Order order = buildOrder(account, request, symbol);
        order.setStatus(OrderStatus.NEW);
        insert(order);
        return response(order, "Limit order accepted and waiting for execution");
    }

    private Order buildOrder(Account account, PlaceOrderRequest request, String symbol) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setAccountId(account.getId());
        order.setSymbol(symbol);
        order.setSide(request.side());
        order.setOrderType(request.orderType());
        order.setQuantity(request.quantity());
        order.setLimitPrice(request.limitPrice());
        order.setIdempotencyKey(request.idempotencyKey());
        order.setCreatedAt(OffsetDateTime.now());
        return order;
    }

    private void insert(Order order) {
        try {
            orders.save(order);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateOrderException(order.getIdempotencyKey());
        }
    }

    private void updateCash(Account account, BigDecimal newCashBalance) {
        account.setCashBalance(newCashBalance);
        accounts.save(account);
    }

    public OrderResponse cancel(Long userId, UUID orderId) {
        Order order = orders.findById(orderId).orElse(null);
        Account account = accounts.findByUserId(userId).orElse(null);
        if (order == null || account == null || !order.getAccountId().equals(account.getId())) {
            throw new ForbiddenException();
        }
        if (order.getStatus() != OrderStatus.NEW) {
            throw new InvalidOrderTransitionException(orderId, order.getStatus(), "cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);
        orders.save(order);
        return response(order, "Order cancelled");
    }

    public java.util.List<OrderResponse> getOrderHistory(Long userId) {
        Account account = accounts.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException(userId));
        return orders.findByAccountIdOrderByCreatedAtDesc(account.getId())
                .stream()
                .map(order -> response(order, "Order status: " + order.getStatus()))
                .toList();
    }

    private OrderResponse response(Order order, String message) {
        return new OrderResponse(
                "ORD-" + order.getId(),
                order.getStatus().name(),
                order.getSymbol(),
                order.getSide().name(),
                order.getOrderType().name(),
                order.getQuantity(),
                order.getLimitPrice(),
                order.getExecutedPrice(),
                message);
    }
}
