package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Order;
import com.zkuba.trading.domain.entity.Position;
import com.zkuba.trading.domain.enums.AccountStatus;
import com.zkuba.trading.domain.enums.OrderSide;
import com.zkuba.trading.domain.enums.OrderStatus;
import com.zkuba.trading.dto.request.CreateAccountRequest;
import com.zkuba.trading.dto.response.AccountResponse;
import com.zkuba.trading.dto.response.BalanceResponse;
import com.zkuba.trading.dto.response.OrderHistoryEntry;
import com.zkuba.trading.dto.response.PositionResponse;
import com.zkuba.trading.exception.AccountAlreadyExistsException;
import com.zkuba.trading.exception.AccountNotActiveException;
import com.zkuba.trading.exception.AccountNotFoundException;
import com.zkuba.trading.exception.ForbiddenException;
import com.zkuba.trading.mapper.OrderMapper;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.PositionRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
class AccountServiceTest {

        @Mock
        private AccountRepository accounts;

        @Mock
        private PositionRepository positionRepository;

        @Mock
        private OrderMapper orderMapper;

        @InjectMocks
        private AccountServiceImpl accountService;

        private BigDecimal defaultBalance;
        private Account activeAccount;

        @BeforeEach
        void setUp() {
                defaultBalance = new BigDecimal("10000.00");

                ReflectionTestUtils.setField(
                                accountService,
                                "defaultBalance",
                                defaultBalance);

                activeAccount = new Account();
                activeAccount.setId(100L);
                activeAccount.setUserId(1L);
                activeAccount.setAccountNumber("ACC-12345678");
                activeAccount.setCashBalance(defaultBalance);
                activeAccount.setCurrency("USD");
                activeAccount.setStatus(AccountStatus.ACTIVE);
                activeAccount.setVersion(0);
        }

        // =========================================================
        // create()
        // =========================================================

        @Test
        @DisplayName("create creates account with default USD currency when currency is null")
        void create_Success_DefaultCurrency() {

                CreateAccountRequest request = new CreateAccountRequest(1L, null);

                when(accounts.findByUserId(1L))
                                .thenReturn(Optional.empty());
                when(accounts.save(any(Account.class))).thenAnswer(i -> {
                        Account a = i.getArgument(0);
                        a.setId(100L);
                        return a;
                });

                AccountResponse response = accountService.create(request);

                assertThat(response).isNotNull();
                assertThat(response.cashBalance())
                                .isEqualByComparingTo(defaultBalance);
                assertThat(response.status())
                                .isEqualTo("ACTIVE");

                verify(accounts).findByUserId(1L);
                verify(accounts).save(any(Account.class));
        }

        @Test
        @DisplayName("create creates account with requested currency")
        void create_Success_CustomCurrency() {

                CreateAccountRequest request = new CreateAccountRequest(1L, "EUR");

                when(accounts.findByUserId(1L))
                                .thenReturn(Optional.empty());
                when(accounts.save(any(Account.class))).thenAnswer(i -> {
                        Account a = i.getArgument(0);
                        a.setId(100L);
                        return a;
                });

                AccountResponse response = accountService.create(request);

                assertThat(response).isNotNull();
                assertThat(response.cashBalance())
                                .isEqualByComparingTo(defaultBalance);

                verify(accounts).save(any(Account.class));
        }

        @Test
        @DisplayName("create rejects creation when user already has an account")
        void create_AlreadyExists_ThrowsException() {

                CreateAccountRequest request = new CreateAccountRequest(1L, "USD");

                when(accounts.findByUserId(1L))
                                .thenReturn(Optional.of(activeAccount));

                assertThatThrownBy(
                                () -> accountService.create(request))
                                .isInstanceOf(AccountAlreadyExistsException.class);

                verify(accounts, never())
                                .save(any(Account.class));
        }

        // =========================================================
        // getByUserId()
        // =========================================================

