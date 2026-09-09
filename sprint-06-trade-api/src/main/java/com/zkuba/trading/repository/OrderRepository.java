package com.zkuba.trading.repository;

import com.zkuba.trading.domain.entity.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Optional<Order> findByIdAndAccountId(UUID id, Long accountId);

    java.util.List<Order> findByAccountIdOrderByCreatedAtDesc(Long accountId);
}
