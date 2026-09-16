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
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void affordabilityReCheckRejectsOrder() {
        // Account can no longer afford it
        account.setCashBalance(new BigDecimal("100.00"));

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        QuoteResponse quoteResponse = new QuoteResponse(quoteData, null);
        when(quoteClient.getQuote("AAPL")).thenReturn(quoteResponse);
        
        // FillRuleEngine passes it
        when(fillRuleEngine.evaluate(order, quoteData))
            .thenReturn(FillDecision.filled(new BigDecimal("150.00")));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(positionRepository.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.empty());
        when(orderRepository.executeOrder(order.getId(), OrderStatus.REJECTED, null)).thenReturn(1);

        Optional<TradeEventPayload> result = service.executeOrder(order.getId());

        assertTrue(result.isPresent());
        assertEquals("REJECTED", result.get().status());
        assertEquals("INSUFFICIENT_FUNDS", result.get().reason());
        verify(orderRepository).executeOrder(order.getId(), OrderStatus.REJECTED, null);
    }

    @Test
    void suspendedAccountRejectsOrder() {
        account.setStatus(AccountStatus.SUSPENDED);

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        QuoteResponse quoteResponse = new QuoteResponse(quoteData, null);
        when(quoteClient.getQuote("AAPL")).thenReturn(quoteResponse);
        
        when(fillRuleEngine.evaluate(order, quoteData))
            .thenReturn(FillDecision.filled(new BigDecimal("150.00")));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(orderRepository.executeOrder(order.getId(), OrderStatus.REJECTED, null)).thenReturn(1);

        Optional<TradeEventPayload> result = service.executeOrder(order.getId());

        assertTrue(result.isPresent());
        assertEquals("REJECTED", result.get().status());
        assertEquals("ACCOUNT_SUSPENDED", result.get().reason());
        verify(orderRepository).executeOrder(order.getId(), OrderStatus.REJECTED, null);
    }

    @Test
    void pricingUnavailableRejectsOrder() {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(quoteClient.getQuote("AAPL")).thenThrow(new PricingUnavailableException("Error", null));
        
        when(orderRepository.executeOrder(order.getId(), OrderStatus.REJECTED, null)).thenReturn(1);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(positionRepository.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.empty());

        Optional<TradeEventPayload> result = service.executeOrder(order.getId());

        assertTrue(result.isPresent());
        assertEquals("REJECTED", result.get().status());
        assertEquals("PRICING_UNAVAILABLE", result.get().reason());
        verify(orderRepository).executeOrder(order.getId(), OrderStatus.REJECTED, null);
    }
}
