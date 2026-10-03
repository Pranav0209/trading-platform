package com.zkuba.trading.exception;

public class WatchlistNotFoundException extends NotFoundException {
    private final Long watchlistId;

    public WatchlistNotFoundException(Long watchlistId) {
        super("WLT-404", "Watchlist not found: " + watchlistId);
        this.watchlistId = watchlistId;
    }

    public Long getWatchlistId() {
        return watchlistId;
    }
}
