package com.zkuba.executor.repository;

import com.zkuba.executor.domain.entity.RealisedPnl;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * Repository for RealisedPnl entities.
 * Each row represents a completed SELL order with a locked-in P&L.
 * The UNIQUE constraint on order_id ensures idempotency (no duplicate P&L records per order).
 */
public interface RealisedPnlRepository extends JpaRepository<RealisedPnl, Long> {
    // JpaRepository provides save() which honors UNIQUE constraint via database constraint
    // Kafka replay safety: if same order_id is replayed, the database UNIQUE constraint
    // will reject the duplicate insert (handled by JPA's DataIntegrityViolationException)
}
