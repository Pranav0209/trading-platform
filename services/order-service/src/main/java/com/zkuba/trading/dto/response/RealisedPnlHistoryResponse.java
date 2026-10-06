package com.zkuba.trading.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response for realised P&L history endpoint.
 * GET /api/v1/portfolio/{accountId}/pnl/realised
 */
public record RealisedPnlHistoryResponse(
        Long accountId,
        String baseCurrency,
        LocalDate from,              // Optional: echo back if provided in request
        LocalDate to,                // Optional: echo back if provided in request
        List<RealisedPnlRecord> records,    // P&L records for current page
        RealisedPnlSummary summary,        // Pagination and totals
        OffsetDateTime asOf          // Timestamp when response was generated
) {}
