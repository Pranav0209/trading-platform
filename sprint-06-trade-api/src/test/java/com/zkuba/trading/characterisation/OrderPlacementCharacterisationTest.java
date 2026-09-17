package com.zkuba.trading.characterisation;

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
import com.zkuba.trading.exception.DuplicateOrderException;

import com.zkuba.trading.mapper.OrderMapper;
import com.zkuba.trading.repository.OrderRepository;
import com.zkuba.trading.repository.PositionRepository;
import com.zkuba.trading.service.OrderServiceImpl;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

/**
 * Characterisation tests pinning Sprint 6 order placement behavior
 * field-by-field.
 * Package: com.zkuba.trading.characterisation
 */
@Disabled("Waiting for MarketDataService implementation")
@ExtendWith(MockitoExtension.class)
public class OrderPlacementCharacterisationTest {

        @Mock
        private OrderMapper orderMapper;

        @Mock
        private OrderRepository orderRepository;

        @Mock
        private PositionRepository positionRepository;

        @InjectMocks
        private OrderServiceImpl orderService;

        private Account activeAccount;
        private Account suspendedAccount;

        @BeforeEach
        void setUp() {
                activeAccount = new Account();
                activeAccount.setId(100L);
                activeAccount.setUserId(1L);
                activeAccount.setCashBalance(new BigDecimal("5000.00"));
                activeAccount.setStatus(AccountStatus.ACTIVE);
                activeAccount.setVersion(1);

                suspendedAccount = new Account();
                suspendedAccount.setId(200L);
                suspendedAccount.setUserId(2L);
                suspendedAccount.setCashBalance(new BigDecimal("5000.00"));
                suspendedAccount.setStatus(AccountStatus.SUSPENDED);
                suspendedAccount.setVersion(1);
        }

        /*
        @Test
        @DisplayName("Pin Sprint 6: Affordable BUY order fills synchronously, updating cash, position and order row")
        void pinAffordableBuyOrderPlacement() {
                PlaceOrderRequest request = new PlaceOrderRequest(
                                100L, "AAPL", OrderSide.BUY, OrderType.LIMIT, 10, new BigDecimal("150.00"),
                                "idem-key-100");

                when(orderMapper.findAccountById(100L)).thenReturn(activeAccount);
                when(marketDataService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("145.00"));
                when(orderMapper.updateAccountBalance(eq(100L), eq(new BigDecimal("3500.00")), eq(1))).thenReturn(1);
                when(orderMapper.insert(any(Order.class))).thenReturn(1);
                when(positionRepository.findByAccountIdAndSymbol(100L, "AAPL")).thenReturn(null);

                OrderResponse response = orderService.place(1L, request);

                // Pin Response Fields
                assertThat(response.orderId()).startsWith("ORD-");
                assertThat(response.status()).isEqualTo("FILLED");
                assertThat(response.symbol()).isEqualTo("AAPL");
                assertThat(response.side()).isEqualTo("BUY");
                assertThat(response.orderType()).isEqualTo("LIMIT");
                assertThat(response.quantity()).isEqualTo(10);
                assertThat(response.limitPrice()).isEqualByComparingTo("150.00");
                assertThat(response.executedPrice()).isEqualByComparingTo("150.00");
                assertThat(response.message()).isEqualTo("Order executed");

                // Pin Cash Balance Update
                verify(orderMapper).updateAccountBalance(100L, new BigDecimal("3500.00"), 1);

                // Pin Position Creation
                ArgumentCaptor<Position> positionCaptor = ArgumentCaptor.forClass(Position.class);
                verify(positionRepository).save(positionCaptor.capture());
                Position savedPosition = positionCaptor.getValue();
                assertThat(savedPosition.getAccountId()).isEqualTo(100L);
                assertThat(savedPosition.getSymbol()).isEqualTo("AAPL");
                assertThat(savedPosition.getQuantity()).isEqualTo(10);
                assertThat(savedPosition.getAverageCost()).isEqualByComparingTo("150.00");

                // Pin Order Persistence
                ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
                verify(orderMapper).insert(orderCaptor.capture());
                Order savedOrder = orderCaptor.getValue();
                assertThat(savedOrder.getAccountId()).isEqualTo(100L);
                assertThat(savedOrder.getSymbol()).isEqualTo("AAPL");
                assertThat(savedOrder.getSide()).isEqualTo(OrderSide.BUY);
                assertThat(savedOrder.getOrderType()).isEqualTo(OrderType.LIMIT);
                assertThat(savedOrder.getQuantity()).isEqualTo(10);
                assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.FILLED);
                assertThat(savedOrder.getExecutedPrice()).isEqualByComparingTo("150.00");
                assertThat(savedOrder.getIdempotencyKey()).isEqualTo("idem-key-100");
        }
        */

