package com.zkuba.trading.service;

import com.zkuba.trading.client.CandleResponse;
import com.zkuba.trading.client.FauxnanceCandleClient;
import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.Position;
import com.zkuba.trading.domain.entity.Watchlist;
import com.zkuba.trading.domain.enums.SignalSource;
import com.zkuba.trading.dto.response.AdviceResponse;
import com.zkuba.trading.dto.response.SignalResponse;
import com.zkuba.trading.exception.AccountNotActiveException;
import com.zkuba.trading.exception.AccountNotFoundException;
import com.zkuba.trading.mapper.WatchlistMapper;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.PositionRepository;
import com.zkuba.trading.repository.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdviceService {

    private static final Logger log = LoggerFactory.getLogger(AdviceService.class);

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final WatchlistRepository watchlistRepository;
    private final WatchlistMapper watchlistMapper;
    private final FauxnanceCandleClient candleClient;
    private final IndicatorCalculator indicatorCalculator;
    private final SignalEngine signalEngine;

    public AdviceService(AccountRepository accountRepository,
                         PositionRepository positionRepository,
                         WatchlistRepository watchlistRepository,
                         WatchlistMapper watchlistMapper,
                         FauxnanceCandleClient candleClient,
                         IndicatorCalculator indicatorCalculator,
                         SignalEngine signalEngine) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.watchlistRepository = watchlistRepository;
        this.watchlistMapper = watchlistMapper;
        this.candleClient = candleClient;
        this.indicatorCalculator = indicatorCalculator;
        this.signalEngine = signalEngine;
    }

    public AdviceResponse getAdvice(Long accountId, Long userId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (!account.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("ACC-403");
        }

        if (!"ACTIVE".equals(account.getStatus().name())) {
            throw new AccountNotActiveException(accountId);
        }

        List<Position> positions = positionRepository.findByAccountId(accountId);
        List<Watchlist> watchlists = watchlistRepository.findByUserId(account.getUserId());

        Map<String, SignalSource> symbolsMap = new HashMap<>();

        for (Position position : positions) {
            if (position.getQuantity() > 0) {
                symbolsMap.put(position.getSymbol(), SignalSource.HOLDING);
            }
        }

        for (Watchlist watchlist : watchlists) {
            List<String> symbols = watchlistMapper.findSymbols(watchlist.getId());
            for (String symbol : symbols) {
                if (symbolsMap.containsKey(symbol)) {
                    symbolsMap.put(symbol, SignalSource.BOTH);
                } else {
                    symbolsMap.put(symbol, SignalSource.WATCHLIST);
                }
            }
        }

        List<SignalResponse> signals = new ArrayList<>();

        for (Map.Entry<String, SignalSource> entry : symbolsMap.entrySet()) {
            String symbol = entry.getKey();
            SignalSource source = entry.getValue();

            try {
                CandleResponse candleResponse = candleClient.getCandles(symbol);
                List<CandleResponse.Candle> candles = candleResponse.data().candles();
                
                Long lastRefreshed = candleResponse.meta().lastRefreshed();
                Instant priceTimestamp = lastRefreshed != null ? Instant.ofEpochSecond(lastRefreshed) : Instant.now();
                boolean stale = Instant.now().minusSeconds(300).isAfter(priceTimestamp); // 5 minutes stale threshold

                IndicatorCalculator.Indicators indicators = indicatorCalculator.calculate(candles);
                SignalResponse signalResponse = signalEngine.generateSignal(symbol, source, indicators, priceTimestamp, stale);
                signals.add(signalResponse);
            } catch (Exception e) {
                log.warn("Failed to generate advice for symbol {}: {}", symbol, e.getMessage());
            }
        }

        return new AdviceResponse(accountId, Instant.now(), signals);
    }
}
