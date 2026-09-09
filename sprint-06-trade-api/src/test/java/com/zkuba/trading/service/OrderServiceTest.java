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
import com.zkuba.trading.domain.exception.OrderConflictException;
import com.zkuba.trading.dto.request.PlaceOrderRequest;
import com.zkuba.trading.dto.response.OrderResponse;
import com.zkuba.trading.mapper.OrderMapper;
import com.zkuba.trading.mapper.PositionMapper;
import com.zkuba.trading.repository.InstrumentRepository;
import com.zkuba.trading.repository.OrderRepository;
import com.zkuba.trading.repository.PositionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    private InstrumentRepository instruments;

    @Mock
    private OrderRepository orders;

    @Mock
    private PositionRepository positions;

    @Mock
    private PositionMapper positionMapper;

    @Mock
    private MarketDataService marketDataService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Account activeAccount;
    private Instrument tradableInstrument;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "self", orderService);

        activeAccount = new Account();
        activeAccount.setId(10L);
        activeAccount.setUserId(1L);
        activeAccount.setAccountNumber("ACC-100");
        activeAccount.setCashBalance(new BigDecimal("10000.00"));
        activeAccount.setStatus(AccountStatus.ACTIVE);
        activeAccount.setVersion(1);

        tradableInstrument = new Instrument();
        tradableInstrument.setSymbol("AAPL");
        tradableInstrument.setTradable(true);
    }

    @Test
    @DisplayName("place should call balance update and order insert on OrderMapper with computed balance")
    void place_MarketBuy_Success() {
        PlaceOrderRequest request = new PlaceOrderRequest("aapl", OrderSide.BUY, OrderType.MARKET, 10, null, "key-1");
        BigDecimal currentPrice = new BigDecimal("200.00");
        BigDecimal expectedNewBalance = new BigDecimal("8000.00"); // 10,000 - (10 * 200)

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(currentPrice);
        when(positions.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(null);
        when(orderMapper.updateAccountBalance(10L, expectedNewBalance, 1)).thenReturn(1);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        OrderResponse response = orderService.place(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("FILLED");
        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.executedPrice()).isEqualTo(currentPrice);
        assertThat(response.message()).isEqualTo("Order executed");

        verify(orderMapper).updateAccountBalance(10L, expectedNewBalance, 1);
        verify(orderMapper).insert(any(Order.class));
        verify(positionMapper).upsert(eq(10L), eq("AAPL"), eq(10), eq(currentPrice));
    }

    @Test
    @DisplayName("place should throw InsufficientFundsException before any mapper write is attempted")
    void place_InsufficientFunds_ThrowsExceptionBeforeWrite() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 100, null, "key-14");
        BigDecimal currentPrice = new BigDecimal("200.00"); // total = 20,000 > cash 10,000

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-14")).thenReturn(Optional.empty());
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(currentPrice);
        when(positions.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(null);

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(InsufficientFundsException.class);

        verify(orderMapper, never()).updateAccountBalance(any(), any(), any());
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("place should throw OrderConflictException and never call insert when balance update returns 0 rows")
    void place_OptimisticLockFailure_ThrowsOrderConflictException() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 10, null, "key-conflict");
        BigDecimal currentPrice = new BigDecimal("200.00");

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-conflict")).thenReturn(Optional.empty());
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(currentPrice);
        when(positions.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(null);
        when(orderMapper.updateAccountBalance(eq(10L), any(), eq(1))).thenReturn(0); // 0 affected rows

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(OrderConflictException.class);

        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("cancel should throw OrderConflictException when conditional update returns 0 affected rows")
    void cancel_UpdateZeroRows_ThrowsOrderConflictException() {
        UUID orderId = UUID.randomUUID();
        Order newOrder = new Order();
        newOrder.setId(orderId);
        newOrder.setAccountId(10L);
        newOrder.setStatus(OrderStatus.NEW);

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orders.findById(orderId)).thenReturn(Optional.of(newOrder));
        when(orderMapper.cancelOrderConditionally(orderId, 10L, "NEW")).thenReturn(0);

        assertThatThrownBy(() -> orderService.cancel(1L, orderId))
                .isInstanceOf(OrderConflictException.class);
    }

    @Test
    @DisplayName("cancel should successfully set order status to CANCELLED when conditional update returns 1")
    void cancel_Success() {
        UUID orderId = UUID.randomUUID();
        Order newOrder = new Order();
        newOrder.setId(orderId);
        newOrder.setAccountId(10L);
        newOrder.setStatus(OrderStatus.NEW);
        newOrder.setSymbol("AAPL");
        newOrder.setSide(OrderSide.BUY);
        newOrder.setOrderType(OrderType.LIMIT);
        newOrder.setQuantity(10);

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orders.findById(orderId)).thenReturn(Optional.of(newOrder));
        when(orderMapper.cancelOrderConditionally(orderId, 10L, "NEW")).thenReturn(1);

        OrderResponse response = orderService.cancel(1L, orderId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.message()).isEqualTo("Order cancelled");
    }

    @Test
    @DisplayName("cancel should throw ForbiddenException when order does not belong to user account")
    void cancel_Forbidden_ThrowsException() {
        UUID orderId = UUID.randomUUID();
        Order orderOfOtherUser = new Order();
        orderOfOtherUser.setId(orderId);
        orderOfOtherUser.setAccountId(999L);

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orders.findById(orderId)).thenReturn(Optional.of(orderOfOtherUser));

        assertThatThrownBy(() -> orderService.cancel(1L, orderId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("cancel should throw InvalidOrderTransitionException when order status is not NEW")
    void cancel_InvalidTransition_ThrowsException() {
        UUID orderId = UUID.randomUUID();
        Order filledOrder = new Order();
        filledOrder.setId(orderId);
        filledOrder.setAccountId(10L);
        filledOrder.setStatus(OrderStatus.FILLED);

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orders.findById(orderId)).thenReturn(Optional.of(filledOrder));

        assertThatThrownBy(() -> orderService.cancel(1L, orderId))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    @DisplayName("getOrderHistory should return list of orders from OrderMapper")
    void getOrderHistory_Success() {
        Order o1 = new Order();
        o1.setId(UUID.randomUUID());
        o1.setAccountId(10L);
        o1.setStatus(OrderStatus.FILLED);
        o1.setSymbol("AAPL");
        o1.setSide(OrderSide.BUY);
        o1.setOrderType(OrderType.MARKET);
        o1.setQuantity(5);

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orderMapper.findByAccountId(10L)).thenReturn(List.of(o1));

        List<OrderResponse> history = orderService.getOrderHistory(1L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).symbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("place should throw AccountNotFoundException when account is null")
    void place_AccountNotFound_ThrowsException() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 10, null, "key-6");
        when(orderMapper.findAccountByUserId(1L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("place should throw AccountNotActiveException when account is not active")
    void place_AccountInactive_ThrowsException() {
        activeAccount.setStatus(AccountStatus.SUSPENDED);
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 10, null, "key-7");
        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    @DisplayName("place should throw InsufficientHoldingsException when selling more shares than owned")
    void place_InsufficientHoldings_ThrowsException() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.SELL, OrderType.MARKET, 20, null, "key-15");
        BigDecimal currentPrice = new BigDecimal("200.00");

        Position existingPosition = new Position();
        existingPosition.setAccountId(10L);
        existingPosition.setSymbol("AAPL");
        existingPosition.setQuantity(10);

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-15")).thenReturn(Optional.empty());
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(currentPrice);
        when(positions.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(existingPosition);

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(InsufficientHoldingsException.class);
    }
}