        /*
        @Test
        @DisplayName("Pin Sprint 6: Reused idempotency key throws DuplicateOrderException")
        void pinReusedIdempotencyKeyThrowsDuplicateOrderException() {
                PlaceOrderRequest request = new PlaceOrderRequest(
                                100L, "AAPL", OrderSide.BUY, OrderType.LIMIT, 5, new BigDecimal("100.00"),
                                "reused-key");

                Order existingOrder = new Order();
                existingOrder.setId(UUID.randomUUID());
                existingOrder.setIdempotencyKey("reused-key");

                when(orderMapper.findByIdempotencyKey("reused-key")).thenReturn(existingOrder);

                assertThatThrownBy(() -> orderService.place(1L, request))
                                .isInstanceOf(DuplicateOrderException.class);

                verify(marketDataService, never()).getCurrentPrice(any());
                verify(orderMapper, never()).insert(any());
        }
        */

        /*
        @Test
        @DisplayName("Pin Sprint 6: Unaffordable BUY order throws InsufficientFundsException")
        void pinUnaffordableBuyOrderThrowsInsufficientFunds() {
                PlaceOrderRequest request = new PlaceOrderRequest(
                                100L, "AAPL", OrderSide.BUY, OrderType.LIMIT, 100, new BigDecimal("100.00"),
                                "unaffordable-key");

                activeAccount.setCashBalance(new BigDecimal("500.00")); // Needs 10,000.00

                when(orderMapper.findAccountById(100L)).thenReturn(activeAccount);
                when(marketDataService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("90.00"));

                assertThatThrownBy(() -> orderService.place(1L, request))
                                .isInstanceOf(InsufficientFundsException.class);

                verify(orderMapper, never()).insert(any());
        }
        */

        /*
        @Test
        @DisplayName("Pin Sprint 6: Unknown symbol pricing error propagates exception")
        void pinUnknownSymbolThrowsException() {
                PlaceOrderRequest request = new PlaceOrderRequest(
                                100L, "UNKNOWN_SYM", OrderSide.BUY, OrderType.LIMIT, 1, new BigDecimal("10.00"),
                                "unknown-sym-key");

                when(marketDataService.getCurrentPrice("UNKNOWN_SYM"))
                                .thenThrow(new IllegalArgumentException("Unknown symbol: UNKNOWN_SYM"));

                assertThatThrownBy(() -> orderService.place(1L, request))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Unknown symbol: UNKNOWN_SYM");

                verify(orderMapper, never()).insert(any());
        }
        */

        /*
        @Test
        @DisplayName("Pin Sprint 6: Non-ACTIVE (SUSPENDED) account order placement throws AccountNotActiveException")
        void pinSuspendedAccountThrowsAccountNotActiveException() {
                PlaceOrderRequest request = new PlaceOrderRequest(
                                200L, "AAPL", OrderSide.BUY, OrderType.LIMIT, 1, new BigDecimal("100.00"),
                                "suspended-acc-key");

                when(marketDataService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("90.00"));
                when(orderMapper.findAccountById(200L)).thenReturn(suspendedAccount);

                assertThatThrownBy(() -> orderService.place(2L, request))
                                .isInstanceOf(AccountNotActiveException.class);

                verify(orderMapper, never()).insert(any());
        }
        */
}