package com.zkuba.trading.service;

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
import com.zkuba.trading.exception.DuplicateOrderException;
import com.zkuba.trading.exception.InvalidOrderException;
import com.zkuba.trading.exception.InvalidOrderTransitionException;
import com.zkuba.trading.exception.OrderConflictException;
import com.zkuba.trading.exception.InsufficientHoldingsException;
import com.zkuba.trading.mapper.OrderMapper;
import com.zkuba.trading.repository.OrderRepository;
import com.zkuba.trading.repository.PositionRepository;
import com.zkuba.trading.domain.entity.Position;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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

    @Test
    @DisplayName("an accepted order is written at NEW and answered NEW")
    void placeOrder_valid_placedAsNewAndReturnsNew() {
        PlaceOrderRequest request = new PlaceOrderRequest(null, "AAPL", OrderSide.BUY, OrderType.MARKET, 2, null, "key-1");
        
        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        OrderResponse response = orderService.place(1L, request);

        assertThat(response.status()).isEqualTo("NEW");
        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.executedPrice()).isNull();

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.NEW);
    }

    @Test
    @DisplayName("ORDER_PLACED is published for a newly accepted order")
    void placeOrder_valid_publishesEvent() {
        PlaceOrderRequest request = new PlaceOrderRequest(null, "AAPL", OrderSide.BUY, OrderType.MARKET, 2, null, "key-1");
        
        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        orderService.place(1L, request);

        ArgumentCaptor<OrderPlacedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        Order eventOrder = eventCaptor.getValue().order();
        assertThat(eventOrder.getSymbol()).isEqualTo("AAPL");
        assertThat(eventOrder.getQuantity()).isEqualTo(2);
        assertThat(eventOrder.getStatus()).isEqualTo(OrderStatus.NEW);
    }

    @Test
    @DisplayName("an order that fails validation publishes nothing")
    void placeOrder_invalidQuantity_publishesNothing() {
        // Invalid quantity 0
        PlaceOrderRequest request = new PlaceOrderRequest(null, "AAPL", OrderSide.BUY, OrderType.MARKET, 0, null, "key-1");
        
        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(InvalidOrderException.class);
                
        verify(orderMapper, never()).insert(any(Order.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("an order with duplicate idempotency key throws DuplicateOrderException and publishes nothing")
    void placeOrder_duplicateKey_publishesNothing() {
        PlaceOrderRequest request = new PlaceOrderRequest(null, "AAPL", OrderSide.BUY, OrderType.MARKET, 2, null, "key-dup");
        
        when(orderMapper.findByIdempotencyKey("key-dup")).thenReturn(new Order());
        
        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(DuplicateOrderException.class);
                
        verify(orderMapper, never()).insert(any(Order.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("data integrity violation on insert returns existing order when idempotency key exists")
    void placeOrder_dbDataIntegrityViolation_returnsExistingOrder() {
        PlaceOrderRequest request = new PlaceOrderRequest(null, "AAPL", OrderSide.BUY, OrderType.MARKET, 2, null, "key-conflict");

        Order existingOrder = new Order();
        existingOrder.setId(UUID.randomUUID());
        existingOrder.setAccountId(10L);
        existingOrder.setSymbol("AAPL");
        existingOrder.setSide(OrderSide.BUY);
        existingOrder.setOrderType(OrderType.MARKET);
        existingOrder.setStatus(OrderStatus.NEW);
        existingOrder.setIdempotencyKey("key-conflict");

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orderMapper.insert(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key constraint"));
        when(orderMapper.findByIdempotencyKey("key-conflict")).thenReturn(null, existingOrder);

        OrderResponse response = orderService.place(1L, request);

        assertThat(response.status()).isEqualTo("NEW");
        assertThat(response.message()).contains("already processed");
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    // ----------- Cancel & Update tests (unchanged functionality, keeping them passing) -----------

    @Test
    @DisplayName("cancel affected rows 0 throws conflict")
    void cancelOrder_affectedRowsZero_throwsConflict() {
        UUID orderId = UUID.randomUUID();
        Order existing = new Order();
        existing.setId(orderId);
        existing.setAccountId(10L);
        existing.setStatus(OrderStatus.NEW);

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orderMapper.findByIdAndAccountId(orderId, 10L)).thenReturn(existing);
        when(orderMapper.cancelOrderConditionally(orderId, 10L, "NEW")).thenReturn(0);

        assertThatThrownBy(() -> orderService.cancel(1L, orderId))
                .isInstanceOf(OrderConflictException.class);
    }

    @Test
    @DisplayName("cancel order in non-NEW status throws InvalidOrderTransitionException")
    void cancelOrder_invalidStatus_throwsInvalidOrderTransitionException() {
        UUID orderId = UUID.randomUUID();
        Order existing = new Order();
        existing.setId(orderId);
        existing.setAccountId(10L);
        existing.setStatus(OrderStatus.FILLED);

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orderMapper.findByIdAndAccountId(orderId, 10L)).thenReturn(existing);

        assertThatThrownBy(() -> orderService.cancel(1L, orderId))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    @DisplayName("cancel order in NEW status succeeds and transitions status to CANCELLED")
    void cancelOrder_statusNew_success() {
        UUID orderId = UUID.randomUUID();
        Order existing = new Order();
        existing.setId(orderId);
        existing.setAccountId(10L);
        existing.setSymbol("AAPL");
        existing.setSide(OrderSide.BUY);
        existing.setOrderType(OrderType.LIMIT);
        existing.setQuantity(5);
        existing.setLimitPrice(new BigDecimal("90.00"));
        existing.setStatus(OrderStatus.NEW);

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orderMapper.findByIdAndAccountId(orderId, 10L)).thenReturn(existing);
        when(orderMapper.cancelOrderConditionally(orderId, 10L, "NEW")).thenReturn(1);

        OrderResponse response = orderService.cancel(1L, orderId);

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(orderMapper).cancelOrderConditionally(orderId, 10L, "NEW");
    }

    @Test
    @DisplayName("updating pending limit order that remains unexecutable stays NEW")
    void updateOrder_unexecutableLimit_remainsNew() {
        UUID orderId = UUID.randomUUID();
        Order existing = new Order();
        existing.setId(orderId);
        existing.setAccountId(10L);
        existing.setSymbol("AAPL");
        existing.setSide(OrderSide.BUY);
        existing.setOrderType(OrderType.LIMIT);
        existing.setQuantity(5);
        existing.setLimitPrice(new BigDecimal("90.00"));
        existing.setStatus(OrderStatus.NEW);

        UpdateOrderRequest updateReq = new UpdateOrderRequest(10, new BigDecimal("95.00"));

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orderRepository.findByIdAndAccountId(orderId, 10L)).thenReturn(Optional.of(existing));
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.00"));

        OrderResponse response = orderService.update(1L, orderId, updateReq);

        assertThat(response.status()).isEqualTo("NEW");
        assertThat(response.quantity()).isEqualTo(10);
        assertThat(response.limitPrice()).isEqualByComparingTo("95.00");
        verify(orderRepository).save(existing);
    }

    @Test
    @DisplayName("updating pending limit order such that it becomes executable executes to FILLED")
    void updateOrder_becomesExecutable_executesToFilled() {
        UUID orderId = UUID.randomUUID();
        Order existing = new Order();
        existing.setId(orderId);
        existing.setAccountId(10L);
        existing.setSymbol("AAPL");
        existing.setSide(OrderSide.BUY);
        existing.setOrderType(OrderType.LIMIT);
        existing.setQuantity(2);
        existing.setLimitPrice(new BigDecimal("90.00"));
        existing.setStatus(OrderStatus.NEW);

        UpdateOrderRequest updateReq = new UpdateOrderRequest(2, new BigDecimal("105.00"));

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orderRepository.findByIdAndAccountId(orderId, 10L)).thenReturn(Optional.of(existing));
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.00"));
        when(orderMapper.updateAccountBalance(eq(10L), any(BigDecimal.class), eq(5))).thenReturn(1);

        OrderResponse response = orderService.update(1L, orderId, updateReq);

        assertThat(response.status()).isEqualTo("FILLED");
        assertThat(response.executedPrice()).isEqualByComparingTo("105.00");
        verify(orderRepository).save(existing);
    }

    @Test
    @DisplayName("updating non-NEW order throws InvalidOrderTransitionException")
    void updateOrder_nonNewStatus_throwsException() {
        UUID orderId = UUID.randomUUID();
        Order existing = new Order();
        existing.setId(orderId);
        existing.setAccountId(10L);
        existing.setStatus(OrderStatus.FILLED);

        UpdateOrderRequest updateReq = new UpdateOrderRequest(10, new BigDecimal("105.00"));

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orderRepository.findByIdAndAccountId(orderId, 10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.update(1L, orderId, updateReq))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }
}
