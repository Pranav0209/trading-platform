package com.zkuba.trading.service;

import com.zkuba.trading.service.impl.*;
import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Order;
import com.zkuba.trading.domain.enums.AccountStatus;
import com.zkuba.trading.domain.enums.OrderSide;
import com.zkuba.trading.domain.enums.OrderStatus;
import com.zkuba.trading.domain.enums.OrderType;
import com.zkuba.trading.dto.request.PlaceOrderRequest;
import com.zkuba.trading.dto.request.UpdateOrderRequest;
import com.zkuba.trading.dto.response.OrderResponse;
import com.zkuba.trading.event.OrderPlacedEvent;
import com.zkuba.trading.event.OrderUpdatedEvent;
import com.zkuba.trading.exception.AccountNotActiveException;
import com.zkuba.trading.exception.AccountNotFoundException;
import com.zkuba.trading.exception.DuplicateOrderException;
import com.zkuba.trading.exception.ForbiddenException;
import com.zkuba.trading.exception.InvalidOrderException;
import com.zkuba.trading.exception.InvalidOrderTransitionException;
import com.zkuba.trading.exception.OrderConflictException;
import com.zkuba.trading.mapper.OrderMapper;
import com.zkuba.trading.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private InstrumentService instrumentService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Account activeAccount;

    @BeforeEach
    void setUp() {
        activeAccount = new Account();
        activeAccount.setId(10L);
        activeAccount.setUserId(1L);
        activeAccount.setCashBalance(new BigDecimal("1000.00"));
        activeAccount.setStatus(AccountStatus.ACTIVE);
        activeAccount.setVersion(5);
    }

    // =========================================================
    // PLACE - VALID ORDERS
    // =========================================================

    @Test
    @DisplayName("place accepts valid market order using user's active account")
    void placeOrder_validMarketOrder_success() {

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        null,
                        " AAPL ",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        2,
                        null,
                        "key-1"
                );

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        when(orderMapper.insert(any(Order.class)))
                .thenReturn(1);

        OrderResponse response =
                orderService.place(1L, request);

        assertThat(response.status())
                .isEqualTo("NEW");

        assertThat(response.symbol())
                .isEqualTo("AAPL");

        assertThat(response.executedPrice())
                .isNull();

        ArgumentCaptor<Order> captor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderMapper).insert(captor.capture());

        Order savedOrder = captor.getValue();

        assertThat(savedOrder.getAccountId())
                .isEqualTo(10L);

        assertThat(savedOrder.getSymbol())
                .isEqualTo("AAPL");

        assertThat(savedOrder.getStatus())
                .isEqualTo(OrderStatus.NEW);

        assertThat(savedOrder.getOrderType())
                .isEqualTo(OrderType.MARKET);

        verify(eventPublisher)
                .publishEvent(any(OrderPlacedEvent.class));
    }

    @Test
    @DisplayName("place accepts valid order when explicit accountId is supplied")
    void placeOrder_explicitAccount_success() {

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        10L,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        2,
                        null,
                        "key-explicit"
                );

        when(orderMapper.findAccountById(10L))
                .thenReturn(activeAccount);

        when(orderMapper.insert(any(Order.class)))
                .thenReturn(1);

        OrderResponse response =
                orderService.place(1L, request);

        assertThat(response.status())
                .isEqualTo("NEW");

        verify(orderMapper).findAccountById(10L);
        verify(orderMapper).insert(any(Order.class));
        verify(eventPublisher)
                .publishEvent(any(OrderPlacedEvent.class));
    }

    @Test
    @DisplayName("place accepts valid limit order with limit price")
    void placeOrder_limitOrder_success() {

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        null,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        5,
                        new BigDecimal("150.00"),
                        "limit-1"
                );

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        when(orderMapper.insert(any(Order.class)))
                .thenReturn(1);

        OrderResponse response =
                orderService.place(1L, request);

        assertThat(response.status())
                .isEqualTo("NEW");

        assertThat(response.limitPrice())
                .isEqualByComparingTo("150.00");
    }

    // =========================================================
    // PLACE - IDEMPOTENCY
    // =========================================================

    @Test
    @DisplayName("place rejects duplicate idempotency key before account lookup")
    void placeOrder_duplicateKey_throwsException() {

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        null,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        2,
                        null,
                        "key-dup"
                );

        when(orderMapper.findByIdempotencyKey("key-dup"))
                .thenReturn(new Order());

        assertThatThrownBy(
                () -> orderService.place(1L, request)
        )
                .isInstanceOf(DuplicateOrderException.class);

        verify(orderMapper, never())
                .findAccountByUserId(1L);

        verify(orderMapper, never())
                .insert(any(Order.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    // =========================================================
    // PLACE - EXPLICIT ACCOUNT BRANCHES
    // =========================================================

    @Test
    @DisplayName("place throws when explicit account does not exist")
    void placeOrder_explicitAccountNotFound() {

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        99L,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        2,
                        null,
                        "key-2"
                );

        when(orderMapper.findAccountById(99L))
                .thenReturn(null);

        assertThatThrownBy(
                () -> orderService.place(1L, request)
        )
                .isInstanceOf(AccountNotFoundException.class);

        verify(orderMapper, never())
                .insert(any(Order.class));
    }

    @Test
    @DisplayName("place rejects explicit account belonging to another user")
    void placeOrder_explicitAccountWrongUser() {

        activeAccount.setUserId(999L);

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        10L,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        2,
                        null,
                        "key-3"
                );

        when(orderMapper.findAccountById(10L))
                .thenReturn(activeAccount);

        assertThatThrownBy(
                () -> orderService.place(1L, request)
        )
                .isInstanceOf(ForbiddenException.class);

        verify(orderMapper, never())
                .insert(any(Order.class));
    }

    @Test
    @DisplayName("place rejects explicit inactive account")
    void placeOrder_explicitAccountInactive() {

        activeAccount.setStatus(AccountStatus.SUSPENDED);

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        10L,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        2,
                        null,
                        "key-4"
                );

        when(orderMapper.findAccountById(10L))
                .thenReturn(activeAccount);

        assertThatThrownBy(
                () -> orderService.place(1L, request)
        )
                .isInstanceOf(AccountNotActiveException.class);

        verify(orderMapper, never())
                .insert(any(Order.class));
    }

    // =========================================================
    // PLACE - requireActiveAccount()
    // =========================================================

    @Test
    @DisplayName("place throws when user has no active account")
    void placeOrder_noAccount() {

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        null,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        2,
                        null,
                        "key-5"
                );

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(null);

        assertThatThrownBy(
                () -> orderService.place(1L, request)
        )
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("place rejects user's inactive account")
    void placeOrder_userAccountInactive() {

        activeAccount.setStatus(AccountStatus.SUSPENDED);

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        null,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        2,
                        null,
                        "key-6"
                );

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        assertThatThrownBy(
                () -> orderService.place(1L, request)
        )
                .isInstanceOf(AccountNotActiveException.class);
    }

    // =========================================================
    // PLACE - VALIDATION
    // =========================================================

    @Test
    @DisplayName("place rejects zero quantity")
    void placeOrder_zeroQuantity() {

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        null,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        0,
                        null,
                        "key-7"
                );

        assertThatThrownBy(
                () -> orderService.place(1L, request)
        )
                .isInstanceOf(InvalidOrderException.class);

        verifyNoOrderCreated();
    }

    @Test
    @DisplayName("place rejects negative quantity")
    void placeOrder_negativeQuantity() {

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        null,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        -1,
                        null,
                        "key-8"
                );

        assertThatThrownBy(
                () -> orderService.place(1L, request)
        )
                .isInstanceOf(InvalidOrderException.class);

        verifyNoOrderCreated();
    }

    @Test
    @DisplayName("place rejects limit order without limit price")
    void placeOrder_limitWithoutPrice() {

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        null,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        2,
                        null,
                        "key-9"
                );

        assertThatThrownBy(
                () -> orderService.place(1L, request)
        )
                .isInstanceOf(InvalidOrderException.class);

        verifyNoOrderCreated();
    }

    @Test
    @DisplayName("place rejects market order with limit price")
    void placeOrder_marketWithLimitPrice() {

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        null,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        2,
                        new BigDecimal("100.00"),
                        "key-10"
                );

        assertThatThrownBy(
                () -> orderService.place(1L, request)
        )
                .isInstanceOf(InvalidOrderException.class);

        verifyNoOrderCreated();
    }

    @Test
    @DisplayName("place rejects zero limit price")
    void placeOrder_zeroLimitPrice() {

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        null,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        2,
                        BigDecimal.ZERO,
                        "key-11"
                );

        assertThatThrownBy(
                () -> orderService.place(1L, request)
        )
                .isInstanceOf(InvalidOrderException.class);

        verifyNoOrderCreated();
    }

    @Test
    @DisplayName("place rejects negative limit price")
    void placeOrder_negativeLimitPrice() {

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        null,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        2,
                        new BigDecimal("-1.00"),
                        "key-12"
                );

        assertThatThrownBy(
                () -> orderService.place(1L, request)
        )
                .isInstanceOf(InvalidOrderException.class);

        verifyNoOrderCreated();
    }

    // =========================================================
    // PLACE - INSERT RESULTS
    // =========================================================

    @Test
    @DisplayName("place throws conflict when insert affects zero rows")
    void placeOrder_insertReturnsZero() {

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        null,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        2,
                        null,
                        "key-13"
                );

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        when(orderMapper.insert(any(Order.class)))
                .thenReturn(0);

        assertThatThrownBy(
                () -> orderService.place(1L, request)
        )
                .isInstanceOf(OrderConflictException.class);

        verify(eventPublisher, never())
                .publishEvent(any());
    }

