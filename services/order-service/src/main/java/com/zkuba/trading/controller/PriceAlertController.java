package com.zkuba.trading.controller;

import com.zkuba.trading.domain.entity.PriceAlert.AlertState;
import com.zkuba.trading.dto.request.CreateAlertRequest;
import com.zkuba.trading.dto.response.AlertResponse;
import com.zkuba.trading.security.JwtAuthenticationFilter.AuthenticatedUser;
import com.zkuba.trading.service.PriceAlertService;
import com.zkuba.trading.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/alerts")
public class PriceAlertController {

    private final PriceAlertService alertService;
    private final AccountService accountService;
    private static final long ALERT_LIMIT = 100L;

    public PriceAlertController(PriceAlertService alertService, AccountService accountService) {
        this.alertService = alertService;
        this.accountService = accountService;
    }

    /**
     * GET /api/v1/accounts/{accountId}/alerts
     * List all price alerts for an account, optionally filtered by state
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAlerts(
            @PathVariable Long accountId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String state) {

        // Authorization: verify token accountId matches path accountId
        verifyAuthorization(user.userId(), accountId);

        AlertState stateFilter = null;
        if (state != null) {
            try {
                stateFilter = AlertState.valueOf(state.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        List<AlertResponse> alerts = alertService.getAlerts(accountId, stateFilter);

        Map<String, Object> response = new HashMap<>();
        response.put("alerts", alerts);
        response.put("count", alerts.size());
        response.put("limit", ALERT_LIMIT);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/accounts/{accountId}/alerts
     * Create a new price alert
     */
    @PostMapping
    public ResponseEntity<AlertResponse> createAlert(
            @PathVariable Long accountId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateAlertRequest request) {

        // Authorization: verify token accountId matches path accountId
        verifyAuthorization(user.userId(), accountId);

        AlertResponse alert = alertService.createAlert(accountId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(alert);
    }

    /**
     * GET /api/v1/accounts/{accountId}/alerts/{alertId}
     * Get a specific price alert
     */
    @GetMapping("/{alertId}")
    public ResponseEntity<AlertResponse> getAlert(
            @PathVariable Long accountId,
            @PathVariable Long alertId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        // Authorization: verify token accountId matches path accountId
        verifyAuthorization(user.userId(), accountId);

        AlertResponse alert = alertService.getAlert(accountId, alertId);
        return ResponseEntity.ok(alert);
    }

    /**
     * DELETE /api/v1/accounts/{accountId}/alerts/{alertId}
     * Delete (cancel) a price alert
     */
    @DeleteMapping("/{alertId}")
    public ResponseEntity<Void> deleteAlert(
            @PathVariable Long accountId,
            @PathVariable Long alertId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        // Authorization: verify token accountId matches path accountId
        verifyAuthorization(user.userId(), accountId);

        alertService.deleteAlert(accountId, alertId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/accounts/{accountId}/alerts/{alertId}/reset
     * Reset a triggered alert back to ACTIVE
     */
    @PostMapping("/{alertId}/reset")
    public ResponseEntity<AlertResponse> resetAlert(
            @PathVariable Long accountId,
            @PathVariable Long alertId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        // Authorization: verify token accountId matches path accountId
        verifyAuthorization(user.userId(), accountId);

        AlertResponse alert = alertService.resetAlert(accountId, alertId);
        return ResponseEntity.ok(alert);
    }

    /**
     * Authorization check: verify the account belongs to the user.
     */
    private void verifyAuthorization(Long userId, Long accountId) {
        accountService.getAccountAndValidateAccess(accountId, userId);
    }
}
