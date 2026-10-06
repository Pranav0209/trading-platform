package com.zkuba.trading.service;

import com.zkuba.trading.dto.response.RealisedPnlHistoryResponse;
import java.time.LocalDate;

public interface RealisedPnlService {

    /**
     * Get realised P&L history for an account.
     *
     * @param accountId      Account ID (from path parameter)
     * @param userId         User ID from JWT claim (for verification)
     * @param symbol         Optional symbol filter
     * @param from           Optional from date (inclusive)
     * @param to             Optional to date (inclusive)
     * @param page           Zero-indexed page number (default 0)
     * @param size           Page size, max 100 (default 20)
     * @return RealisedPnlHistoryResponse with records, pagination, and summary
     * @throws ForbiddenException if accountId doesn't belong to userId
     * @throws InvalidParametersException if filters are invalid (e.g. from > to)
     */
    RealisedPnlHistoryResponse getRealisedPnlHistory(
            Long accountId,
            Long userId,
            String symbol,
            LocalDate from,
            LocalDate to,
            Integer page,
            Integer size
    );
}
