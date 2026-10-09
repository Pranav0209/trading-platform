package com.zkuba.trading.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.zkuba.trading.client.CandleResponse;
import com.zkuba.trading.client.FauxnanceCandleClient;
import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.enums.AccountStatus;
import com.zkuba.trading.domain.entity.Position;
import com.zkuba.trading.domain.entity.Watchlist;
import com.zkuba.trading.domain.enums.SignalSource;
import com.zkuba.trading.domain.enums.SignalType;
import com.zkuba.trading.domain.enums.AccountStatus;
import com.zkuba.trading.dto.response.AdviceResponse;
import com.zkuba.trading.dto.response.SignalResponse;
import com.zkuba.trading.exception.AccountNotActiveException;
import com.zkuba.trading.exception.AccountNotFoundException;
import com.zkuba.trading.mapper.WatchlistMapper;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.PositionRepository;
import com.zkuba.trading.repository.WatchlistRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AdviceServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private WatchlistRepository watchlistRepository;
    @Mock
    private WatchlistMapper watchlistMapper;
    @Mock
    private FauxnanceCandleClient candleClient;
    @Mock
    private IndicatorCalculator indicatorCalculator;
    @Mock
    private SignalEngine signalEngine;

    @InjectMocks
    private AdviceService adviceService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(1L);
        account.setUserId(2L);
        account.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    void getAdvice_Success_HoldingAndWatchlist() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        
        Position pos = new Position();
        pos.setSymbol("AAPL");
        pos.setQuantity(10);
        when(positionRepository.findByAccountId(1L)).thenReturn(List.of(pos));
        
        Watchlist wl = new Watchlist();
        wl.setId(10L);
        when(watchlistRepository.findByUserId(2L)).thenReturn(List.of(wl));
        
        // AAPL is in both, TSLA is only in watchlist
        when(watchlistMapper.findSymbols(10L)).thenReturn(List.of("AAPL", "TSLA"));
        
        CandleResponse.Meta meta = new CandleResponse.Meta("AAPL", "1d", Instant.now().getEpochSecond());
        CandleResponse.Data data = new CandleResponse.Data("AAPL", "USD", "1d", List.of());
        CandleResponse candleResp = new CandleResponse(data, meta);
        
        when(candleClient.getCandles(anyString())).thenReturn(candleResp);
        
        IndicatorCalculator.Indicators indicators = new IndicatorCalculator.Indicators(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        when(indicatorCalculator.calculate(any())).thenReturn(indicators);
        
        SignalResponse sigAapl = new SignalResponse("AAPL", SignalSource.BOTH, SignalType.HOLD, 50, List.of("Reason"), Map.of(), false, Instant.now());
        SignalResponse sigTsla = new SignalResponse("TSLA", SignalSource.WATCHLIST, SignalType.BUY, 80, List.of("Reason"), Map.of(), false, Instant.now());
        
        when(signalEngine.generateSignal(eq("AAPL"), eq(SignalSource.BOTH), any(), any(), anyBoolean())).thenReturn(sigAapl);
        when(signalEngine.generateSignal(eq("TSLA"), eq(SignalSource.WATCHLIST), any(), any(), anyBoolean())).thenReturn(sigTsla);
        
        AdviceResponse response = adviceService.getAdvice(1L, 2L);
        
        assertNotNull(response);
        assertEquals(2, response.signals().size());
    }

    @Test
    void getAdvice_AccountNotFound() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(AccountNotFoundException.class, () -> adviceService.getAdvice(1L, 2L));
    }

    @Test
    void getAdvice_AccessDenied() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        assertThrows(AccessDeniedException.class, () -> adviceService.getAdvice(1L, 99L));
    }

    @Test
    void getAdvice_AccountNotActive() {
        account.setStatus(AccountStatus.SUSPENDED);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        assertThrows(AccountNotActiveException.class, () -> adviceService.getAdvice(1L, 2L));
    }

    @Test
    void getAdvice_ClientThrows_IgnoresAndContinues() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        
        Position pos = new Position();
        pos.setSymbol("AAPL");
        pos.setQuantity(10);
        when(positionRepository.findByAccountId(1L)).thenReturn(List.of(pos));
        when(watchlistRepository.findByUserId(2L)).thenReturn(List.of());
        
        when(candleClient.getCandles("AAPL")).thenThrow(new RuntimeException("API error"));
        
        AdviceResponse response = adviceService.getAdvice(1L, 2L);
        
        assertNotNull(response);
        assertEquals(0, response.signals().size()); // Exception caught and ignored
    }
}