        @Test
        @DisplayName("getByUserId returns active account")
        void getByUserId_Success() {

                when(accounts.findByUserId(1L))
                                .thenReturn(Optional.of(activeAccount));

                Account account = accountService.getByUserId(1L);

                assertThat(account).isNotNull();
                assertThat(account.getId()).isEqualTo(100L);
                assertThat(account.getUserId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("getByUserId throws when account does not exist")
        void getByUserId_NotFound_ThrowsException() {

                when(accounts.findByUserId(99L))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(
                                () -> accountService.getByUserId(99L))
                                .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("getByUserId throws when account is suspended")
        void getByUserId_Inactive_ThrowsException() {

                activeAccount.setStatus(AccountStatus.SUSPENDED);

                when(accounts.findByUserId(1L))
                                .thenReturn(Optional.of(activeAccount));

                assertThatThrownBy(
                                () -> accountService.getByUserId(1L))
                                .isInstanceOf(AccountNotActiveException.class);
        }

        // =========================================================
        // getAccountAndValidateAccess()
        // =========================================================

        @Test
        @DisplayName("getAccountAndValidateAccess returns account for correct user")
        void getAccountAndValidateAccess_Success() {

                when(accounts.findById(100L))
                                .thenReturn(Optional.of(activeAccount));

                Account result = accountService.getAccountAndValidateAccess(
                                100L,
                                1L);

                assertThat(result).isSameAs(activeAccount);
        }

        @Test
        @DisplayName("getAccountAndValidateAccess throws when account does not exist")
        void getAccountAndValidateAccess_NotFound() {

                when(accounts.findById(999L))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(
                                () -> accountService.getAccountAndValidateAccess(
                                                999L,
                                                1L))
                                .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("getAccountAndValidateAccess rejects access from another user")
        void getAccountAndValidateAccess_WrongUser() {

                when(accounts.findById(100L))
                                .thenReturn(Optional.of(activeAccount));

                assertThatThrownBy(
                                () -> accountService.getAccountAndValidateAccess(
                                                100L,
                                                999L))
                                .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("getAccountAndValidateAccess rejects inactive account")
        void getAccountAndValidateAccess_Inactive() {

                activeAccount.setStatus(AccountStatus.SUSPENDED);

                when(accounts.findById(100L))
                                .thenReturn(Optional.of(activeAccount));

                assertThatThrownBy(
                                () -> accountService.getAccountAndValidateAccess(
                                                100L,
                                                1L))
                                .isInstanceOf(AccountNotActiveException.class);
        }

        // =========================================================
        // getAccountResponseById()
        // =========================================================

        @Test
        @DisplayName("getAccountResponseById returns mapped account response")
        void getAccountResponseById_Success() {
                when(accounts.findById(100L))
                                .thenReturn(Optional.of(activeAccount));
                AccountResponse response = accountService.getAccountResponseById(
                                100L,
                                1L);

                assertThat(response).isNotNull();
                assertThat(response.id()).isEqualTo(100L);
                assertThat(response.accountId())
                                .isEqualTo("ACC-12345678");
                assertThat(response.holderName())
                                .isEqualTo("User 1");
                assertThat(response.cashBalance())
                                .isEqualByComparingTo(defaultBalance);
                assertThat(response.status())
                                .isEqualTo("ACTIVE");
                assertThat(response.version())
                                .isEqualTo(0);
        }

        // =========================================================
        // getBalance()
        // =========================================================

        @Test
        @DisplayName("getBalance returns account balance and currency")
        void getBalance_Success() {

                when(accounts.findById(100L))
                                .thenReturn(Optional.of(activeAccount));

                BalanceResponse response = accountService.getBalance(100L, 1L);

                assertThat(response).isNotNull();
                assertThat(response.accountId())
                                .isEqualTo(100L);
                assertThat(response.cashBalance())
                                .isEqualByComparingTo(defaultBalance);
                assertThat(response.currency())
                                .isEqualTo("USD");
                assertThat(response.asOf())
                                .isNotBlank();
        }

        @Test
        @DisplayName("getBalance uses USD when account currency is null")
        void getBalance_NullCurrency_UsesUsd() {

                activeAccount.setCurrency(null);

                when(accounts.findById(100L))
                                .thenReturn(Optional.of(activeAccount));

                BalanceResponse response = accountService.getBalance(100L, 1L);

                assertThat(response.currency())
                                .isEqualTo("USD");
        }

        // =========================================================
        // getPositionsResponse()
        // =========================================================

        @Test
        @DisplayName("getPositionsResponse returns only positions with positive quantity")
        void getPositionsResponse_FiltersZeroQuantity() {

                Position activePosition = new Position();
                activePosition.setAccountId(100L);
                activePosition.setSymbol("AAPL");
                activePosition.setQuantity(10);
                activePosition.setAverageCost(
                                new BigDecimal("150.00"));

                Position zeroPosition = new Position();
                zeroPosition.setAccountId(100L);
                zeroPosition.setSymbol("MSFT");
                zeroPosition.setQuantity(0);
                zeroPosition.setAverageCost(
                                new BigDecimal("300.00"));

                when(accounts.findById(100L))
                                .thenReturn(Optional.of(activeAccount));

                when(positionRepository.findByAccountId(100L))
                                .thenReturn(List.of(
                                                activePosition,
                                                zeroPosition));

                List<PositionResponse> result = accountService.getPositionsResponse(
                                100L,
                                1L);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).symbol())
                                .isEqualTo("AAPL");
                assertThat(result.get(0).quantity())
                                .isEqualTo(10);
                assertThat(result.get(0).averageCost())
                                .isEqualByComparingTo("150.00");
        }

        @Test
        @DisplayName("getPositionsResponse returns empty list when no positions exist")
        void getPositionsResponse_NoPositions() {

                when(accounts.findById(100L))
                                .thenReturn(Optional.of(activeAccount));

                when(positionRepository.findByAccountId(100L))
                                .thenReturn(List.of());

                List<PositionResponse> result = accountService.getPositionsResponse(
                                100L,
                                1L);

                assertThat(result).isEmpty();
        }

        // =========================================================
        // getOrderHistory()
        // =========================================================

        @Test
        @DisplayName("getOrderHistory maps orders and passes status filter")
        void getOrderHistory_WithStatus() {

                OffsetDateTime from = OffsetDateTime.parse("2026-01-01T00:00:00Z");

                OffsetDateTime to = OffsetDateTime.parse("2026-02-01T00:00:00Z");

                Order order = createOrder();

                when(accounts.findById(100L))
                                .thenReturn(Optional.of(activeAccount));

                when(orderMapper.findByAccountIdFiltered(
                                eq(100L),
                                eq("FILLED"),
                                eq(from),
                                eq(to))).thenReturn(List.of(order));

                List<OrderHistoryEntry> result = accountService.getOrderHistory(
                                100L,
                                OrderStatus.FILLED,
                                from,
                                to,
                                1L);

                assertThat(result).hasSize(1);

                OrderHistoryEntry entry = result.get(0);

                assertThat(entry.orderId())
                                .isEqualTo("ORD-" + order.getId());
                assertThat(entry.accountId())
                                .isEqualTo(100L);
                assertThat(entry.symbol())
                                .isEqualTo("AAPL");
                assertThat(entry.side())
                                .isEqualTo("BUY");
                assertThat(entry.quantity())
                                .isEqualTo(10);
                assertThat(entry.price())
                                .isEqualByComparingTo("150.00");
                assertThat(entry.executedPrice())
                                .isEqualByComparingTo("149.50");
                assertThat(entry.status())
                                .isEqualTo("FILLED");
                assertThat(entry.idempotencyKey())
                                .isEqualTo("idem-123");
                assertThat(entry.createdOn())
                                .isNotNull();

                verify(orderMapper).findByAccountIdFiltered(
                                100L,
                                "FILLED",
                                from,
                                to);
        }

        @Test
        @DisplayName("getOrderHistory passes null status when no status filter is supplied")
        void getOrderHistory_WithoutStatus() {

                when(accounts.findById(100L))
                                .thenReturn(Optional.of(activeAccount));

                when(orderMapper.findByAccountIdFiltered(
                                eq(100L),
                                eq(null),
                                eq(null),
                                eq(null))).thenReturn(List.of());

                List<OrderHistoryEntry> result = accountService.getOrderHistory(
                                100L,
                                null,
                                null,
                                null,
                                1L);

                assertThat(result).isEmpty();

                verify(orderMapper).findByAccountIdFiltered(
                                100L,
                                null,
                                null,
                                null);
        }

        @Test
        @DisplayName("getOrderHistory maps null createdAt to null")
        void getOrderHistory_NullCreatedAt() {

                Order order = createOrder();
                order.setCreatedAt(null);

                when(accounts.findById(100L))
                                .thenReturn(Optional.of(activeAccount));

                when(orderMapper.findByAccountIdFiltered(
                                eq(100L),
                                eq(null),
                                eq(null),
                                eq(null))).thenReturn(List.of(order));

                List<OrderHistoryEntry> result = accountService.getOrderHistory(
                                100L,
                                null,
                                null,
                                null,
                                1L);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).createdOn())
                                .isNull();
        }

        // =========================================================
        // Helpers
        // =========================================================

        private Order createOrder() {

                Order order = new Order();

                order.setId(UUID.randomUUID());
                order.setAccountId(100L);
                order.setSymbol("AAPL");
                order.setSide(OrderSide.BUY);
                order.setQuantity(10);
                order.setLimitPrice(
                                new BigDecimal("150.00"));
                order.setExecutedPrice(
                                new BigDecimal("149.50"));
                order.setStatus(OrderStatus.FILLED);
                order.setIdempotencyKey("idem-123");
                order.setCreatedAt(
                                OffsetDateTime.parse(
                                                "2026-01-15T10:00:00Z"));

                return order;
        }
}