@Test
@DisplayName("place handles DB conflict when existing order is found after insert failure")
void placeOrder_dbConflict_existingOrder() {

    String idempotencyKey = "key-conflict";

    Order existingOrder = createOrder();
    existingOrder.setIdempotencyKey(idempotencyKey);

    // IMPORTANT:
    // 1st call happens before insert -> must return null
    // 2nd call happens inside DataIntegrityViolationException catch -> existing order
    when(orderMapper.findByIdempotencyKey(idempotencyKey))
            .thenReturn(null)
            .thenReturn(existingOrder);

    when(orderMapper.findAccountByUserId(1L))
            .thenReturn(activeAccount);

    when(orderMapper.insert(any(Order.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

    PlaceOrderRequest request = new PlaceOrderRequest(
            null,
            "AAPL",
            OrderSide.BUY,
            OrderType.MARKET,
            10,
            null,
            idempotencyKey
    );

    OrderResponse response =
            orderService.place(1L, request);

    assertThat(response).isNotNull();
    assertThat(response.message())
            .isEqualTo("Order already processed with idempotency key");

    verify(orderMapper, times(2))
            .findByIdempotencyKey(idempotencyKey);

    verify(orderMapper)
            .insert(any(Order.class));

    verify(eventPublisher, never())
            .publishEvent(any());
}



    // =========================================================
    // CANCEL
    // =========================================================

    @Test
    @DisplayName("cancel throws when user has no account")
    void cancelOrder_noAccount() {

        UUID orderId = UUID.randomUUID();

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(null);

        assertThatThrownBy(
                () -> orderService.cancel(1L, orderId)
        )
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("cancel throws when account is inactive")
    void cancelOrder_inactiveAccount() {

        UUID orderId = UUID.randomUUID();

        activeAccount.setStatus(AccountStatus.SUSPENDED);

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        assertThatThrownBy(
                () -> orderService.cancel(1L, orderId)
        )
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    @DisplayName("cancel throws conflict when order does not exist")
    void cancelOrder_orderNotFound() {

        UUID orderId = UUID.randomUUID();

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        when(orderMapper.findByIdAndAccountId(
                orderId,
                10L
        )).thenReturn(null);

        assertThatThrownBy(
                () -> orderService.cancel(1L, orderId)
        )
                .isInstanceOf(OrderConflictException.class);
    }

    @Test
    @DisplayName("cancel rejects non-NEW order")
    void cancelOrder_invalidStatus() {

        UUID orderId = UUID.randomUUID();

        Order existing = createOrder();
        existing.setId(orderId);
        existing.setStatus(OrderStatus.FILLED);

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        when(orderMapper.findByIdAndAccountId(
                orderId,
                10L
        )).thenReturn(existing);

        assertThatThrownBy(
                () -> orderService.cancel(1L, orderId)
        )
                .isInstanceOf(
                        InvalidOrderTransitionException.class
                );

        verify(orderMapper, never())
                .cancelOrderConditionally(
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("cancel throws conflict when conditional update affects zero rows")
    void cancelOrder_affectedRowsZero() {

        UUID orderId = UUID.randomUUID();

        Order existing = createOrder();
        existing.setId(orderId);
        existing.setStatus(OrderStatus.NEW);

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        when(orderMapper.findByIdAndAccountId(
                orderId,
                10L
        )).thenReturn(existing);

        when(orderMapper.cancelOrderConditionally(
                orderId,
                10L,
                "NEW"
        )).thenReturn(0);

        assertThatThrownBy(
                () -> orderService.cancel(1L, orderId)
        )
                .isInstanceOf(OrderConflictException.class);
    }

    @Test
    @DisplayName("cancel successfully changes NEW order to CANCELLED")
    void cancelOrder_success() {

        UUID orderId = UUID.randomUUID();

        Order existing = createOrder();
        existing.setId(orderId);
        existing.setStatus(OrderStatus.NEW);

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        when(orderMapper.findByIdAndAccountId(
                orderId,
                10L
        )).thenReturn(existing);

        when(orderMapper.cancelOrderConditionally(
                orderId,
                10L,
                "NEW"
        )).thenReturn(1);

        OrderResponse response =
                orderService.cancel(1L, orderId);

        assertThat(response.status())
                .isEqualTo("CANCELLED");

        assertThat(existing.getStatus())
                .isEqualTo(OrderStatus.CANCELLED);

        verify(orderMapper).cancelOrderConditionally(
                orderId,
                10L,
                "NEW"
        );
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @Test
    @DisplayName("update throws when user has no account")
    void updateOrder_noAccount() {

        UUID orderId = UUID.randomUUID();

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(null);

        UpdateOrderRequest request =
                new UpdateOrderRequest(
                        10,
                        new BigDecimal("95.00")
                );

        assertThatThrownBy(
                () -> orderService.update(
                        1L,
                        orderId,
                        request
                )
        )
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("update rejects inactive account")
    void updateOrder_inactiveAccount() {

        UUID orderId = UUID.randomUUID();

        activeAccount.setStatus(AccountStatus.SUSPENDED);

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        UpdateOrderRequest request =
                new UpdateOrderRequest(
                        10,
                        new BigDecimal("95.00")
                );

        assertThatThrownBy(
                () -> orderService.update(
                        1L,
                        orderId,
                        request
                )
        )
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    @DisplayName("update throws conflict when order does not exist")
    void updateOrder_orderNotFound() {

        UUID orderId = UUID.randomUUID();

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        when(orderRepository.findByIdAndAccountId(
                orderId,
                10L
        )).thenReturn(Optional.empty());

        UpdateOrderRequest request =
                new UpdateOrderRequest(
                        10,
                        new BigDecimal("95.00")
                );

        assertThatThrownBy(
                () -> orderService.update(
                        1L,
                        orderId,
                        request
                )
        )
                .isInstanceOf(OrderConflictException.class);
    }

    @Test
    @DisplayName("update rejects non-NEW order")
    void updateOrder_nonNewStatus() {

        UUID orderId = UUID.randomUUID();

        Order existing = createOrder();
        existing.setId(orderId);
        existing.setStatus(OrderStatus.FILLED);

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        when(orderRepository.findByIdAndAccountId(
                orderId,
                10L
        )).thenReturn(Optional.of(existing));

        UpdateOrderRequest request =
                new UpdateOrderRequest(
                        10,
                        new BigDecimal("105.00")
                );

        assertThatThrownBy(
                () -> orderService.update(
                        1L,
                        orderId,
                        request
                )
        )
                .isInstanceOf(
                        InvalidOrderTransitionException.class
                );
    }

    @Test
    @DisplayName("update rejects request when neither quantity nor price is provided")
    void updateOrder_noFieldsProvided() {

        UUID orderId = UUID.randomUUID();

        Order existing = createOrder();
        existing.setId(orderId);
        existing.setStatus(OrderStatus.NEW);

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        when(orderRepository.findByIdAndAccountId(
                orderId,
                10L
        )).thenReturn(Optional.of(existing));

        UpdateOrderRequest request =
                new UpdateOrderRequest(null, null);

        assertThatThrownBy(
                () -> orderService.update(
                        1L,
                        orderId,
                        request
                )
        )
                .isInstanceOf(InvalidOrderException.class);

        verify(orderRepository, never())
                .save(any(Order.class));
    }

    @Test
    @DisplayName("update rejects non-positive limit price")
    void updateOrder_invalidLimitPrice() {

        UUID orderId = UUID.randomUUID();

        Order existing = createOrder();
        existing.setId(orderId);
        existing.setStatus(OrderStatus.NEW);

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        when(orderRepository.findByIdAndAccountId(
                orderId,
                10L
        )).thenReturn(Optional.of(existing));

        UpdateOrderRequest request =
                new UpdateOrderRequest(
                        null,
                        BigDecimal.ZERO
                );

        assertThatThrownBy(
                () -> orderService.update(
                        1L,
                        orderId,
                        request
                )
        )
                .isInstanceOf(InvalidOrderException.class);

        verify(orderRepository, never())
                .save(any(Order.class));
    }

    @Test
    @DisplayName("update changes only quantity when price is omitted")
    void updateOrder_onlyQuantity() {

        UUID orderId = UUID.randomUUID();

        Order existing = createOrder();
        existing.setId(orderId);
        existing.setStatus(OrderStatus.NEW);
        existing.setQuantity(5);
        existing.setLimitPrice(
                new BigDecimal("90.00")
        );

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        when(orderRepository.findByIdAndAccountId(
                orderId,
                10L
        )).thenReturn(Optional.of(existing));

        UpdateOrderRequest request =
                new UpdateOrderRequest(10, null);

        OrderResponse response =
                orderService.update(
                        1L,
                        orderId,
                        request
                );

        assertThat(response.quantity())
                .isEqualTo(10);

        assertThat(response.limitPrice())
                .isEqualByComparingTo("90.00");

        verify(orderRepository).save(existing);
        verify(eventPublisher)
                .publishEvent(any(OrderUpdatedEvent.class));
    }

    @Test
    @DisplayName("update changes only limit price when quantity is omitted")
    void updateOrder_onlyLimitPrice() {

        UUID orderId = UUID.randomUUID();

        Order existing = createOrder();
        existing.setId(orderId);
        existing.setStatus(OrderStatus.NEW);
        existing.setQuantity(5);
        existing.setLimitPrice(
                new BigDecimal("90.00")
        );

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        when(orderRepository.findByIdAndAccountId(
                orderId,
                10L
        )).thenReturn(Optional.of(existing));

        UpdateOrderRequest request =
                new UpdateOrderRequest(
                        null,
                        new BigDecimal("95.00")
                );

        OrderResponse response =
                orderService.update(
                        1L,
                        orderId,
                        request
                );

        assertThat(response.quantity())
                .isEqualTo(5);

        assertThat(response.limitPrice())
                .isEqualByComparingTo("95.00");

        verify(orderRepository).save(existing);
        verify(eventPublisher)
                .publishEvent(any(OrderUpdatedEvent.class));
    }

    @Test
    @DisplayName("update changes both quantity and limit price")
    void updateOrder_quantityAndPrice() {

        UUID orderId = UUID.randomUUID();

        Order existing = createOrder();
        existing.setId(orderId);
        existing.setStatus(OrderStatus.NEW);

        when(orderMapper.findAccountByUserId(1L))
                .thenReturn(activeAccount);

        when(orderRepository.findByIdAndAccountId(
                orderId,
                10L
        )).thenReturn(Optional.of(existing));

        UpdateOrderRequest request =
                new UpdateOrderRequest(
                        20,
                        new BigDecimal("95.00")
                );

        OrderResponse response =
                orderService.update(
                        1L,
                        orderId,
                        request
                );

        assertThat(response.quantity())
                .isEqualTo(20);

        assertThat(response.limitPrice())
                .isEqualByComparingTo("95.00");

        assertThat(existing.getQuantity())
                .isEqualTo(20);

        assertThat(existing.getLimitPrice())
                .isEqualByComparingTo("95.00");

        verify(orderRepository).save(existing);

        verify(eventPublisher)
                .publishEvent(any(OrderUpdatedEvent.class));
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private Order createOrder() {

        Order order = new Order();

        order.setId(UUID.randomUUID());
        order.setAccountId(10L);
        order.setSymbol("AAPL");
        order.setSide(OrderSide.BUY);
        order.setOrderType(OrderType.LIMIT);
        order.setQuantity(5);
        order.setLimitPrice(
                new BigDecimal("90.00")
        );
        order.setExecutedPrice(null);
        order.setStatus(OrderStatus.NEW);
        order.setIdempotencyKey("test-key");

        return order;
    }

    private void verifyNoOrderCreated() {

        verify(orderMapper, never())
                .insert(any(Order.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }
}
