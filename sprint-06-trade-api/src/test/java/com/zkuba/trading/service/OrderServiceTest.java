package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Order;
import com.zkuba.trading.domain.enums.AccountStatus;
import com.zkuba.trading.domain.enums.OrderSide;
import com.zkuba.trading.domain.enums.OrderStatus;
import com.zkuba.trading.domain.enums.OrderType;
import com.zkuba.trading.dto.request.PlaceOrderRequest;
import com.zkuba.trading.dto.response.OrderResponse;
import com.zkuba.trading.exception.InsufficientFundsException;
import com.zkuba.trading.exception.OrderConflictException;
import com.zkuba.trading.mapper.OrderMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zkuba.trading.domain.entity.Position;
import com.zkuba.trading.exception.DuplicateOrderException;
import com.zkuba.trading.dto.request.UpdateOrderRequest;
import com.zkuba.trading.exception.InsufficientHoldingsException;
import com.zkuba.trading.exception.InvalidOrderTransitionException;
import com.zkuba.trading.repository.OrderRepository;
import com.zkuba.trading.repository.PositionRepository;
import java.util.Optional;

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
    @DisplayName("placing an order updates balance and inserts order with expected computed values")
    void placeOrder_success_updatesBalanceAndInsertsOrder() {
        PlaceOrderRequest request = new PlaceOrderRequest(null, "aapl", OrderSide.BUY, OrderType.MARKET, 2, null,
                "key-1");
        BigDecimal price = new BigDecimal("100.00");
        BigDecimal expectedBalance = new BigDecimal("800.00");

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(price);
        when(orderMapper.updateAccountBalance(10L, expectedBalance, 5)).thenReturn(1);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        OrderResponse response = orderService.place(1L, request);

        assertThat(response.status()).isEqualTo("FILLED");
        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.executedPrice()).isEqualByComparingTo("100.00");

        verify(orderMapper).updateAccountBalance(10L, expectedBalance, 5);
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(orderCaptor.capture());

        Order insertedOrder = orderCaptor.getValue();
        assertThat(insertedOrder.getSymbol()).isEqualTo("AAPL");
        assertThat(insertedOrder.getAccountId()).isEqualTo(10L);
        assertThat(insertedOrder.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(insertedOrder.getExecutedPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("insufficient funds throws before any mapper write")
    void placeOrder_insufficientFunds_throwsBeforeWrite() {
        PlaceOrderRequest request = new PlaceOrderRequest(null, "AAPL", OrderSide.BUY, OrderType.MARKET, 2, null,
                "key-2");

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("600.00"));

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(InsufficientFundsException.class);

        verify(orderMapper, never()).updateAccountBalance(any(), any(), any());
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("balance update affected rows 0 throws conflict and prevents insert")
    void placeOrder_optimisticLockFailure_throwsConflict() {
        PlaceOrderRequest request = new PlaceOrderRequest(null, "AAPL", OrderSide.BUY, OrderType.MARKET, 1, null,
                "key-3");

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.00"));
        when(orderMapper.updateAccountBalance(eq(10L), any(BigDecimal.class), eq(5))).thenReturn(0);

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(OrderConflictException.class);

        verify(orderMapper, never()).insert(any(Order.class));
    }

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
    @DisplayName("placing order with duplicate idempotencyKey throws DuplicateOrderException")
    void placeOrder_duplicateKey_throwsDuplicateOrderException() {
        PlaceOrderRequest request = new PlaceOrderRequest(null, "AAPL", OrderSide.BUY, OrderType.MARKET, 2, null,
                "key-dup");
        Order existing = new Order();

        when(orderMapper.findByIdempotencyKey("key-dup")).thenReturn(existing);

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(DuplicateOrderException.class);
    }

    @Test
    @DisplayName("sell order without enough holdings throws InsufficientHoldingsException")
    void placeOrder_sell_insufficientHoldings_throwsException() {
        PlaceOrderRequest request = new PlaceOrderRequest(null, "AAPL", OrderSide.SELL, OrderType.LIMIT, 10,
                new BigDecimal("150.00"), "key-sell-1");

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("150.00"));
        when(positionRepository.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(null);

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(InsufficientHoldingsException.class);
    }

    @Test
    @DisplayName("sell order with sufficient holdings succeeds and reduces position quantity")
    void placeOrder_sell_success_updatesPosition() {
        PlaceOrderRequest request = new PlaceOrderRequest(null, "AAPL", OrderSide.SELL, OrderType.LIMIT, 5,
                new BigDecimal("150.00"), "key-sell-2");
        Position position = new Position();
        position.setAccountId(10L);
        position.setSymbol("AAPL");
        position.setQuantity(20);
        position.setAverageCost(new BigDecimal("100.00"));

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("150.00"));
        when(positionRepository.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(position);
        when(orderMapper.updateAccountBalance(eq(10L), any(BigDecimal.class), eq(5))).thenReturn(1);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        OrderResponse response = orderService.place(1L, request);

        assertThat(response.status()).isEqualTo("FILLED");
        assertThat(response.side()).isEqualTo("SELL");
        assertThat(position.getQuantity()).isEqualTo(15);
        verify(positionRepository).save(position);
    }

    @Test
    @DisplayName("unexecutable limit buy order is saved with status NEW without updating balance or positions")
    void placeOrder_limitBuyUnexecutable_placedAsNew() {
        PlaceOrderRequest request = new PlaceOrderRequest(null, "AAPL", OrderSide.BUY, OrderType.LIMIT, 5,
                new BigDecimal("90.00"), "key-limit-new");

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.00"));
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        OrderResponse response = orderService.place(1L, request);

        assertThat(response.status()).isEqualTo("NEW");
        assertThat(response.executedPrice()).isNull();
        verify(orderMapper, never()).updateAccountBalance(any(), any(), any());
        verify(positionRepository, never()).save(any());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.NEW);
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
    @DisplayName("data integrity violation on insert returns existing order when idempotency key exists")
    void placeOrder_dbDataIntegrityViolation_returnsExistingOrder() {
        PlaceOrderRequest request = new PlaceOrderRequest(null, "AAPL", OrderSide.BUY, OrderType.MARKET, 2, null,
                "key-conflict");
        BigDecimal price = new BigDecimal("100.00");
        BigDecimal expectedBalance = new BigDecimal("800.00");

        Order existingOrder = new Order();
        existingOrder.setId(UUID.randomUUID());
        existingOrder.setAccountId(10L);
        existingOrder.setSymbol("AAPL");
        existingOrder.setSide(OrderSide.BUY);
        existingOrder.setOrderType(OrderType.MARKET);
        existingOrder.setQuantity(2);
        existingOrder.setExecutedPrice(price);
        existingOrder.setStatus(OrderStatus.FILLED);
        existingOrder.setIdempotencyKey("key-conflict");

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(price);
        when(orderMapper.updateAccountBalance(10L, expectedBalance, 5)).thenReturn(1);
        when(orderMapper.insert(any(Order.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key constraint"));
        when(orderMapper.findByIdempotencyKey("key-conflict")).thenReturn(null, existingOrder);

        OrderResponse response = orderService.place(1L, request);

        assertThat(response.status()).isEqualTo("FILLED");
        assertThat(response.message()).contains("already processed");
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
