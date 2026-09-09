package com.zkuba.trading.controller;

import com.zkuba.trading.dto.request.CreateWatchlistRequest;
import com.zkuba.trading.dto.response.WatchlistResponse;
import com.zkuba.trading.security.JwtAuthenticationFilter.AuthenticatedUser;
import com.zkuba.trading.service.WatchlistService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/watchlists")
public class WatchlistController {
    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<WatchlistResponse> all(@AuthenticationPrincipal AuthenticatedUser user) {
        return watchlistService.getAll(user.userId());
    }

    @PostMapping
    public WatchlistResponse create(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateWatchlistRequest request) {
        return watchlistService.create(user.userId(), request);
    }

    @PostMapping("/{id}/symbols/{symbol}")
    public void add(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @PathVariable String symbol) {
        watchlistService.addSymbol(user.userId(), id, symbol);
    }

    @DeleteMapping("/{id}/symbols/{symbol}")
    public void remove(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @PathVariable String symbol) {
        watchlistService.removeSymbol(user.userId(), id, symbol);
    }
}

