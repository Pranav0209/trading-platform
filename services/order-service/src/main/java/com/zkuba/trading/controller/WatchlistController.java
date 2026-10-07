package com.zkuba.trading.controller;

import com.zkuba.trading.dto.request.CreateWatchlistRequest;
import com.zkuba.trading.dto.response.WatchlistResponse;
import com.zkuba.trading.security.JwtAuthenticationFilter.AuthenticatedUser;
import com.zkuba.trading.service.WatchlistService;
import com.zkuba.trading.service.AccountService;
import com.zkuba.trading.domain.entity.Account;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.zkuba.trading.service.MarketDataStreamService;

@RestController
@RequestMapping("/api/v1/watchlists")
public class WatchlistController {
    @Autowired
    private WatchlistService watchlistService;

    @Autowired
    private MarketDataStreamService marketDataStreamService;
    
    @Autowired
    private AccountService accountService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public SseEmitter streamPrices(@AuthenticationPrincipal AuthenticatedUser user) {
        // Get the account to retrieve its currency
        Account account = accountService.getByUserId(user.userId());
        String accountCurrency = account.getCurrency() != null ? account.getCurrency() : "USD";
        
        // Subscribe with user context for per-account currency conversion
        return marketDataStreamService.subscribe(user.userId(), accountCurrency);
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

