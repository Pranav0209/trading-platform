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
    private MarketDataService marketDataService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Account activeAccount;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "self", orderService);

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
        PlaceOrderRequest request = new PlaceOrderRequest("aapl", OrderSide.BUY, OrderType.MARKET, 2, null, "key-1");
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
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 2, null, "key-2");

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
        PlaceOrderRequest request = new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 1, null, "key-3");

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

        when(orderMapper.findAccountByUserId(1L)).thenReturn(activeAccount);
        when(orderMapper.cancelOrderConditionally(orderId, 10L, "NEW")).thenReturn(0);

        assertThatThrownBy(() -> orderService.cancel(1L, orderId))
                .isInstanceOf(OrderConflictException.class);
    }
}
