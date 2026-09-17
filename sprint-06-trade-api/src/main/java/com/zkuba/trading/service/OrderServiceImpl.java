package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Order;
import com.zkuba.trading.domain.enums.AccountStatus;
import com.zkuba.trading.domain.enums.OrderStatus;
import com.zkuba.trading.domain.enums.OrderType;
import com.zkuba.trading.dto.request.PlaceOrderRequest;
import com.zkuba.trading.dto.request.UpdateOrderRequest;
import com.zkuba.trading.dto.response.OrderResponse;
import com.zkuba.trading.exception.AccountNotActiveException;
import com.zkuba.trading.exception.AccountNotFoundException;
import com.zkuba.trading.exception.DuplicateOrderException;
import com.zkuba.trading.exception.ForbiddenException;
import com.zkuba.trading.exception.InvalidOrderException;
import com.zkuba.trading.exception.InvalidOrderTransitionException;
import com.zkuba.trading.exception.OrderConflictException;
import com.zkuba.trading.event.OrderPlacedEvent;
import com.zkuba.trading.event.OrderUpdatedEvent;
import com.zkuba.trading.mapper.OrderMapper;
import com.zkuba.trading.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public OrderResponse place(Long userId, PlaceOrderRequest request) {
        validate(request);
        if (request.idempotencyKey() != null && orderMapper.findByIdempotencyKey(request.idempotencyKey()) != null) {
            throw new DuplicateOrderException(request.idempotencyKey());
        }

        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);

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

        Order order = buildNewOrder(account, request, symbol);
        try {
            int insertedRows = orderMapper.insert(order);
            if (insertedRows == 0) {
                throw new OrderConflictException("Order insert failed for account: " + account.getId());
            }
        } catch (DataIntegrityViolationException e) {
            if (request.idempotencyKey() != null) {
                Order existing = orderMapper.findByIdempotencyKey(request.idempotencyKey());
                if (existing != null) {
                    return response(existing, "Order already processed with idempotency key");
                }
            }
            throw e;
        }

        eventPublisher.publishEvent(new OrderPlacedEvent(order));

        return response(order, "Order placed");
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
    @Transactional
    public OrderResponse update(Long userId, UUID orderId, UpdateOrderRequest request) {
        Account account = requireActiveAccount(userId);
        Order order = orderRepository.findByIdAndAccountId(orderId, account.getId())
                .orElseThrow(() -> new OrderConflictException("Order not found or access denied: " + orderId));

        if (order.getStatus() != OrderStatus.NEW) {
            throw new InvalidOrderTransitionException(orderId, order.getStatus(), "update");
        }

        if (request.quantity() == null && request.limitPrice() == null) {
            throw new InvalidOrderException("At least quantity or limitPrice must be provided to update order");
        }

        int newQuantity = request.quantity() != null ? request.quantity() : order.getQuantity();
        BigDecimal newLimitPrice = request.limitPrice() != null ? request.limitPrice() : order.getLimitPrice();

        if (newLimitPrice != null && newLimitPrice.signum() <= 0) {
            throw new InvalidOrderException("Limit price must be positive");
        }

        order.setQuantity(newQuantity);
        order.setLimitPrice(newLimitPrice);

        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderUpdatedEvent(order));

        return response(order, "Order updated");
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

    private Order buildNewOrder(Account account, PlaceOrderRequest request, String symbol) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setAccountId(account.getId());
        order.setSymbol(symbol);
        order.setSide(request.side());
        order.setOrderType(request.orderType());
        order.setQuantity(request.quantity());
        order.setLimitPrice(request.limitPrice());
        order.setExecutedPrice(null);
        order.setStatus(OrderStatus.NEW);
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
