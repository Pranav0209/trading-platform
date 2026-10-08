package com.zkuba.trading.service;

import com.zkuba.trading.dto.request.CreateWatchlistRequest;
import com.zkuba.trading.dto.response.WatchlistResponse;
import java.util.List;

public interface WatchlistService {

    List<WatchlistResponse> getAll(Long userId);

    WatchlistResponse create(Long userId, CreateWatchlistRequest request);

    void addSymbol(Long userId, Long watchlistId, String symbol);

    void removeSymbol(Long userId, Long watchlistId, String symbol);

    void delete(Long userId, Long watchlistId);
}
