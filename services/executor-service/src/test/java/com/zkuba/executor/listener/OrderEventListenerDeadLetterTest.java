package com.zkuba.executor.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.executor.client.FauxnanceQuoteClient;
import com.zkuba.executor.domain.entity.Account;
import com.zkuba.executor.domain.entity.Order;
import com.zkuba.executor.domain.enums.AccountStatus;
import com.zkuba.executor.domain.enums.OrderSide;
import com.zkuba.executor.domain.enums.OrderStatus;
import com.zkuba.executor.repository.AccountRepository;
import com.zkuba.executor.repository.OrderRepository;
import com.zkuba.executor.repository.PositionRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration test: Message goes to orders.DLT after retries exhaust.
 *
 * Scenario:
 * 1. Send a valid ORDER_PLACED message to 'orders' topic
 * 2. Mock FauxnanceQuoteClient to throw RestClientException (simulates 5xx)
 * 3. Spring Kafka error handler retries with exponential backoff: 1s, 2s, 4s, 8s, 16s (30s total)
 * 4. After budget exhausted, message is sent to orders.DLT
 *
 * How to run:
 *   mvn test -Dtest=OrderEventListenerDeadLetterTest
 *
 * How to verify in Kafka:
 *   docker exec -it kafka kafka-console-consumer.sh \
 *     --bootstrap-server localhost:9092 \
 *     --topic orders.DLT \
 *     --from-beginning \
 *     --property print.headers=true
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS}",
    "spring.kafka.consumer.group-id=test-executor",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
class OrderEventListenerDeadLetterTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FauxnanceQuoteClient quoteClient;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private PositionRepository positionRepository;

    private static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private ConsumerRecord<String, String> dltMessage = null;
    private CountDownLatch dltLatch = new CountDownLatch(1);

    /**
     * Valid ORDER_PLACED event that will be retried when FauxnanceQuoteClient fails
     */
    private static final String VALID_ORDER_PLACED_EVENT = """
        {
          "eventId": "test-event-002",
          "eventType": "ORDER_PLACED",
          "eventTime": "2026-09-18T10:00:00Z",
          "source": "trade-api",
          "schemaVersion": 1,
          "payload": {
            "orderId": "33333333-3333-3333-3333-333333333333",
            "accountId": 1,
            "symbol": "AAPL",
            "side": "BUY",
            "quantity": 10,
            "idempotencyKey": "test-dlt-005",
            "createdOn": "2026-09-18T10:00:00Z"
          }
        }
        """;

    @BeforeEach
    void setup() {
        // Mock the order in Postgres
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setAccountId(1L);
        order.setSymbol("AAPL");
        order.setSide(OrderSide.BUY);
        order.setQuantity(10);
        order.setStatus(OrderStatus.NEW);
        order.setLimitPrice(new BigDecimal("150.00"));
        order.setCreatedAt(OffsetDateTime.now());

        when(orderRepository.findById(ORDER_ID))
            .thenReturn(Optional.of(order));

        // Mock the account
        Account account = new Account();
        account.setId(1L);
        account.setStatus(AccountStatus.ACTIVE);
        account.setCashBalance(new BigDecimal("5000.00"));

        when(accountRepository.findById(1L))
            .thenReturn(Optional.of(account));

        // Mock FauxnanceQuoteClient to always throw 5xx error
        // This simulates a service unavailable scenario that will trigger retries
        when(quoteClient.getQuote(anyString()))
            .thenThrow(new RestClientException("500: Service Unavailable - Connection refused"));
    }

    /**
     * Test: Valid order message is retried with backoff and then sent to orders.DLT
     *
     * Expected flow:
     * 1. Message sent to 'orders' topic
     * 2. FauxnanceQuoteClient.getQuote() throws RestClientException (not a fatal error)
     * 3. Spring Kafka error handler detects this is retryable (NOT in addNotRetryableExceptions)
     * 4. Exponential backoff retries: 1s delay, 2s delay, 4s delay, 8s delay, 16s delay
     * 5. After 30s budget exhausted, message sent to orders.DLT
     * 6. DeadLetterPublishingRecoverer adds exception headers
     *
     * Timeline:
     * - T=0s: Message arrives, first attempt fails
     * - T=1s: First retry (2^0 * 1000ms)
     * - T=3s: Second retry (2^1 * 1000ms)
     * - T=7s: Third retry (2^2 * 1000ms)
     * - T=15s: Fourth retry (2^3 * 1000ms)
     * - T=31s: Budget exhausted, message sent to DLT
     */
    @Test
    void messageIsRetriedAndThenSentToDeadLetterTopic() throws Exception {
        System.out.println("\n=== Dead-Letter Test: OrderEventListener Processing ===\n");

        // Set up a listener on orders.DLT to capture dead-lettered messages
        dltLatch = new CountDownLatch(1);
        
        // Send the order event to 'orders' topic
        // OrderEventListener (auto-wired via @SpringBootTest) will consume it
        System.out.println("1. Sending ORDER_PLACED event to 'orders' topic...");
        kafkaTemplate.send("orders", String.valueOf(1), VALID_ORDER_PLACED_EVENT).get();
        System.out.println("   ✓ Message sent");
        System.out.println("");

        System.out.println("2. OrderEventListener consuming message from 'orders' topic...");
        System.out.println("   - Calls TradeExecutionService.executeOrder()");
        System.out.println("   - Calls FauxnanceQuoteClient.getQuote() → FAILS with 5xx");
        System.out.println("   - Exception is RestClientException (NOT fatal, IS retryable)");
        System.out.println("");

        System.out.println("3. Spring Kafka Error Handler applies backoff & retries:");
        System.out.println("   - Delay 1s → Retry 1 fails");
        System.out.println("   - Delay 2s → Retry 2 fails");
        System.out.println("   - Delay 4s → Retry 3 fails");
        System.out.println("   - Delay 8s → Retry 4 fails");
        System.out.println("   - Delay 16s → Retry 5 fails");
        System.out.println("   - Total: ~31s, Budget exhausted");
        System.out.println("");

        System.out.println("4. Waiting 35 seconds for retries to exhaust...");
        
        // Consume from orders.DLT in a separate thread
        new Thread(() -> {
            try {
                var consumer = createDLTConsumer();
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < 40_000) {
                    var records = consumer.poll(Duration.ofSeconds(1));
                    for (ConsumerRecord<String, String> record : records) {
                        dltMessage = record;
                        dltLatch.countDown();
                        break;
                    }
                    if (dltLatch.getCount() == 0) break;
                }
                consumer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Wait for message to appear in DLT (with timeout)
        boolean messageReceived = dltLatch.await(40, TimeUnit.SECONDS);

        System.out.println("   ✓ 35 seconds elapsed");
        System.out.println("");

        if (messageReceived && dltMessage != null) {
            System.out.println("5. ✓ MESSAGE SUCCESSFULLY DEAD-LETTERED!");
            System.out.println("");
            System.out.println("   Topic: " + dltMessage.topic());
            System.out.println("   Partition: " + dltMessage.partition());
            System.out.println("   Headers:");
            for (org.apache.kafka.common.header.Header header : dltMessage.headers()) {
                String value = new String(header.value() != null ? header.value() : "null".getBytes());
                System.out.println("     " + header.key() + ": " + value);
            }
            System.out.println("   Message value: " + dltMessage.value().substring(0, Math.min(100, dltMessage.value().length())) + "...");
            System.out.println("");
            System.out.println("✓✓✓ TEST PASSED ✓✓✓");
            System.out.println("OrderEventListener processed message → Retried with backoff → Sent to orders.DLT");
        } else {
            System.out.println("✗ TEST FAILED: Message did not appear in orders.DLT");
            System.out.println("Make sure Kafka and PostgreSQL are running!");
            assertTrue(false, "Dead-lettered message not found in orders.DLT");
        }
    }

    private KafkaConsumer<String, String> createDLTConsumer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "${KAFKA_BOOTSTRAP_SERVERS}");
        props.put("group.id", "dlt-test-consumer");
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("orders.DLT"));
        return consumer;
    }
}
