package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Order;
import com.zkuba.trading.domain.entity.Position;
import com.zkuba.trading.domain.enums.AccountStatus;
import com.zkuba.trading.domain.enums.OrderSide;
import com.zkuba.trading.domain.enums.OrderStatus;
import com.zkuba.trading.domain.enums.OrderType;
import com.zkuba.trading.dto.request.PlaceOrderRequest;
import com.zkuba.trading.dto.response.OrderResponse;
import com.zkuba.trading.exception.AccountNotActiveException;
import com.zkuba.trading.exception.AccountNotFoundException;
import com.zkuba.trading.exception.DuplicateOrderException;
import com.zkuba.trading.exception.ForbiddenException;
import com.zkuba.trading.exception.InsufficientFundsException;
import com.zkuba.trading.exception.InsufficientHoldingsException;
import com.zkuba.trading.exception.InvalidOrderException;
import com.zkuba.trading.exception.InvalidOrderTransitionException;
import com.zkuba.trading.exception.OrderConflictException;
import com.zkuba.trading.mapper.OrderMapper;
import com.zkuba.trading.repository.PositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private PositionRepository positionRepository;

    @Override
    @Transactional
    public OrderResponse place(Long userId, PlaceOrderRequest request) {
        validate(request);
        if (request.idempotencyKey() != null && orderMapper.findByIdempotencyKey(request.idempotencyKey()) != null) {
            throw new DuplicateOrderException(request.idempotencyKey());
        }

        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);

        BigDecimal marketPrice = marketDataService.getCurrentPrice(symbol);

        Account account;
        if (request.accountId() != null) {
            account = orderMapper.findAccountById(request.accountId());
            if (account == null) {
                throw new AccountNotFoundException(request.accountId());
            }
            if (!account.getUserId().equals(userId)) {
                throw new ForbiddenException("ACC-403", "Account does not belong to user");
            }
            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new AccountNotActiveException(account.getId());
            }
        } else {
            account = requireActiveAccount(userId);
        }

        if (request.side() == OrderSide.SELL) {
            Position position = positionRepository.findByAccountIdAndSymbol(account.getId(), symbol);
            int currentQty = position != null ? position.getQuantity() : 0;
            if (currentQty < request.quantity()) {
                throw new InsufficientHoldingsException(account.getId(), symbol);
            }
        }

        BigDecimal executionPrice = request.orderType() == OrderType.MARKET
                ? marketPrice
                : request.limitPrice();
        BigDecimal totalValue = executionPrice.multiply(BigDecimal.valueOf(request.quantity()));
        BigDecimal newBalance = calculateNewBalance(account, request.side(), totalValue);

        return processOrderPlacement(account, request, symbol, executionPrice, newBalance);
    }

    private OrderResponse processOrderPlacement(
            Account account,
            PlaceOrderRequest request,
            String symbol,
            BigDecimal executionPrice,
            BigDecimal newBalance) {
        Position position = positionRepository.findByAccountIdAndSymbol(account.getId(), symbol);
        if (request.side() == OrderSide.SELL) {
            int currentQty = position != null ? position.getQuantity() : 0;
            if (currentQty < request.quantity()) {
                throw new InsufficientHoldingsException(account.getId(), symbol);
            }
            int remaining = currentQty - request.quantity();
            if (remaining == 0) {
                positionRepository.delete(position);
            } else {
                position.setQuantity(remaining);
                positionRepository.save(position);
            }
        } else if (request.side() == OrderSide.BUY) {
            if (position == null) {
                position = new Position();
                position.setAccountId(account.getId());
                position.setSymbol(symbol);
                position.setQuantity(request.quantity());
                position.setAverageCost(executionPrice);
            } else {
                int oldQty = position.getQuantity();
                int newQty = oldQty + request.quantity();
                BigDecimal totalCost = position.getAverageCost().multiply(BigDecimal.valueOf(oldQty))
                        .add(executionPrice.multiply(BigDecimal.valueOf(request.quantity())));
                BigDecimal newAvgCost = totalCost.divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);
                position.setQuantity(newQty);
                position.setAverageCost(newAvgCost);
            }
            positionRepository.save(position);
        }

        int updatedRows = orderMapper.updateAccountBalance(account.getId(), newBalance, account.getVersion());
        if (updatedRows == 0) {
            throw new OrderConflictException("Account update conflict for account: " + account.getId());
        }

        Order order = buildFilledOrder(account, request, symbol, executionPrice);
        int insertedRows = orderMapper.insert(order);
        if (insertedRows == 0) {
            throw new OrderConflictException("Order insert failed for account: " + account.getId());
        }

        return response(order, "Order executed");
    }

    @Override
    @Transactional
    public OrderResponse cancel(Long userId, UUID orderId) {
        Account account = requireActiveAccount(userId);
        Order order = orderMapper.findByIdAndAccountId(orderId, account.getId());
        if (order == null) {
            throw new OrderConflictException("Order not found or access denied: " + orderId);
        }
        if (order.getStatus() != OrderStatus.NEW) {
            throw new InvalidOrderTransitionException(orderId, order.getStatus(), "cancel");
        }

        int updatedRows = orderMapper.cancelOrderConditionally(orderId, account.getId(), OrderStatus.NEW.name());
        if (updatedRows == 0) {
            throw new OrderConflictException("Order cancellation conflict for order: " + orderId);
        }

        order.setStatus(OrderStatus.CANCELLED);
        return response(order, "Order cancelled");
    }

    @Override
    public List<OrderResponse> getOrderHistory(Long userId) {
        Account account = requireActiveAccount(userId);
        List<Order> orderList = orderMapper.findByAccountId(account.getId());
        return orderList.stream()
                .map(order -> response(order, "Order status: " + order.getStatus()))
                .toList();
    }

    private Account requireActiveAccount(Long userId) {
        Account account = orderMapper.findAccountByUserId(userId);
        if (account == null) {
            throw new AccountNotFoundException(userId);
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getId());
        }
        return account;
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

    private BigDecimal calculateNewBalance(Account account, OrderSide side, BigDecimal totalValue) {
        if (side == OrderSide.BUY) {
            if (account.getCashBalance().compareTo(totalValue) < 0) {
                throw new InsufficientFundsException(account.getId());
            }
            return account.getCashBalance().subtract(totalValue);
        }
        return account.getCashBalance().add(totalValue);
    }

    private Order buildFilledOrder(Account account, PlaceOrderRequest request, String symbol,
            BigDecimal executionPrice) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setAccountId(account.getId());
        order.setSymbol(symbol);
        order.setSide(request.side());
        order.setOrderType(request.orderType());
        order.setQuantity(request.quantity());
        order.setLimitPrice(request.limitPrice());
        order.setExecutedPrice(executionPrice);
        order.setStatus(OrderStatus.FILLED);
        order.setIdempotencyKey(request.idempotencyKey());
        order.setCreatedAt(OffsetDateTime.now());
        return order;
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

