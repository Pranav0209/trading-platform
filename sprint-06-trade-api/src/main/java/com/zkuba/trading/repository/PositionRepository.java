package com.zkuba.trading.repository;

import com.zkuba.trading.domain.entity.Position;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByAccountId(Long accountId);

    Position findByAccountIdAndSymbol(Long accountId, String symbol);
}

