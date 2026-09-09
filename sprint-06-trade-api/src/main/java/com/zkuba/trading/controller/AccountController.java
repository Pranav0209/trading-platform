package com.zkuba.trading.controller;

import com.zkuba.trading.dto.response.AccountResponse;
import com.zkuba.trading.dto.response.HoldingResponse;
import com.zkuba.trading.dto.response.PositionResponse;
import com.zkuba.trading.security.JwtAuthenticationFilter.AuthenticatedUser;
import com.zkuba.trading.service.AccountService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @GetMapping
    public AccountResponse account(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.responseByUser(user.userId());
    }

    @GetMapping("/positions")
    public List<PositionResponse> positions(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountService
                .getPositions(user.userId())
                .stream()
                .map(p -> new PositionResponse(p.getSymbol(), p.getQuantity(), p.getAverageCost()))
                .toList();
    }

    @GetMapping("/holdings")
    public List<HoldingResponse> holdings(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountService
                .getPositions(user.userId())
                .stream()
                .map(p -> new HoldingResponse(p.getSymbol(), p.getQuantity(), p.getAverageCost()))
                .toList();
    }
}


