package com.zkuba.executor.repository;

import com.zkuba.executor.domain.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {
    Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol);
    List<Position> findByAccountIdInAndSymbol(List<Long> accountIds, String symbol);

    @Query(
            value = "SELECT symbol FROM positions " +
                    "UNION SELECT symbol FROM watchlist_items " +
                    "UNION SELECT symbol FROM price_alerts WHERE state IN ('ACTIVE', 'TRIGGERED') " +
                    "UNION SELECT symbol FROM smart_rules WHERE enabled = TRUE",
            nativeQuery = true
    )
    List<String> findUniqueSymbolsOfInterest();
}
