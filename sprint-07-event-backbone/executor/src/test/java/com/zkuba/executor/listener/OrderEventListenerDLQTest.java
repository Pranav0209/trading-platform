package com.zkuba.executor.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zkuba.executor.client.PricingUnavailableException;
import com.zkuba.executor.service.TradeExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the dead-letter & retry classification in OrderEventListener.
 *
 * These tests verify which exception type propagates out of onOrderPlaced() so
 * that the KafkaConfig DefaultErrorHandler routes messages correctly:
 *
 * JsonProcessingException / IllegalArgumentException / NoSuchElementException
 * → fatal → dead-lettered to orders.DLT on the very first attempt
 *
 * RuntimeException (DB down, lock exhausted, broker blip)
 * → retryable → exponential back-off, then DLT
 *
 * PricingUnavailableException
 * → retryable forever → fixed back-off, never DLT
 *
 * The actual Spring Kafka error handler routing is tested by the framework;
 * our responsibility is to surface the correct exception type.
 */
@ExtendWith(MockitoExtension.class)
class OrderEventListenerDLQTest {

  @Mock
  private TradeExecutionService tradeExecutionService;

  @Mock
  private KafkaTemplate<String, String> kafkaTemplate;

  @Mock
  private Acknowledgment ack;

  /** Real ObjectMapper — we need actual JSON parsing for these tests. */
  @Spy
  private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @InjectMocks
  private OrderEventListener listener;

  private static final String VALID_ORDER_JSON = """
      {
        "eventId": "test-id",
        "eventType": "ORDER_PLACED",
        "eventTime": "2026-09-16T10:00:00Z",
        "source": "api",
        "schemaVersion": 1,
        "payload": {
          "orderId": "11111111-1111-1111-1111-111111111111",
          "accountId": 1,
          "symbol": "AAPL",
          "side": "BUY",
          "quantity": 10,
          "idempotencyKey": "idemp-1",
          "createdOn": "2026-09-16T10:00:00Z"
        }
      }
      """;

  // -------------------------------------------------------------------------
  // Path 1: Malformed message → JsonProcessingException (fatal, DLT attempt 1)
  // -------------------------------------------------------------------------

  @Test
  void malformedMessageThrowsJsonProcessingExceptionWhichIsFatal() {
    assertThrows(JsonProcessingException.class,
        () -> listener.onOrderPlaced("not-valid-json {{{", ack));

    // Fatal: no ack, no interaction with execution service
    verify(ack, never()).acknowledge();
    verify(tradeExecutionService, never()).executeOrder(any());
  }

  // -------------------------------------------------------------------------
  // Path 2: Unexpected event type → IllegalArgumentException (fatal, DLT attempt
  // 1)
  // -------------------------------------------------------------------------

  @Test
  void unexpectedEventTypeThrowsIllegalArgumentExceptionWhichIsFatal() {
    String wrongType = """
        {
          "eventId": "test-id",
          "eventType": "ORDER_CANCELLED",
          "eventTime": "2026-09-16T10:00:00Z",
          "source": "api",
          "schemaVersion": 1,
          "payload": {
            "orderId": "11111111-1111-1111-1111-111111111111",
            "accountId": 1
          }
        }
        """;

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> listener.onOrderPlaced(wrongType, ack));

    assertTrue(ex.getMessage().contains("Unexpected event type"));
    verify(ack, never()).acknowledge();
  }

  // -------------------------------------------------------------------------
  // Path 3: Missing orderId → IllegalArgumentException (fatal, DLT attempt 1)
  // -------------------------------------------------------------------------

  @Test
  void missingOrderIdThrowsIllegalArgumentExceptionWhichIsFatal() {
    String missingId = """
        {
          "eventId": "test-id",
          "eventType": "ORDER_PLACED",
          "eventTime": "2026-09-16T10:00:00Z",
          "source": "api",
          "schemaVersion": 1,
          "payload": null
        }
        """;

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> listener.onOrderPlaced(missingId, ack));

    assertTrue(ex.getMessage().contains("Missing order identifier"));
    verify(ack, never()).acknowledge();
  }

  // -------------------------------------------------------------------------
  // Path 4: Order not found in Postgres → NoSuchElementException (fatal, DLT
  // attempt 1)
  // -------------------------------------------------------------------------

  @Test
  void orderNotInPostgresThrowsNoSuchElementExceptionWhichIsFatal() throws Exception {
    when(tradeExecutionService.executeOrder(any()))
        .thenThrow(new NoSuchElementException("Order not found"));

    assertThrows(NoSuchElementException.class,
        () -> listener.onOrderPlaced(VALID_ORDER_JSON, ack));

    verify(ack, never()).acknowledge();
  }

  // -------------------------------------------------------------------------
  // Path 5: Transient DB failure → RuntimeException (retryable)
  // Verify it succeeds on subsequent attempt (no exception on retry)
  // -------------------------------------------------------------------------

  @Test
  void transientRuntimeExceptionIsRetryableAndSucceedsLater() throws Exception {
    // First call: transient failure
    when(tradeExecutionService.executeOrder(any()))
        .thenThrow(new RuntimeException("Transient DB error"))
        .thenReturn(Optional.empty()); // second call: success

    // First invocation throws (will be retried by Spring Kafka)
    assertThrows(RuntimeException.class,
        () -> listener.onOrderPlaced(VALID_ORDER_JSON, ack));

    // Second invocation (simulating retry) completes without exception
    assertDoesNotThrow(() -> listener.onOrderPlaced(VALID_ORDER_JSON, ack));

    verify(ack, times(1)).acknowledge();
  }

  // -------------------------------------------------------------------------
  // Path 6: Fauxnance outage → PricingUnavailableException (retryable forever)
  // Must NOT be dead-lettered (order would stay NEW forever)
  // -------------------------------------------------------------------------

  @Test
  void pricingUnavailableExceptionIsRetryableForeverAndNeverDeadLettered() throws Exception {
    when(tradeExecutionService.executeOrder(any()))
        .thenThrow(new PricingUnavailableException("Fauxnance down", null));

    // Propagates so Spring Kafka can apply infinite FixedBackOff
    assertThrows(PricingUnavailableException.class,
        () -> listener.onOrderPlaced(VALID_ORDER_JSON, ack));

    // Must never be acknowledged (would advance offset and lose the order)
    verify(ack, never()).acknowledge();
  }

  // -------------------------------------------------------------------------
  // Path 7: Poison message does not block the partition
  // A fatal exception clears the offset immediately (attempt 1 → DLT)
  // so subsequent messages on the same partition are unaffected.
  // -------------------------------------------------------------------------

  @Test
  void poisonMessageIsDeadLetteredAndDoesNotBlockPartition() throws Exception {
    // 1. Poison: malformed JSON → fatal, dead-lettered on attempt 1
    assertThrows(JsonProcessingException.class,
        () -> listener.onOrderPlaced("{{invalid}}", ack));

    // 2. Next valid message on the same partition executes normally
    when(tradeExecutionService.executeOrder(any())).thenReturn(Optional.empty());

    assertDoesNotThrow(() -> listener.onOrderPlaced(VALID_ORDER_JSON, ack));
    verify(ack, times(1)).acknowledge();
  }
}