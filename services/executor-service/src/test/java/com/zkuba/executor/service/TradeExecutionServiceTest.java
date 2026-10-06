package com.zkuba.executor.service;

import com.zkuba.executor.client.FauxnanceQuoteClient;
import com.zkuba.executor.client.PricingUnavailableException;
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
import static org.mockito.ArgumentMatchers.*;
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

    private UUID orderId;
    private Account account;
    private Order order;
    private QuoteResponse.Data quoteData;

    @BeforeEach
    void setUp() {

        orderId = UUID.randomUUID();

        account = new Account();
        account.setId(100L);
        account.setAccountNumber("ACC-100");
        account.setCashBalance(new BigDecimal("10000.00"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setUserId(100L);
        account.setCurrency("USD");
        account.setVersion(1);

        order = new Order();
        order.setId(orderId);
        order.setAccountId(account.getId());
        order.setSymbol("AAPL");
        order.setSide(OrderSide.BUY);
        order.setOrderType(OrderType.MARKET);
        order.setQuantity(10);
        order.setLimitPrice(new BigDecimal("150.00"));
        order.setStatus(OrderStatus.NEW);
        order.setIdempotencyKey("IDEMP-100");
        order.setCreatedAt(OffsetDateTime.now());

        quoteData = new QuoteResponse.Data(
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
    }

    @Test
    void executeOrder_orderNotFound_returnsEmpty() {

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.empty());

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isEmpty());

        verify(orderRepository)
                .findById(orderId);

        verifyNoInteractions(
                quoteClient,
                fillRuleEngine,
                accountRepository,
                positionRepository
        );
    }

    @Test
    void executeOrder_orderAlreadyProcessed_returnsEmpty() {

        order.setStatus(OrderStatus.FILLED);

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isEmpty());

        verify(orderRepository)
                .findById(orderId);

        verifyNoInteractions(
                quoteClient,
                fillRuleEngine,
                accountRepository,
                positionRepository
        );
    }

    @Test
    void executeOrder_rejectedOrderStatus_returnsEmpty() {

        order.setStatus(OrderStatus.REJECTED);

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isEmpty());

        verify(orderRepository)
                .findById(orderId);
    }

    @Test
    void executeOrder_pricingUnavailable_rejectsOrder() {

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        when(quoteClient.getQuote("AAPL"))
                .thenThrow(
                        new PricingUnavailableException(
                                "Fauxnance unavailable",
                                null
                        )
                );

        when(
                orderRepository.executeOrder(
                        eq(orderId),
                        eq(OrderStatus.REJECTED),
                        isNull(),
                        any(OffsetDateTime.class),
                        any()
                )
        ).thenReturn(1);

        when(accountRepository.findById(account.getId()))
                .thenReturn(Optional.of(account));

        when(
                positionRepository.findByAccountIdAndSymbol(
                        account.getId(),
                        "AAPL"
                )
        ).thenReturn(Optional.empty());

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isPresent());

        TradeEventPayload event =
                result.get();

        assertEquals(
                OrderStatus.REJECTED.name(),
                event.status()
        );

        assertEquals(
                "PRICING_UNAVAILABLE",
                event.reason()
        );

        assertNull(event.executedPrice());

        assertEquals(
                BigDecimal.ZERO,
                event.cashDelta()
        );
    }

    @Test
    void executeOrder_priceNotMet_keepsOrderUnchanged() {

        mockQuote();

        when(
                fillRuleEngine.evaluate(
                        eq(order),
                        eq(quoteData)
                )
        ).thenReturn(
                FillDecision.rejected("PRICE_NOT_MET")
        );

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isEmpty());

        assertEquals(
                OrderStatus.NEW,
                order.getStatus()
        );

        verify(
                orderRepository,
                never()
        ).executeOrder(
                any(),
                any(),
                any(),
                any(),
                any()
        );

        verifyNoInteractions(
                accountRepository,
                positionRepository
        );
    }

    @Test
    void executeOrder_otherFillRejection_rejectsOrder() {

        mockQuote();

        when(
                fillRuleEngine.evaluate(
                        eq(order),
                        eq(quoteData)
                )
        ).thenReturn(
                FillDecision.rejected("INVALID_ORDER")
        );

        mockSuccessfulRejection();

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isPresent());

        TradeEventPayload event =
                result.get();

        assertEquals(
                OrderStatus.REJECTED.name(),
                event.status()
        );

        assertEquals(
                "INVALID_ORDER",
                event.reason()
        );

        assertNull(event.executedPrice());

        verify(orderRepository)
                .executeOrder(
                        eq(orderId),
                        eq(OrderStatus.REJECTED),
                        isNull(),
                        any(OffsetDateTime.class),
                        any()
                );
    }

    @Test
    void executeOrder_suspendedAccount_rejectsOrder() {

        mockQuote();

        when(
                fillRuleEngine.evaluate(
                        eq(order),
                        eq(quoteData)
                )
        ).thenReturn(
                FillDecision.filled(
                        new BigDecimal("150.00")
                )
        );

        account.setStatus(
                AccountStatus.SUSPENDED
        );

        when(accountRepository.findById(account.getId()))
                .thenReturn(Optional.of(account));

        when(
                orderRepository.executeOrder(
                        eq(orderId),
                        eq(OrderStatus.REJECTED),
                        isNull(),
                        any(OffsetDateTime.class),
                        any()
                )
        ).thenReturn(1);

        when(
                positionRepository.findByAccountIdAndSymbol(
                        account.getId(),
                        "AAPL"
                )
        ).thenReturn(Optional.empty());

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isPresent());

        assertEquals(
                "ACCOUNT_SUSPENDED",
                result.get().reason()
        );

        verify(orderRepository)
                .executeOrder(
                        eq(orderId),
                        eq(OrderStatus.REJECTED),
                        isNull(),
                        any(OffsetDateTime.class),
                        any()
                );
    }

    @Test
    void executeOrder_buyInsufficientFunds_rejectsOrder() {

        mockQuote();

        when(
                fillRuleEngine.evaluate(
                        eq(order),
                        eq(quoteData)
                )
        ).thenReturn(
                FillDecision.filled(
                        new BigDecimal("150.00")
                )
        );

        account.setCashBalance(
                new BigDecimal("100.00")
        );

        when(accountRepository.findById(account.getId()))
                .thenReturn(Optional.of(account));

        when(
                positionRepository.findByAccountIdAndSymbol(
                        account.getId(),
                        "AAPL"
                )
        ).thenReturn(Optional.empty());

        mockSuccessfulRejection();

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isPresent());

        assertEquals(
                "INSUFFICIENT_FUNDS",
                result.get().reason()
        );

        /*
         * executeOrder() queries the position once,
         * then rejectOrder() queries it again.
         */
        verify(
                positionRepository,
                times(2)
        ).findByAccountIdAndSymbol(
                account.getId(),
                "AAPL"
        );
    }

