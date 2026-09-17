package com.zkuba.executor.repository;

import com.zkuba.executor.domain.entity.Order;
import com.zkuba.executor.domain.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {


    @Modifying
    @Query("""
            UPDATE Order o
            SET o.status = :newStatus,
                o.executedPrice = :executedPrice,
                o.executedOn = :executedOn
            WHERE o.id = :id
              AND o.status = 'NEW'
            """)
    int executeOrder(
            @Param("id") UUID id,
            @Param("newStatus") OrderStatus newStatus,
            @Param("executedPrice") BigDecimal executedPrice,
            @Param("executedOn") OffsetDateTime executedOn);
}