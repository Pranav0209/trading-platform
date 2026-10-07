package com.zkuba.executor.repository;

import com.zkuba.executor.domain.entity.SmartRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmartRuleRepository extends JpaRepository<SmartRule, Long> {
    List<SmartRule> findBySymbolAndEnabledTrue(String symbol);
}