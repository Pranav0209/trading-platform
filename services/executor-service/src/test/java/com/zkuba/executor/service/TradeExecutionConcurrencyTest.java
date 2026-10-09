package com.zkuba.executor.service;

import com.zkuba.executor.client.QuoteResponse;
import com.zkuba.executor.domain.entity.Account;
import com.zkuba.executor.domain.entity.Order;
import com.zkuba.executor.domain.entity.Position;
import com.zkuba.executor.domain.enums.AccountStatus;
import com.zkuba.executor.domain.enums.OrderSide;
import com.zkuba.executor.domain.enums.OrderStatus;
import com.zkuba.executor.domain.enums.OrderType;
import com.zkuba.executor.event.TradeEventPayload;
import com.zkuba.executor.repository.AccountRepository;
import com.zkuba.executor.repository.OrderRepository;
import com.zkuba.executor.repository.PositionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import com.zkuba.executor.client.QuoteClient;
import com.zkuba.executor.client.CurrencyConverter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "jwt.secret=secretsecretsecretsecretsecretsecret"
})
public class TradeExecutionConcurrencyTest {

    @Autowired
    private TradeExecutionService tradeExecutionService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PositionRepository positionRepository;

    @MockBean
    private QuoteClient quoteClient;

    @MockBean
    private CurrencyConverter currencyConverter;

    @Test
    public void testConcurrentSellOrders_preventShortSelling() throws InterruptedException {
        // Setup initial state
        Account initialAccount = accountRepository.findAll().stream().findFirst().orElseThrow();
        initialAccount.setStatus(AccountStatus.ACTIVE);
        initialAccount.setCashBalance(new BigDecimal("10000.00"));
        Account account = accountRepository.save(initialAccount);

        Position position = positionRepository.findByAccountIdAndSymbol(account.getId(), "MSFT").orElseGet(() -> {
            Position pos = new Position();
            pos.setAccountId(account.getId());
            pos.setSymbol("MSFT");
            return pos;
        });
        position.setQuantity(10);
        position.setAverageCost(new BigDecimal("200.00"));
        positionRepository.save(position);

        Order order1 = new Order();
        order1.setId(UUID.randomUUID());
        order1.setAccountId(account.getId());
        order1.setSymbol("MSFT");
        order1.setSide(OrderSide.SELL);
        order1.setOrderType(OrderType.MARKET);
        order1.setQuantity(10);
        order1.setStatus(OrderStatus.NEW);
        order1.setIdempotencyKey(UUID.randomUUID().toString());
        order1.setCreatedAt(OffsetDateTime.now());
        order1.setExecutedOn(OffsetDateTime.now());
        order1 = orderRepository.save(order1);

        Order order2 = new Order();
        order2.setId(UUID.randomUUID());
        order2.setAccountId(account.getId());
        order2.setSymbol("MSFT");
        order2.setSide(OrderSide.SELL);
        order2.setOrderType(OrderType.MARKET);
        order2.setQuantity(10);
        order2.setStatus(OrderStatus.NEW);
        order2.setIdempotencyKey(UUID.randomUUID().toString());
        order2.setCreatedAt(OffsetDateTime.now());
        order2.setExecutedOn(OffsetDateTime.now());
        order2 = orderRepository.save(order2);

        QuoteResponse.Data quote = new QuoteResponse.Data(
                "MSFT",
                new BigDecimal("250.00"),
                new BigDecimal("249.00"),
                new BigDecimal("251.00"),
                "USD",
                null, null, null, OffsetDateTime.now(), "OPEN"
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectCount = new AtomicInteger(0);

        Order finalOrder1 = order1;
        executor.submit(() -> {
            try {
                Optional<TradeEventPayload> result = tradeExecutionService.executeLimitOrder(finalOrder1.getId(), quote);
                if (result.isPresent() && "FILLED".equals(result.get().status())) {
                    successCount.incrementAndGet();
                } else if (result.isPresent() && "REJECTED".equals(result.get().status()) && "INSUFFICIENT_HOLDINGS".equals(result.get().reason())) {
                    rejectCount.incrementAndGet();
                }
            } catch (Exception e) {
                // Ignore for test
            } finally {
                latch.countDown();
            }
        });

        Order finalOrder2 = order2;
        executor.submit(() -> {
            try {
                Optional<TradeEventPayload> result = tradeExecutionService.executeLimitOrder(finalOrder2.getId(), quote);
                if (result.isPresent() && "FILLED".equals(result.get().status())) {
                    successCount.incrementAndGet();
                } else if (result.isPresent() && "REJECTED".equals(result.get().status()) && "INSUFFICIENT_HOLDINGS".equals(result.get().reason())) {
                    rejectCount.incrementAndGet();
                }
            } catch (Exception e) {
                // Ignore for test
            } finally {
                latch.countDown();
            }
        });

        latch.await();

        // Exactly one should succeed, exactly one should be rejected due to insufficient holdings
        assertEquals(1, successCount.get(), "Only one sell order should succeed");
        assertEquals(1, rejectCount.get(), "The second sell order must be rejected for insufficient holdings");

        Optional<Position> finalPosition = positionRepository.findByAccountIdAndSymbol(account.getId(), "MSFT");
        assertTrue(finalPosition.isEmpty(), "Position should be completely sold and deleted");
    }
}
