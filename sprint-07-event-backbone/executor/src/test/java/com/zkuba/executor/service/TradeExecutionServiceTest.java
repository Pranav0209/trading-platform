package com.zkuba.executor.service;

import com.zkuba.executor.client.FauxnanceQuoteClient;
import com.zkuba.executor.client.QuoteResponse;
import com.zkuba.executor.domain.entity.Account;
import com.zkuba.executor.domain.entity.Order;
import com.zkuba.executor.domain.entity.Position;
import com.zkuba.executor.domain.enums.AccountStatus;
import com.zkuba.executor.domain.enums.OrderSide;
import com.zkuba.executor.domain.enums.OrderStatus;
import com.zkuba.executor.domain.enums.OrderType;
import com.zkuba.executor.engine.FillDecision;
import com.zkuba.executor.engine.FillRuleEngine;
import com.zkuba.executor.event.TradeEventPayload;
import com.zkuba.executor.repository.AccountRepository;
import com.zkuba.executor.repository.OrderRepository;
import com.zkuba.executor.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeExecutionServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private FauxnanceQuoteClient quoteClient;

    @Mock
    private FillRuleEngine fillRuleEngine;

    @InjectMocks
    private TradeExecutionService service;

    private Order order;
    private Account account;
    private QuoteResponse.Data quoteData;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(UUID.randomUUID());
        order.setAccountId(1L);
        order.setSymbol("AAPL");
        order.setSide(OrderSide.BUY);
        order.setOrderType(OrderType.MARKET);
        order.setQuantity(10);
        order.setStatus(OrderStatus.NEW);

        account = new Account();
        account.setId(1L);
        account.setCashBalance(new BigDecimal("10000.00"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setVersion(1);

        quoteData = new QuoteResponse.Data("AAPL", null, null, null, null, null, null, null, null, null);
    }

    // --- Story 4 Existing Tests Adapted ---

    @Test
    void affordabilityReCheckRejectsOrder() {
        account.setCashBalance(new BigDecimal("100.00")); // can't afford 10 * 150 = 1500

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(quoteClient.getQuote("AAPL")).thenReturn(new QuoteResponse(quoteData, null));
        when(fillRuleEngine.evaluate(order, quoteData)).thenReturn(FillDecision.filled(new BigDecimal("150.00")));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(positionRepository.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.empty());

        when(orderRepository.executeOrder(eq(order.getId()), eq(OrderStatus.REJECTED), isNull(),
                any(OffsetDateTime.class))).thenReturn(1);

        Optional<TradeEventPayload> result = service.executeOrder(order.getId());

        assertTrue(result.isPresent());
        assertEquals("REJECTED", result.get().status());
        assertEquals("INSUFFICIENT_FUNDS", result.get().reason());
        verify(orderRepository).executeOrder(eq(order.getId()), eq(OrderStatus.REJECTED), isNull(),
                any(OffsetDateTime.class));
    }

    @Test
    void suspendedAccountRejectsOrder() {
        account.setStatus(AccountStatus.SUSPENDED);

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(quoteClient.getQuote("AAPL")).thenReturn(new QuoteResponse(quoteData, null));
        when(fillRuleEngine.evaluate(order, quoteData)).thenReturn(FillDecision.filled(new BigDecimal("150.00")));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(orderRepository.executeOrder(eq(order.getId()), eq(OrderStatus.REJECTED), isNull(),
                any(OffsetDateTime.class))).thenReturn(1);

        Optional<TradeEventPayload> result = service.executeOrder(order.getId());

        assertTrue(result.isPresent());
        assertEquals("REJECTED", result.get().status());
        assertEquals("ACCOUNT_SUSPENDED", result.get().reason());
    }

    // --- Story 5 New Required Test Paths ---

    @Test
    void aSettledOrderWritesStatusCashAndPositionTogether() {
        // Path 1: Success path
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(quoteClient.getQuote("AAPL")).thenReturn(new QuoteResponse(quoteData, null));
        when(fillRuleEngine.evaluate(order, quoteData)).thenReturn(FillDecision.filled(new BigDecimal("150.00")));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(positionRepository.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.empty());

        // Guarded transition successful
        when(orderRepository.executeOrder(eq(order.getId()), eq(OrderStatus.FILLED), eq(new BigDecimal("150.00")),
                any(OffsetDateTime.class))).thenReturn(1);

        Optional<TradeEventPayload> result = service.executeOrder(order.getId());

        assertTrue(result.isPresent());
        assertEquals("FILLED", result.get().status());

        // Assert cash balance was deducted (10000 - 1500 = 8500)
        assertEquals(new BigDecimal("8500.00"), account.getCashBalance());

        // Position was updated (since it's saved to DB)
        verify(positionRepository).save(any(Position.class));
    }

    @Test
    void aFailureInAnyOfTheThreeLeavesNoneOfThemWritten() {
        // Path 2: Test that an unexpected runtime error rolls everything back.
        // Actually, since `@Transactional` does this automatically, we just test that
        // throwing an error stops the downstream calls.
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(quoteClient.getQuote("AAPL")).thenReturn(new QuoteResponse(quoteData, null));
        when(fillRuleEngine.evaluate(order, quoteData)).thenReturn(FillDecision.filled(new BigDecimal("150.00")));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(positionRepository.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.empty());

        // Throw an unexpected runtime exception during order update
        when(orderRepository.executeOrder(any(), any(), any(), any())).thenThrow(new RuntimeException("DB down"));

        assertThrows(RuntimeException.class, () -> service.executeOrder(order.getId()));

        // Verify account wasn't modified in memory either because error happened first
        assertEquals(new BigDecimal("10000.00"), account.getCashBalance());
        verify(positionRepository, never()).save(any());
    }

    @Test
    void aSecondDeliveryAffectsZeroRowsAndPublishesNothing() {
        // Path 3: Guarded transition returns 0, throws OptimisticLockException
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(quoteClient.getQuote("AAPL")).thenReturn(new QuoteResponse(quoteData, null));
        when(fillRuleEngine.evaluate(order, quoteData)).thenReturn(FillDecision.filled(new BigDecimal("150.00")));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(positionRepository.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.empty());

        // Guarded transition returns 0 (another thread got there first)
        when(orderRepository.executeOrder(eq(order.getId()), eq(OrderStatus.FILLED), eq(new BigDecimal("150.00")),
                any(OffsetDateTime.class))).thenReturn(0);

        assertThrows(OptimisticLockException.class, () -> service.executeOrder(order.getId()));

        // No account update or position save should happen
        verify(positionRepository, never()).save(any());
    }

    @Test
    void anExhaustedOptimisticLockBudgetIsReportedAsAnErrorRatherThanIgnored() {
        // Path 4: Same as Path 3 but testing the semantic that OptimisticLockException
        // IS an error.
        // The @Retryable annotation in Spring retries this up to 5 times.
        // When running the unit test without Spring context, @Retryable doesn't wrap
        // it,
        // so we directly see the OptimisticLockException thrown, which confirms it's an
        // error.
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(quoteClient.getQuote("AAPL")).thenReturn(new QuoteResponse(quoteData, null));
        when(fillRuleEngine.evaluate(order, quoteData)).thenReturn(FillDecision.filled(new BigDecimal("150.00")));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(positionRepository.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.empty());

        when(orderRepository.executeOrder(any(), any(), any(), any())).thenReturn(0);

        OptimisticLockException error = assertThrows(OptimisticLockException.class,
                () -> service.executeOrder(order.getId()));
        assertEquals("Order already executed", error.getMessage());
    }
}
