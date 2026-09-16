package com.zkuba.executor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.executor.client.FauxnanceQuoteClient;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
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

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private FauxnanceQuoteClient fauxnanceQuoteClient;

        @MockBean
        private KafkaTemplate<String, String> kafkaTemplate;

        @Test
        void testOrderExecutionFlow() throws Exception {
                // Setup mock database state
                Account account = new Account();
                account.setId(100L);
                account.setAccountNumber("TEST-ACC-1");
                account.setCashBalance(new BigDecimal("10000.00"));
                account.setStatus(AccountStatus.ACTIVE);
                account.setUserId(100L);
                account.setCurrency("USD");
                account.setVersion(1);

                Order order = new Order();
                order.setId(UUID.randomUUID());
                order.setAccountId(account.getId());
                order.setSymbol("AAPL");
                order.setSide(OrderSide.BUY);
                order.setOrderType(OrderType.MARKET);
                order.setQuantity(10);
                order.setStatus(OrderStatus.NEW);
                order.setIdempotencyKey("TEST-IDEMP-1");
                order.setCreatedAt(OffsetDateTime.now());

                when(orderRepository.findById(order.getId())).thenReturn(java.util.Optional.of(order));
                when(accountRepository.findById(account.getId())).thenReturn(java.util.Optional.of(account));
                when(positionRepository.findByAccountIdAndSymbol(account.getId(), "AAPL"))
                                .thenReturn(java.util.Optional.empty());
                when(orderRepository.executeOrder(eq(order.getId()), any(), any(), any(java.time.OffsetDateTime.class)))
                                .thenReturn(1);

                // Mock Fauxnance API
                QuoteResponse.Data quoteData = new QuoteResponse.Data(
                                "AAPL", new BigDecimal("150.00"), new BigDecimal("149.50"), new BigDecimal("150.50"),
                                "USD", null, null, null, null, null);
                when(fauxnanceQuoteClient.getQuote("AAPL")).thenReturn(new QuoteResponse(quoteData, null));
                when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

                // Create OrderPlaced event payload
                OrderPlacedPayload payload = new OrderPlacedPayload(
                                order.getId(),
                                account.getId(),
                                "AAPL",
                                "BUY",
                                10,
                                null,
                                "TEST-IDEMP-1",
                                Instant.now());
                Envelope<OrderPlacedPayload> envelope = new Envelope<>(
                                UUID.randomUUID().toString(),
                                "ORDER_PLACED",
                                Instant.now(),
                                "trade-api",
                                1,
                                payload);
                objectMapper.findAndRegisterModules();
                String message = objectMapper.writeValueAsString(envelope);

                // Mock Kafka Acknowledgment
                Acknowledgment ack = mock(Acknowledgment.class);

                // Simulate Kafka message consumption
                orderEventListener.onOrderPlaced(message, ack);

                // Verify that Fauxnance was called
                verify(fauxnanceQuoteClient, times(1)).getQuote("AAPL");

                // Verify that the trade event was published
                verify(kafkaTemplate, times(1)).send(eq("trade-events"), anyString(), anyString());

                // Verify message was acknowledged
                verify(ack, times(1)).acknowledge();

        }
}
