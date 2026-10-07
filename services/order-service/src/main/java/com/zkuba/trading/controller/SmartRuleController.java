package com.zkuba.trading.controller;

import com.zkuba.trading.dto.request.CreateSmartRuleRequest;
import com.zkuba.trading.dto.response.SmartRuleResponse;
import com.zkuba.trading.exception.ForbiddenException;
import com.zkuba.trading.security.JwtAuthenticationFilter.AuthenticatedUser;
import com.zkuba.trading.service.SmartRuleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/smart-rules")
public class SmartRuleController {

    private final SmartRuleService smartRuleService;

    public SmartRuleController(SmartRuleService smartRuleService) {
        this.smartRuleService = smartRuleService;
    }

    @GetMapping
    public List<SmartRuleResponse> getRules(
            @PathVariable Long accountId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        verifyAuthorization(user.userId(), accountId);
        return smartRuleService.getRules(accountId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SmartRuleResponse createRule(
            @PathVariable Long accountId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateSmartRuleRequest request) {
        verifyAuthorization(user.userId(), accountId);
        return smartRuleService.createRule(accountId, request);
    }

    @DeleteMapping("/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(
            @PathVariable Long accountId,
            @PathVariable Long ruleId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        verifyAuthorization(user.userId(), accountId);
        smartRuleService.deleteRule(accountId, ruleId);
    }

    private void verifyAuthorization(Long tokenAccountId, Long resourceAccountId) {
        if (!tokenAccountId.equals(resourceAccountId)) {
            throw new ForbiddenException("ACC-403", "Account mismatch");
        }
    }
}