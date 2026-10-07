package com.zkuba.trading.controller;

import com.zkuba.trading.domain.enums.OrderStatus;
import com.zkuba.trading.dto.response.AccountResponse;
import com.zkuba.trading.dto.response.BalanceResponse;
import com.zkuba.trading.dto.response.OrderHistoryEntry;
import com.zkuba.trading.dto.response.PortfolioResponse;
import com.zkuba.trading.security.JwtAuthenticationFilter.AuthenticatedUser;
import com.zkuba.trading.service.AccountService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @GetMapping("/{id}")
    public AccountResponse getAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.getAccountResponseById(id, user.userId());
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse getBalance(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.getBalance(id, user.userId());
    }

    @GetMapping("/{id}/portfolio")
    public List<PortfolioResponse> getPortfolio(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.getPortfolioResponse(id, user.userId());
    }

    @GetMapping("/{id}/orders")
    public List<OrderHistoryEntry> getOrders(
            @PathVariable Long id,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.getOrderHistory(id, status, from, to, user.userId());
    }
}
