package com.zkuba.trading.controller;

import com.zkuba.trading.dto.response.RealisedPnlHistoryResponse;
import com.zkuba.trading.security.JwtAuthenticationFilter.AuthenticatedUser;
import com.zkuba.trading.service.RealisedPnlService;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portfolio API Controller
 * Exposes endpoints for portfolio data and analytics including realised P&L history.
 */
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    @Autowired
    private RealisedPnlService realisedPnlService;

    /**
     * Get realised profit and loss history for an account.
     * <p>
     * Transaction-by-transaction history of realised profit and loss for the account.
     * Each row represents one completed SELL order. Realised P&L is booked at the moment of sale
     * and never changes. Results are ordered by realised date, most recent first.
     * <p>
     * The `averageCost` field is the weighted average cost of the position at the time of the SELL,
     * captured before the position was updated. Realised P&L is calculated as:
     * `(sellPrice - averageCost) * quantity`.
     * <p>
     * All money and quantity values use NUMERIC precision to avoid floating-point rounding errors.
     * The backend verifies that {accountId} equals the accountId claim in the JWT and returns 403 otherwise.
     *
     * @param accountId Account ID (path parameter, required)
     * @param user      Authenticated user from JWT
     * @param symbol    Symbol filter (query parameter, optional)
     * @param from      From date inclusive, ISO 8601 date in UTC (query parameter, optional)
     * @param to        To date inclusive, ISO 8601 date in UTC (query parameter, optional)
     * @param page      Zero-indexed page number (query parameter, optional, default 0)
     * @param size      Number of records per page, max 100 (query parameter, optional, default 20)
     * @return RealisedPnlHistoryResponse with paginated records and summary
     * @throws ForbiddenException        if accountId doesn't belong to the authenticated user (403)
     * @throws InvalidParametersException if query parameters are invalid, e.g. from > to or size > 100 (400)
     * @throws AccountNotFoundException   if accountId doesn't exist (404)
     * @throws UnauthorizedException    if JWT is missing or invalid (401)
     */
    @GetMapping("/{accountId}/pnl/realised")
    public RealisedPnlHistoryResponse getRealisedPnlHistory(
            @PathVariable Long accountId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        
        return realisedPnlService.getRealisedPnlHistory(
                accountId,
                user.userId(),
                symbol,
                from,
                to,
                page,
                size
        );
    }
}
