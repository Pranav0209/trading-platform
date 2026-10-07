package com.zkuba.trading.repository;

import com.zkuba.trading.domain.entity.PriceAlert;
import com.zkuba.trading.domain.entity.PriceAlert.AlertState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    /**
     * Find all alerts for an account
     */
    List<PriceAlert> findByAccountId(Long accountId);

    /**
     * Find alerts for an account filtered by state
     */
    List<PriceAlert> findByAccountIdAndState(Long accountId, AlertState state);

    /**
     * Find all ACTIVE and TRIGGERED alerts for a symbol.
     * Used by the market-data consumer to check if anyone is watching this symbol.
     */
    @Query("""
        SELECT a FROM PriceAlert a 
        WHERE a.symbol = :symbol 
        AND a.state IN ('ACTIVE', 'TRIGGERED')
        """)
    List<PriceAlert> findActiveAlertsForSymbol(@Param("symbol") String symbol);

    /**
     * Count active and triggered alerts for an account.
     * Used to enforce the 100-alert limit.
     */
    @Query("""
        SELECT COUNT(a) FROM PriceAlert a 
        WHERE a.account.id = :accountId 
        AND a.state IN ('ACTIVE', 'TRIGGERED')
        """)
    long countActiveAlertsForAccount(@Param("accountId") Long accountId);

    /**
     * Check if an alert belongs to an account (for authorization)
     */
    Optional<PriceAlert> findByIdAndAccountId(Long id, Long accountId);
}
