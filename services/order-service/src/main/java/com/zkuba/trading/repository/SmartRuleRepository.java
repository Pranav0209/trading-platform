package com.zkuba.trading.repository;

import com.zkuba.trading.domain.entity.SmartRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmartRuleRepository extends JpaRepository<SmartRule, Long> {

    List<SmartRule> findByAccountIdAndParentRuleIsNullOrderByCreatedAtDesc(Long accountId);

    Optional<SmartRule> findByIdAndAccountId(Long id, Long accountId);
}