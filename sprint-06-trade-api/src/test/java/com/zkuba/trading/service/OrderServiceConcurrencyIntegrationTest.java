package com.zkuba.trading.service;

import com.zkuba.trading.domain.enums.OrderSide;
import com.zkuba.trading.domain.enums.OrderType;
import com.zkuba.trading.dto.request.PlaceOrderRequest;
import com.zkuba.trading.dto.response.OrderResponse;
import com.zkuba.trading.exception.OrderConflictException;
import com.zkuba.trading.mapper.OrderMapper;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Disabled
class OrderServiceConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private OrderMapper orderMapper;

    @MockBean
    private MarketDataService marketDataService;

    @Test
    @DisplayName("two concurrent place calls on same account allow exactly one success and one conflict")
    void concurrentPlaceOrderProducesOneSuccessAndOneConflict() throws Exception {
        Long userId = createUserAndAccountWithBalance(new BigDecimal("1000.00"));
        String symbol = "AAPL";

        when(marketDataService.getCurrentPrice(symbol)).thenReturn(new BigDecimal("600.00"));

        CountDownLatch bothReadsReached = new CountDownLatch(2);
        CountDownLatch releaseReads = new CountDownLatch(1);

        doAnswer(invocation -> {
            Long requestedUserId = invocation.getArgument(0);
            Object result = invocation.callRealMethod();
            if (requestedUserId.equals(userId)) {
                bothReadsReached.countDown();
                boolean released = releaseReads.await(5, TimeUnit.SECONDS);
                if (!released) {
                    throw new IllegalStateException("Timed out while coordinating concurrent reads");
                }
            }
            return result;
        }).when(orderMapper).findAccountByUserId(userId);

        PlaceOrderRequest requestOne =
                new PlaceOrderRequest(null, symbol, OrderSide.BUY, OrderType.MARKET, 1, null,
                        "concurrency-key-" + UUID.randomUUID());
        PlaceOrderRequest requestTwo =
                new PlaceOrderRequest(null, symbol, OrderSide.BUY, OrderType.MARKET, 1, null,
                        "concurrency-key-" + UUID.randomUUID());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<OrderResponse> futureOne = executor.submit(placeCallable(userId, requestOne));
            Future<OrderResponse> futureTwo = executor.submit(placeCallable(userId, requestTwo));

            assertThat(bothReadsReached.await(5, TimeUnit.SECONDS)).isTrue();
            releaseReads.countDown();

            int successCount = 0;
            int conflictCount = 0;
            int otherFailureCount = 0;

            ResultType firstResult = classifyResult(futureOne);
            ResultType secondResult = classifyResult(futureTwo);

            successCount += firstResult == ResultType.SUCCESS ? 1 : 0;
            conflictCount += firstResult == ResultType.CONFLICT ? 1 : 0;
            otherFailureCount += firstResult == ResultType.OTHER_FAILURE ? 1 : 0;

            successCount += secondResult == ResultType.SUCCESS ? 1 : 0;
            conflictCount += secondResult == ResultType.CONFLICT ? 1 : 0;
            otherFailureCount += secondResult == ResultType.OTHER_FAILURE ? 1 : 0;

            assertThat(successCount).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(1);
            assertThat(otherFailureCount).isEqualTo(0);

            BigDecimal finalBalance = jdbcTemplate.queryForObject(
                    "SELECT cash_balance FROM accounts WHERE user_id = ?",
                    BigDecimal.class,
                    userId);
            assertThat(finalBalance).isEqualByComparingTo("400.00");
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<OrderResponse> placeCallable(Long userId, PlaceOrderRequest request) {
        return () -> orderService.place(userId, request);
    }

    private Long createUserAndAccountWithBalance(BigDecimal balance) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        Long userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users(username, email, password_hash, role, status)
                VALUES (?, ?, ?, 'ROLE_USER', 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                "user_" + unique,
                unique + "@example.com",
                "hashed-password");

        jdbcTemplate.update(
                """
                INSERT INTO accounts(account_number, user_id, cash_balance, currency, status, version)
                VALUES (?, ?, ?, 'USD', 'ACTIVE', 0)
                """,
                "ACC-" + unique,
                userId,
                balance);

        jdbcTemplate.update(
                """
                INSERT INTO instruments(symbol, display_name, asset_class, currency, tradable)
                VALUES ('AAPL', 'Apple Inc.', 'EQUITY', 'USD', TRUE)
                ON CONFLICT (symbol) DO NOTHING
                """);

        return userId;
    }

    private ResultType classifyResult(Future<OrderResponse> future) throws InterruptedException {
        try {
            future.get(10, TimeUnit.SECONDS);
            return ResultType.SUCCESS;
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            if (cause instanceof OrderConflictException) {
                return ResultType.CONFLICT;
            }
            return ResultType.OTHER_FAILURE;
        } catch (java.util.concurrent.TimeoutException timeoutException) {
            return ResultType.OTHER_FAILURE;
        }
    }

    private enum ResultType {
        SUCCESS,
        CONFLICT,
        OTHER_FAILURE
    }
}
