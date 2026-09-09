package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.Watchlist;
import com.zkuba.trading.domain.exception.InstrumentNotFoundException;
import com.zkuba.trading.domain.exception.WatchlistNotFoundException;
import com.zkuba.trading.dto.request.CreateWatchlistRequest;
import com.zkuba.trading.dto.response.WatchlistResponse;
import com.zkuba.trading.mapper.WatchlistMapper;
import com.zkuba.trading.repository.InstrumentRepository;
import com.zkuba.trading.repository.WatchlistRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchlistService {

    @Autowired
    private WatchlistMapper watchlistMapper;

    @Autowired
    private InstrumentRepository instruments;

    @Autowired
    private WatchlistRepository watchlists;

    public List<WatchlistResponse> getAll(Long userId) {
        return watchlists
                .findByUserId(userId)
                .stream()
                .map(
                        watchlist ->
                                new WatchlistResponse(
                                        watchlist.getId(),
                                        watchlist.getName(),
                                        watchlistMapper.findSymbols(watchlist.getId())))
                .toList();
    }

    @Transactional
    public WatchlistResponse create(Long userId, CreateWatchlistRequest request) {
        Watchlist watchlist = new Watchlist();
        watchlist.setUserId(userId);
        watchlist.setName(request.name());
        watchlists.save(watchlist);
        return new WatchlistResponse(watchlist.getId(), watchlist.getName(), List.of());
    }

    @Transactional
    public void addSymbol(Long userId, Long watchlistId, String symbol) {
        verifyOwnership(userId, watchlistId);
        String normalizedSymbol = symbol.toUpperCase();
        if (instruments.findById(normalizedSymbol).isEmpty()) {
            throw new InstrumentNotFoundException(symbol);
        }
        watchlistMapper.addSymbol(watchlistId, normalizedSymbol);
    }

    @Transactional
    public void removeSymbol(Long userId, Long watchlistId, String symbol) {
        verifyOwnership(userId, watchlistId);
        watchlistMapper.removeSymbol(watchlistId, symbol.toUpperCase());
    }

    private void verifyOwnership(Long userId, Long watchlistId) {
        Watchlist watchlist = watchlists.findById(watchlistId).orElse(null);
        if (watchlist == null || !watchlist.getUserId().equals(userId)) {
            throw new WatchlistNotFoundException(watchlistId);
        }
    }
}
