package com.zkuba.executor.repository;

import com.zkuba.executor.domain.entity.SmartRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SmartRuleRepository extends JpaRepository<SmartRule, Long> {

    @Query("""
            SELECT DISTINCT s
            FROM SmartRule s
            LEFT JOIN FETCH s.children
            JOIN FETCH s.account
            WHERE s.symbol = :symbol
              AND s.enabled = true
            """)
    List<SmartRule> findBySymbolAndEnabledTrue(@Param("symbol") String symbol);
}