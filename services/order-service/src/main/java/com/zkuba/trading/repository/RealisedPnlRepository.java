package com.zkuba.trading.repository;

import com.zkuba.trading.domain.entity.RealisedPnl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

/**
 * Repository for RealisedPnl entities.
 * Provides read-only access to realised P&L records written by executor-service.
 */
public interface RealisedPnlRepository extends JpaRepository<RealisedPnl, Long> {

    /**
     * Query realised P&L history for an account with optional filtering by symbol and date range.
     * Results ordered by realised_at DESC (most recent first).
     *
     * @param accountId    Account ID (required)
     * @param symbol       Symbol filter (optional, null means no filter)
     * @param fromDate     From date inclusive (optional, null means unbounded below)
     * @param toDate       To date inclusive (optional, null means unbounded above)
     * @param pageable     Pagination info
     * @return Page of RealisedPnl records matching the criteria
     */
    @Query("""
        SELECT p FROM RealisedPnl p
        WHERE p.accountId = :accountId
        AND (:symbol IS NULL OR p.symbol = :symbol)
        AND (:fromDate IS NULL OR p.realisedAt >= :fromDate)
        AND (:toDate IS NULL OR p.realisedAt <= :toDate)
        ORDER BY p.realisedAt DESC
    """)
    Page<RealisedPnl> findRealisedPnlHistory(
            @Param("accountId") Long accountId,
            @Param("symbol") String symbol,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            Pageable pageable
    );

    /**
     * Calculate total realised P&L for an account across all records
     * (used for the summary in the API response).
     *
     * @param accountId    Account ID
     * @param symbol       Symbol filter (optional, null means no filter)
     * @param fromDate     From date inclusive (optional, null means unbounded below)
     * @param toDate       To date inclusive (optional, null means unbounded above)
     * @return Sum of all realised_pnl for matching records, or 0 if none found
     */
    @Query("""
        SELECT COALESCE(SUM(p.realisedPnl), 0)
        FROM RealisedPnl p
        WHERE p.accountId = :accountId
        AND (:symbol IS NULL OR p.symbol = :symbol)
        AND (:fromDate IS NULL OR p.realisedAt >= :fromDate)
        AND (:toDate IS NULL OR p.realisedAt <= :toDate)
    """)
    java.math.BigDecimal sumRealisedPnl(
            @Param("accountId") Long accountId,
            @Param("symbol") String symbol,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate
    );
}
