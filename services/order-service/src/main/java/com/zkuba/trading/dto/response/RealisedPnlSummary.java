package com.zkuba.trading.dto.response;

import java.math.BigDecimal;

/**
 * Summary statistics for realised P&L query.
 */
public record RealisedPnlSummary(
        Long totalCount,        // Total number of P&L records (not just on current page)
        Long totalPages,        // Computed as ceil(totalCount / pageSize)
        Long currentPage,       // Zero-indexed current page
        Long pageSize,          // Records per page
        BigDecimal totalRealisedPnl  // Sum of realised_pnl across ALL records (not just page)
) {}
