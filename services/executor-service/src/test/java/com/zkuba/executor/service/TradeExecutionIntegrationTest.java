package com.zkuba.executor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.executor.client.QuoteClient;
import com.zkuba.executor.client.QuoteResponse;
import com.zkuba.executor.domain.entity.Account;
import com.zkuba.executor.domain.entity.Order;
import com.zkuba.executor.domain.enums.AccountStatus;
import com.zkuba.executor.domain.enums.OrderSide;
import com.zkuba.executor.domain.enums.OrderStatus;
import com.zkuba.executor.domain.enums.OrderType;
import com.zkuba.executor.event.Envelope;
import com.zkuba.executor.event.OrderPlacedPayload;
import com.zkuba.executor.listener.OrderEventListener;
import com.zkuba.executor.repository.AccountRepository;
import com.zkuba.executor.repository.OrderRepository;
import com.zkuba.executor.repository.PositionRepository;
import com.zkuba.executor.repository.RealisedPnlRepository;
import com.zkuba.executor.repository.SmartRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
})
class TradeExecutionIntegrationTest {

    @MockBean
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OrderEventListener orderEventListener;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private PositionRepository positionRepository;

    @MockBean
    private RealisedPnlRepository realisedPnlRepository;

    @MockBean
    private SmartRuleRepository smartRuleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private QuoteClient fauxnanceQuoteClient;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void testOrderExecutionFlow() throws Exception {

        // ============================================================
        // ACCOUNT
        // ============================================================

        Account account = new Account();

        account.setId(100L);
        account.setAccountNumber("TEST-ACC-1");
        account.setCashBalance(new BigDecimal("10000.00"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setUserId(100L);
        account.setCurrency("USD");
        account.setVersion(1);

        // ============================================================
        // ORDER
        // ============================================================

        UUID orderId = UUID.randomUUID();

        Order order = new Order();

        order.setId(orderId);
        order.setAccountId(account.getId());
        order.setSymbol("AAPL");
        order.setSide(OrderSide.BUY);
        order.setOrderType(OrderType.MARKET);
        order.setQuantity(10);

        order.setLimitPrice(
                new BigDecimal("150.00")
        );

        order.setStatus(OrderStatus.NEW);
        order.setIdempotencyKey("TEST-IDEMP-1");
        order.setCreatedAt(OffsetDateTime.now());

        // ============================================================
        // ORDER REPOSITORY
        // ============================================================

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        /*
         * Do not match an exact execution price.
         * The real FillRuleEngine determines the execution price.
         */
        when(orderRepository.executeOrder(
                eq(orderId),
                eq(OrderStatus.FILLED),
                any(BigDecimal.class),
                any(OffsetDateTime.class),
                any()
        )).thenReturn(1);

        // ============================================================
        // ACCOUNT REPOSITORY
        // ============================================================

        when(accountRepository.findById(account.getId()))
                .thenReturn(Optional.of(account));

        // ============================================================
        // POSITION REPOSITORY
        // ============================================================

        when(positionRepository.findByAccountIdAndSymbol(
                account.getId(),
                "AAPL"
        )).thenReturn(Optional.empty());

        when(positionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // ============================================================
        // SMART RULE REPOSITORY
        // ============================================================

        when(smartRuleRepository.findBySymbolAndEnabledTrue("AAPL"))
                .thenReturn(java.util.Collections.emptyList());

        // ============================================================
        // FAUXNANCE QUOTE
        // ============================================================

        QuoteResponse.Data quoteData =
                new QuoteResponse.Data(
                        "AAPL",
                        new BigDecimal("150.00"),
                        new BigDecimal("149.50"),
                        new BigDecimal("150.50"),
                        "USD",
                        null,
                        null,
                        null,
                        OffsetDateTime.now(),
                        "OPEN"
                );

        QuoteResponse quoteResponse =
                new QuoteResponse(
                        quoteData,
                        null
                );

        when(fauxnanceQuoteClient.getQuote("AAPL"))
                .thenReturn(quoteResponse);

        // ============================================================
        // KAFKA
        // ============================================================

        when(kafkaTemplate.send(
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(
                CompletableFuture.completedFuture(null)
        );

        // ============================================================
        // ORDER PLACED PAYLOAD
        // ============================================================

        OrderPlacedPayload payload =
                new OrderPlacedPayload(
                        orderId
                );

        // ============================================================
        // ENVELOPE
        // ============================================================

        Envelope<OrderPlacedPayload> envelope =
                new Envelope<>(
                        UUID.randomUUID().toString(),
                        "ORDER_PLACED",
                        Instant.now(),
                        "trade-api",
                        1,
                        payload
                );

        // ============================================================
        // SERIALIZE
        // ============================================================

        objectMapper.findAndRegisterModules();

        String message =
                objectMapper.writeValueAsString(envelope);

        // ============================================================
        // ACK
        // ============================================================

        Acknowledgment ack =
                mock(Acknowledgment.class);

        // ============================================================
        // EXECUTE LISTENER
        // ============================================================

        orderEventListener.onOrderPlaced(
                message,
                ack
        );

        // ============================================================
        // VERIFY FAUXNANCE
        // ============================================================

        verify(
                fauxnanceQuoteClient,
                times(1)
        ).getQuote("AAPL");

        // ============================================================
        // VERIFY ORDER FILLED
        // ============================================================

        verify(orderRepository, times(1))
                .executeOrder(
                        eq(orderId),
                        eq(OrderStatus.FILLED),
                        any(BigDecimal.class),
                        any(OffsetDateTime.class),
                        any()
                );

        // ============================================================
        // VERIFY POSITION CREATED
        // ============================================================

        verify(positionRepository, times(1))
                .save(any());

        // ============================================================
        // VERIFY TRADE EVENT
        // ============================================================

        verify(kafkaTemplate, times(1))
                .send(
                        eq("trade-events"),
                        anyString(),
                        anyString()
                );

        // ============================================================
        // VERIFY ACK
        // ============================================================

        verify(
                ack,
                times(1)
        ).acknowledge();
    }
}