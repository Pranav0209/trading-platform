package com.zkuba.trading.domain.exception;

public class WatchlistNotFoundException extends NotFoundException {
    public WatchlistNotFoundException(Long watchlistId) {
        super("WLT-404", "Watchlist not found: " + watchlistId);
    }
}