@Test
    void executeOrder_successfulBuy_updatesExistingPosition() {

        mockQuote();

        when(
                fillRuleEngine.evaluate(
                        eq(order),
                        eq(quoteData)
                )
        ).thenReturn(
                FillDecision.filled(
                        new BigDecimal("200.00")
                )
        );

        Position existingPosition =
                createPosition(
                        5,
                        new BigDecimal("100.00")
                );

        when(accountRepository.findById(account.getId()))
                .thenReturn(Optional.of(account));

        when(
                positionRepository.findByAccountIdAndSymbol(
                        account.getId(),
                        "AAPL"
                )
        ).thenReturn(
                Optional.of(existingPosition)
        );

        when(
                orderRepository.executeOrder(
                        eq(orderId),
                        eq(OrderStatus.FILLED),
                        eq(new BigDecimal("200.00")),
                        any(OffsetDateTime.class),
                        any()
                )
        ).thenReturn(1);

        when(
                positionRepository.save(
                        any(Position.class)
                )
        ).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isPresent());

        TradeEventPayload event =
                result.get();

        assertEquals(
                15,
                event.positionQuantityAfter()
        );

        assertEquals(
                new BigDecimal("166.6667"),
                event.averageCostAfter()
        );

        verify(positionRepository)
                .save(
                        argThat(position ->
                                position.getQuantity() == 15
                                        && position.getAverageCost()
                                        .compareTo(
                                                new BigDecimal("166.6667")
                                        ) == 0
                        )
                );
    }

    @Test
    void executeOrder_sellInsufficientHoldings_rejectsOrder() {

        order.setSide(OrderSide.SELL);
        order.setQuantity(10);

        mockQuote();

        when(
                fillRuleEngine.evaluate(
                        eq(order),
                        eq(quoteData)
                )
        ).thenReturn(
                FillDecision.filled(
                        new BigDecimal("150.00")
                )
        );

        Position position =
                createPosition(
                        5,
                        new BigDecimal("100.00")
                );

        when(accountRepository.findById(account.getId()))
                .thenReturn(Optional.of(account));

        when(
                positionRepository.findByAccountIdAndSymbol(
                        account.getId(),
                        "AAPL"
                )
        ).thenReturn(
                Optional.of(position)
        );

        mockSuccessfulRejection();

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isPresent());

        assertEquals(
                "INSUFFICIENT_HOLDINGS",
                result.get().reason()
        );
    }

    @Test
    void executeOrder_sellWithNoPosition_rejectsOrder() {

        order.setSide(OrderSide.SELL);
        order.setQuantity(10);

        mockQuote();

        when(
                fillRuleEngine.evaluate(
                        eq(order),
                        eq(quoteData)
                )
        ).thenReturn(
                FillDecision.filled(
                        new BigDecimal("150.00")
                )
        );

        when(accountRepository.findById(account.getId()))
                .thenReturn(Optional.of(account));

        when(
                positionRepository.findByAccountIdAndSymbol(
                        account.getId(),
                        "AAPL"
                )
        ).thenReturn(Optional.empty());

        mockSuccessfulRejection();

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isPresent());

        assertEquals(
                "INSUFFICIENT_HOLDINGS",
                result.get().reason()
        );

        /*
         * Current service checks the position in executeOrder()
         * and then again inside rejectOrder().
         */
        verify(
                positionRepository,
                times(2)
        ).findByAccountIdAndSymbol(
                account.getId(),
                "AAPL"
        );
    }

    @Test
    void executeOrder_successfulSell_partialPositionRemains() {

        order.setSide(OrderSide.SELL);
        order.setQuantity(4);

        mockQuote();

        when(
                fillRuleEngine.evaluate(
                        eq(order),
                        eq(quoteData)
                )
        ).thenReturn(
                FillDecision.filled(
                        new BigDecimal("150.00")
                )
        );

        Position position =
                createPosition(
                        10,
                        new BigDecimal("120.00")
                );

        when(accountRepository.findById(account.getId()))
                .thenReturn(Optional.of(account));

        when(
                positionRepository.findByAccountIdAndSymbol(
                        account.getId(),
                        "AAPL"
                )
        ).thenReturn(
                Optional.of(position)
        );

        when(
                orderRepository.executeOrder(
                        eq(orderId),
                        eq(OrderStatus.FILLED),
                        eq(new BigDecimal("150.00")),
                        any(OffsetDateTime.class),
                        any()
                )
        ).thenReturn(1);

        when(
                positionRepository.save(
                        any(Position.class)
                )
        ).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isPresent());

        TradeEventPayload event =
                result.get();

        assertEquals(
                OrderStatus.FILLED.name(),
                event.status()
        );

        assertEquals(
                6,
                event.positionQuantityAfter()
        );

        assertEquals(
                new BigDecimal("120.00"),
                event.averageCostAfter()
        );

        assertEquals(
                new BigDecimal("600.00"),
                event.cashDelta()
        );

        verify(positionRepository)
                .save(position);

        verify(
                positionRepository,
                never()
        ).delete(any(Position.class));
    }

    @Test
    void executeOrder_successfulSell_closesPosition() {

        order.setSide(OrderSide.SELL);
        order.setQuantity(10);

        mockQuote();

        when(
                fillRuleEngine.evaluate(
                        eq(order),
                        eq(quoteData)
                )
        ).thenReturn(
                FillDecision.filled(
                        new BigDecimal("150.00")
                )
        );

        Position position =
                createPosition(
                        10,
                        new BigDecimal("120.00")
                );

        when(accountRepository.findById(account.getId()))
                .thenReturn(Optional.of(account));

        when(
                positionRepository.findByAccountIdAndSymbol(
                        account.getId(),
                        "AAPL"
                )
        ).thenReturn(
                Optional.of(position)
        );

        when(
                orderRepository.executeOrder(
                        eq(orderId),
                        eq(OrderStatus.FILLED),
                        eq(new BigDecimal("150.00")),
                        any(OffsetDateTime.class),
                        any()
                )
        ).thenReturn(1);

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isPresent());

        TradeEventPayload event =
                result.get();

        assertEquals(
                0,
                event.positionQuantityAfter()
        );

        assertEquals(
                BigDecimal.ZERO,
                event.averageCostAfter()
        );

        assertEquals(
                new BigDecimal("1500.00"),
                event.cashDelta()
        );

        verify(positionRepository)
                .delete(position);

        verify(
                positionRepository,
                never()
        ).save(any(Position.class));
    }

    @Test
    void executeOrder_optimisticLockOnFill_throwsException() {

        mockQuote();

        when(
                fillRuleEngine.evaluate(
                        eq(order),
                        eq(quoteData)
                )
        ).thenReturn(
                FillDecision.filled(
                        new BigDecimal("150.00")
                )
        );

        when(accountRepository.findById(account.getId()))
                .thenReturn(Optional.of(account));

        when(
                positionRepository.findByAccountIdAndSymbol(
                        account.getId(),
                        "AAPL"
                )
        ).thenReturn(Optional.empty());

        when(
                orderRepository.executeOrder(
                        eq(orderId),
                        eq(OrderStatus.FILLED),
                        eq(new BigDecimal("150.00")),
                        any(OffsetDateTime.class),
                        any()
                )
        ).thenReturn(0);

        assertThrows(
                OptimisticLockException.class,
                () -> service.executeOrder(orderId)
        );

        verify(
                positionRepository,
                never()
        ).save(any(Position.class));
    }

    @Test
    void executeOrder_optimisticLockOnRejection_throwsException() {

        mockQuote();

        when(
                fillRuleEngine.evaluate(
                        eq(order),
                        eq(quoteData)
                )
        ).thenReturn(
                FillDecision.rejected(
                        "INVALID_ORDER"
                )
        );

        when(
                orderRepository.executeOrder(
                        eq(orderId),
                        eq(OrderStatus.REJECTED),
                        isNull(),
                        any(OffsetDateTime.class),
                        any()
                )
        ).thenReturn(0);

        assertThrows(
                OptimisticLockException.class,
                () -> service.executeOrder(orderId)
        );

        verify(
                accountRepository,
                never()
        ).findById(anyLong());
    }

    
    @Test
    void rejectedEvent_containsCorrectPayload() {

        mockQuote();

        when(
                fillRuleEngine.evaluate(
                        eq(order),
                        eq(quoteData)
                )
        ).thenReturn(
                FillDecision.rejected(
                        "INVALID_ORDER"
                )
        );

        mockSuccessfulRejection();

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isPresent());

        TradeEventPayload event =
                result.get();

        assertEquals(
                orderId,
                event.orderId()
        );

        assertEquals(
                100L,
                event.accountId()
        );

        assertEquals(
                "AAPL",
                event.symbol()
        );

        assertEquals(
                "BUY",
                event.side()
        );

        assertEquals(
                10,
                event.quantity()
        );

        assertEquals(
                new BigDecimal("150.00"),
                event.price()
        );

        assertNull(
                event.executedPrice()
        );

        assertEquals(
                "REJECTED",
                event.status()
        );

        assertEquals(
                "INVALID_ORDER",
                event.reason()
        );

        assertEquals(
                BigDecimal.ZERO,
                event.cashDelta()
        );

        assertEquals(
                0,
                event.positionQuantityAfter()
        );

        assertEquals(
                BigDecimal.ZERO,
                event.averageCostAfter()
        );

        assertNotNull(
                event.executedOn()
        );
    }

    @Test
    void rejectedEvent_withExistingPosition_containsPositionData() {

        mockQuote();

        when(
                fillRuleEngine.evaluate(
                        eq(order),
                        eq(quoteData)
                )
        ).thenReturn(
                FillDecision.rejected(
                        "INVALID_ORDER"
                )
        );

        Position position =
                createPosition(
                        25,
                        new BigDecimal("125.50")
                );

        when(
                orderRepository.executeOrder(
                        eq(orderId),
                        eq(OrderStatus.REJECTED),
                        isNull(),
                        any(OffsetDateTime.class),
                        any()
                )
        ).thenReturn(1);

        when(accountRepository.findById(account.getId()))
                .thenReturn(Optional.of(account));

        when(
                positionRepository.findByAccountIdAndSymbol(
                        account.getId(),
                        "AAPL"
                )
        ).thenReturn(
                Optional.of(position)
        );

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isPresent());

        TradeEventPayload event =
                result.get();

        assertEquals(
                25,
                event.positionQuantityAfter()
        );

        assertEquals(
                new BigDecimal("125.50"),
                event.averageCostAfter()
        );

        assertEquals(
                BigDecimal.ZERO,
                event.cashDelta()
        );
    }

    @Test
    void filledBuyEvent_withExistingPosition_containsUpdatedPosition() {

        mockQuote();

        when(
                fillRuleEngine.evaluate(
                        eq(order),
                        eq(quoteData)
                )
        ).thenReturn(
                FillDecision.filled(
                        new BigDecimal("150.00")
                )
        );

        Position position =
                createPosition(
                        20,
                        new BigDecimal("100.00")
                );

        when(accountRepository.findById(account.getId()))
                .thenReturn(Optional.of(account));

        when(
                positionRepository.findByAccountIdAndSymbol(
                        account.getId(),
                        "AAPL"
                )
        ).thenReturn(
                Optional.of(position)
        );

        when(
                orderRepository.executeOrder(
                        eq(orderId),
                        eq(OrderStatus.FILLED),
                        eq(new BigDecimal("150.00")),
                        any(OffsetDateTime.class),
                        any()
                )
        ).thenReturn(1);

        when(
                positionRepository.save(
                        any(Position.class)
                )
        ).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        Optional<TradeEventPayload> result =
                service.executeOrder(orderId);

        assertTrue(result.isPresent());

        TradeEventPayload event =
                result.get();

        assertEquals(
                30,
                event.positionQuantityAfter()
        );

        assertEquals(
                new BigDecimal("116.6667"),
                event.averageCostAfter()
        );
    }

    private void mockQuote() {

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        QuoteResponse quoteResponse =
                new QuoteResponse(
                        quoteData,
                        null
                );

        when(
                quoteClient.getQuote("AAPL")
        ).thenReturn(quoteResponse);
    }

    private void mockSuccessfulRejection() {

        when(
                orderRepository.findById(orderId)
        ).thenReturn(Optional.of(order));

        when(
                orderRepository.executeOrder(
                        eq(orderId),
                        eq(OrderStatus.REJECTED),
                        isNull(),
                        any(OffsetDateTime.class),
                        any()
                )
        ).thenReturn(1);

        when(
                accountRepository.findById(
                        account.getId()
                )
        ).thenReturn(Optional.of(account));

        when(
                positionRepository.findByAccountIdAndSymbol(
                        account.getId(),
                        "AAPL"
                )
        ).thenReturn(Optional.empty());
    }

    private Position createPosition(
            int quantity,
            BigDecimal averageCost) {

        Position position =
                new Position();

        position.setAccountId(
                account.getId()
        );

        position.setSymbol(
                "AAPL"
        );

        position.setQuantity(
                quantity
        );

        position.setAverageCost(
                averageCost
        );

        return position;
    }
}