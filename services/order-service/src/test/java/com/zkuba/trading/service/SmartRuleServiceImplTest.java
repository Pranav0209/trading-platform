package com.zkuba.trading.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Instrument;
import com.zkuba.trading.domain.entity.SmartRule;
import com.zkuba.trading.domain.entity.SmartRule.ComparisonOperator;
import com.zkuba.trading.domain.entity.SmartRule.MetricType;
import com.zkuba.trading.domain.entity.SmartRule.QuantityMode;
import com.zkuba.trading.domain.entity.SmartRule.RuleAction;
import com.zkuba.trading.dto.request.CreateSmartRuleRequest;
import com.zkuba.trading.dto.response.SmartRuleResponse;
import com.zkuba.trading.exception.AccountNotFoundException;
import com.zkuba.trading.exception.InstrumentNotFoundException;
import com.zkuba.trading.exception.SmartRuleNotFoundException;
import com.zkuba.trading.exception.SmartRuleValidationException;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.InstrumentRepository;
import com.zkuba.trading.repository.SmartRuleRepository;
import com.zkuba.trading.service.impl.SmartRuleServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SmartRuleServiceImplTest {

    @Mock
    private SmartRuleRepository smartRuleRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private InstrumentRepository instrumentRepository;

    @InjectMocks
    private SmartRuleServiceImpl smartRuleService;

    private Account account;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(1L);

        instrument = new Instrument();
        instrument.setSymbol("AAPL");
        instrument.setDisplayName("Apple Inc.");
        instrument.setTradable(true);
    }

    @Test
    void getRules_Success() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        
        SmartRule rule = new SmartRule();
        rule.setId(10L);
        rule.setAccount(account);
        rule.setSymbol("AAPL");
        rule.setAction(RuleAction.BUY);
        rule.setQuantityMode(QuantityMode.FIXED);
        rule.setQuantityValue(10);
        rule.setMetricType(MetricType.PRICE);
        rule.setComparisonOperator(ComparisonOperator.LESS_THAN);
        
        when(smartRuleRepository.findByAccountIdAndParentRuleIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(rule));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));

        List<SmartRuleResponse> rules = smartRuleService.getRules(1L);
        assertEquals(1, rules.size());
        assertEquals("AAPL", rules.get(0).symbol());
    }

    @Test
    void getRules_AccountNotFound() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(AccountNotFoundException.class, () -> smartRuleService.getRules(1L));
    }

    @Test
    void createRule_Success() {
        CreateSmartRuleRequest req = new CreateSmartRuleRequest(
                "AAPL", RuleAction.BUY, QuantityMode.FIXED, 10,
                MetricType.PRICE, ComparisonOperator.LESS_THAN, new BigDecimal("150"),
                null, null, null, true, null
        );

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));
        when(smartRuleRepository.save(any())).thenAnswer(i -> {
            SmartRule r = i.getArgument(0);
            r.setId(99L);
            return r;
        });

        SmartRuleResponse res = smartRuleService.createRule(1L, req);
        assertNotNull(res);
        assertEquals(99L, res.id());
        assertEquals("AAPL", res.symbol());
    }

    @Test
    void createRule_InstrumentNotTradable() {
        instrument.setTradable(false);
        CreateSmartRuleRequest req = new CreateSmartRuleRequest(
                "AAPL", RuleAction.BUY, QuantityMode.FIXED, 10,
                MetricType.PRICE, ComparisonOperator.LESS_THAN, new BigDecimal("150"),
                null, null, null, true, null
        );

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));

        assertThrows(InstrumentNotFoundException.class, () -> smartRuleService.createRule(1L, req));
    }

    @Test
    void createRule_Validation_BuyMustBeFixed() {
        CreateSmartRuleRequest req = new CreateSmartRuleRequest(
                "AAPL", RuleAction.BUY, QuantityMode.ALL, null,
                MetricType.PRICE, ComparisonOperator.LESS_THAN, new BigDecimal("150"),
                null, null, null, true, null
        );

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));

        assertThrows(SmartRuleValidationException.class, () -> smartRuleService.createRule(1L, req));
    }

    @Test
    void createRule_Validation_FixedMustHaveQuantity() {
        CreateSmartRuleRequest req = new CreateSmartRuleRequest(
                "AAPL", RuleAction.SELL, QuantityMode.FIXED, null,
                MetricType.PRICE, ComparisonOperator.LESS_THAN, new BigDecimal("150"),
                null, null, null, true, null
        );

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));

        assertThrows(SmartRuleValidationException.class, () -> smartRuleService.createRule(1L, req));
    }

    @Test
    void createRule_Validation_HalfCannotHaveExplicitQuantity() {
        CreateSmartRuleRequest req = new CreateSmartRuleRequest(
                "AAPL", RuleAction.SELL, QuantityMode.HALF, 10,
                MetricType.PRICE, ComparisonOperator.LESS_THAN, new BigDecimal("150"),
                null, null, null, true, null
        );

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));

        assertThrows(SmartRuleValidationException.class, () -> smartRuleService.createRule(1L, req));
    }

    @Test
    void createRule_Validation_IndicatorMustHaveLookback() {
        CreateSmartRuleRequest req = new CreateSmartRuleRequest(
                "AAPL", RuleAction.BUY, QuantityMode.FIXED, 10,
                MetricType.MOVING_AVERAGE, ComparisonOperator.LESS_THAN, new BigDecimal("150"),
                1, null, null, true, null
        ); // lookback < 2

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));

        assertThrows(SmartRuleValidationException.class, () -> smartRuleService.createRule(1L, req));
    }
    
    @Test
    void createRule_Validation_MACrossover() {
        CreateSmartRuleRequest req = new CreateSmartRuleRequest(
                "AAPL", RuleAction.BUY, QuantityMode.FIXED, 10,
                MetricType.MOVING_AVERAGE_CROSSOVER, ComparisonOperator.CROSS_ABOVE, new BigDecimal("150"),
                10, 1, null, true, null
        ); // secondary lookback < 2

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));

        assertThrows(SmartRuleValidationException.class, () -> smartRuleService.createRule(1L, req));
    }

    @Test
    void createRule_Validation_BollingerBandOperator() {
        CreateSmartRuleRequest req = new CreateSmartRuleRequest(
                "AAPL", RuleAction.BUY, QuantityMode.FIXED, 10,
                MetricType.BOLLINGER_BAND, ComparisonOperator.LESS_THAN, null,
                10, null, null, true, null
        ); // bollinger must be TOUCH_UPPER or TOUCH_LOWER

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));

        assertThrows(SmartRuleValidationException.class, () -> smartRuleService.createRule(1L, req));
    }
    
    @Test
    void createRule_Validation_StopLossNoParent() {
        CreateSmartRuleRequest req = new CreateSmartRuleRequest(
                "AAPL", RuleAction.SELL, QuantityMode.FIXED, 10,
                MetricType.STOP_LOSS, ComparisonOperator.LESS_THAN, new BigDecimal("150"),
                null, null, null, true, null
        ); // stop loss requires parent

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));

        assertThrows(SmartRuleValidationException.class, () -> smartRuleService.createRule(1L, req));
    }

    @Test
    void deleteRule_Success() {
        SmartRule rule = new SmartRule();
        when(smartRuleRepository.findByIdAndAccountId(10L, 1L)).thenReturn(Optional.of(rule));
        smartRuleService.deleteRule(1L, 10L);
        verify(smartRuleRepository).delete(rule);
    }
    
    @Test
    void deleteRule_NotFound() {
        when(smartRuleRepository.findByIdAndAccountId(10L, 1L)).thenReturn(Optional.empty());
        assertThrows(SmartRuleNotFoundException.class, () -> smartRuleService.deleteRule(1L, 10L));
    }

    @Test
    void createRule_WithChildren() {
        CreateSmartRuleRequest childReq = new CreateSmartRuleRequest(
                "AAPL", RuleAction.SELL, QuantityMode.ALL, null,
                MetricType.PRICE, ComparisonOperator.GREATER_THAN, new BigDecimal("200"),
                null, null, null, true, null
        );
        CreateSmartRuleRequest req = new CreateSmartRuleRequest(
                "AAPL", RuleAction.BUY, QuantityMode.FIXED, 10,
                MetricType.PRICE, ComparisonOperator.LESS_THAN, new BigDecimal("150"),
                null, null, null, true, List.of(childReq)
        );

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));
        when(smartRuleRepository.save(any())).thenAnswer(i -> {
            SmartRule r = i.getArgument(0);
            r.setId(99L);
            return r;
        });

        SmartRuleResponse res = smartRuleService.createRule(1L, req);
        assertNotNull(res);
        assertEquals(1, res.children().size());
        assertEquals(RuleAction.SELL.name(), res.children().get(0).action());
    }
}
