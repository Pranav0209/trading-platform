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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    private AccountMapper accountMapper;

    @Mock
    private AccountRepository accounts;

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
    private OrderService orderService;

    private Account activeAccount;
    private Instrument tradableInstrument;

    @BeforeEach
    void setUp() {
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
    @DisplayName("place should execute Market Buy order successfully when funds are sufficient")
    void place_MarketBuy_Success() {
        PlaceOrderRequest request = new PlaceOrderRequest("aapl", OrderSide.BUY, OrderType.MARKET, 10, null, "key-1");
        BigDecimal currentPrice = new BigDecimal("200.00");

        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(currentPrice);
        when(positions.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(null);

        OrderResponse response = orderService.place(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("FILLED");
        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.executedPrice()).isEqualTo(currentPrice);
        assertThat(response.message()).isEqualTo("Order executed");

        verify(positionMapper).upsert(eq(10L), eq("AAPL"), eq(10), eq(currentPrice));
        verify(orders).save(any(Order.class));
    }

    @Test
    @DisplayName("place should update average cost correctly when adding to existing position")
    void place_MarketBuy_ExistingPosition_Success() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 10, null, "key-2");
        BigDecimal currentPrice = new BigDecimal("200.00");

        Position existingPosition = new Position();
        existingPosition.setAccountId(10L);
        existingPosition.setSymbol("AAPL");
        existingPosition.setQuantity(10);
        existingPosition.setAverageCost(new BigDecimal("100.00"));

        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(currentPrice);
        when(positions.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(existingPosition);

        OrderResponse response = orderService.place(1L, request);

        assertThat(response.status()).isEqualTo("FILLED");
        // (10 * 100 + 10 * 200) / 20 = 150.00
        verify(positionMapper).upsert(eq(10L), eq("AAPL"), eq(20), eq(new BigDecimal("150.00000000")));
    }

    @Test
    @DisplayName("place should accept Limit Buy order as NEW when market price is higher than limit price")
    void place_LimitBuy_Pending() {
        BigDecimal limitPrice = new BigDecimal("180.00");
        BigDecimal currentPrice = new BigDecimal("200.00");
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 10, limitPrice, "key-3");

        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-3")).thenReturn(Optional.empty());
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(currentPrice);

        OrderResponse response = orderService.place(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("NEW");
        assertThat(response.message()).isEqualTo("Limit order accepted and waiting for execution");

        verify(orders).save(any(Order.class));
        verify(positionMapper, never()).upsert(any(), any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("place should execute Limit Buy order immediately when market price is lower than or equal to limit price")
    void place_LimitBuy_Triggered_Executes() {
        BigDecimal limitPrice = new BigDecimal("210.00");
        BigDecimal currentPrice = new BigDecimal("200.00");
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 10, limitPrice, "key-4");

        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-4")).thenReturn(Optional.empty());
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(currentPrice);
        when(positions.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(null);

        OrderResponse response = orderService.place(1L, request);

        assertThat(response.status()).isEqualTo("FILLED");
        verify(positionMapper).upsert(eq(10L), eq("AAPL"), eq(10), eq(currentPrice));
    }

    @Test
    @DisplayName("place should execute Market Sell order successfully when holdings are sufficient")
    void place_MarketSell_Success() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.SELL, OrderType.MARKET, 5, null, "key-5");
        BigDecimal currentPrice = new BigDecimal("200.00");

        Position existingPosition = new Position();
        existingPosition.setAccountId(10L);
        existingPosition.setSymbol("AAPL");
        existingPosition.setQuantity(10);
        existingPosition.setAverageCost(new BigDecimal("150.00"));

        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-5")).thenReturn(Optional.empty());
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(currentPrice);
        when(positions.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(existingPosition);

        OrderResponse response = orderService.place(1L, request);

        assertThat(response.status()).isEqualTo("FILLED");
        verify(positionMapper).upsert(eq(10L), eq("AAPL"), eq(5), eq(new BigDecimal("150.00")));
    }

    @Test
    @DisplayName("place should throw AccountNotFoundException when user account does not exist")
    void place_AccountNotFound_ThrowsException() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 10, null, "key-6");
        when(accounts.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("place should throw AccountNotActiveException when account status is not ACTIVE")
    void place_AccountInactive_ThrowsException() {
        activeAccount.setStatus(AccountStatus.SUSPENDED);
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 10, null, "key-7");
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    @DisplayName("place should throw InstrumentNotFoundException when symbol does not exist")
    void place_InstrumentNotFound_ThrowsException() {
        PlaceOrderRequest request = new PlaceOrderRequest("INVALID", OrderSide.BUY, OrderType.MARKET, 10, null, "key-8");
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(InstrumentNotFoundException.class);
    }

    @Test
    @DisplayName("place should throw InvalidOrderException when instrument is not tradable")
    void place_InstrumentNotTradable_ThrowsException() {
        tradableInstrument.setTradable(false);
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 10, null, "key-9");
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessage("Instrument is not tradable");
    }

    @Test
    @DisplayName("place should throw DuplicateOrderException when idempotencyKey already exists")
    void place_DuplicateIdempotencyKey_ThrowsException() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 10, null, "dup-key");
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("dup-key")).thenReturn(Optional.of(new Order()));

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(DuplicateOrderException.class);
    }

    @Test
    @DisplayName("place should throw InvalidOrderException for zero or negative quantity")
    void place_InvalidQuantity_ThrowsException() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 0, null, "key-10");
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-10")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessage("Quantity must be positive");
    }

    @Test
    @DisplayName("place should throw InvalidOrderException for Limit order missing limit price")
    void place_LimitOrderMissingPrice_ThrowsException() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 10, null, "key-11");
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-11")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessage("Limit price is required for limit orders");
    }

    @Test
    @DisplayName("place should throw InvalidOrderException for Market order containing limit price")
    void place_MarketOrderWithLimitPrice_ThrowsException() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 10, new BigDecimal("100.00"), "key-12");
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-12")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessage("Limit price must be omitted for market orders");
    }

    @Test
    @DisplayName("place should throw InvalidOrderException for negative limit price")
    void place_NegativeLimitPrice_ThrowsException() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 10, new BigDecimal("-10.00"), "key-13");
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-13")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessage("Limit price must be positive");
    }

    @Test
    @DisplayName("place should throw InsufficientFundsException when cash balance is lower than total purchase value")
    void place_InsufficientFunds_ThrowsException() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 100, null, "key-14");
        BigDecimal currentPrice = new BigDecimal("200.00"); // total = 20,000 > cash 10,000

        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-14")).thenReturn(Optional.empty());
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(currentPrice);
        when(positions.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(null);

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("place should throw InsufficientHoldingsException when user attempts to sell more shares than owned")
    void place_InsufficientHoldings_ThrowsException() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.SELL, OrderType.MARKET, 20, null, "key-15");
        BigDecimal currentPrice = new BigDecimal("200.00");

        Position existingPosition = new Position();
        existingPosition.setAccountId(10L);
        existingPosition.setSymbol("AAPL");
        existingPosition.setQuantity(10); // only owns 10, trying to sell 20

        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-15")).thenReturn(Optional.empty());
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(currentPrice);
        when(positions.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(existingPosition);

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(InsufficientHoldingsException.class);
    }

    @Test
    @DisplayName("place should throw DuplicateOrderException when DB throws DataIntegrityViolationException on save")
    void place_DataIntegrityViolation_ThrowsDuplicateOrderException() {
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 10, null, "key-17");
        BigDecimal currentPrice = new BigDecimal("200.00");

        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(instruments.findById("AAPL")).thenReturn(Optional.of(tradableInstrument));
        when(orders.findByIdempotencyKey("key-17")).thenReturn(Optional.empty());
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(currentPrice);
        when(positions.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(null);
        when(orders.save(any(Order.class))).thenThrow(new DataIntegrityViolationException("Unique constraint failure"));

        assertThatThrownBy(() -> orderService.place(1L, request))
                .isInstanceOf(DuplicateOrderException.class);
    }

    @Test
    @DisplayName("cancel should successfully set order status to CANCELLED")
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

        when(orders.findById(orderId)).thenReturn(Optional.of(newOrder));
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));

        OrderResponse response = orderService.cancel(1L, orderId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.message()).isEqualTo("Order cancelled");

        verify(orders).save(newOrder);
    }

    @Test
    @DisplayName("cancel should throw ForbiddenException when order does not belong to user account")
    void cancel_Forbidden_ThrowsException() {
        UUID orderId = UUID.randomUUID();
        Order orderOfOtherUser = new Order();
        orderOfOtherUser.setId(orderId);
        orderOfOtherUser.setAccountId(999L); // different account

        when(orders.findById(orderId)).thenReturn(Optional.of(orderOfOtherUser));
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));

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

        when(orders.findById(orderId)).thenReturn(Optional.of(filledOrder));
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> orderService.cancel(1L, orderId))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    @DisplayName("getOrderHistory should return list of orders for user account sorted by createdAt desc")
    void getOrderHistory_Success() {
        Order o1 = new Order();
        o1.setId(UUID.randomUUID());
        o1.setAccountId(10L);
        o1.setStatus(OrderStatus.FILLED);
        o1.setSymbol("AAPL");
        o1.setSide(OrderSide.BUY);
        o1.setOrderType(OrderType.MARKET);
        o1.setQuantity(5);

        when(accounts.findByUserId(1L)).thenReturn(Optional.of(activeAccount));
        when(orders.findByAccountIdOrderByCreatedAtDesc(10L)).thenReturn(java.util.List.of(o1));

        java.util.List<OrderResponse> history = orderService.getOrderHistory(1L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).symbol()).isEqualTo("AAPL");
    }
}
