package com.zkuba.executor.repository;

import com.zkuba.executor.domain.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {
    Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol);
}
