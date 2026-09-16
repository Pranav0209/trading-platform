package com.zkuba.executor.repository;

import com.zkuba.executor.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByIdAndAccountId(UUID id, Long accountId);
    
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
        "UPDATE Order o SET o.status = :newStatus, o.executedPrice = :executedPrice " +
        "WHERE o.id = :id AND o.status = 'NEW'"
    )
    int executeOrder(
        @org.springframework.data.repository.query.Param("id") UUID id, 
        @org.springframework.data.repository.query.Param("newStatus") com.zkuba.executor.domain.enums.OrderStatus newStatus, 
        @org.springframework.data.repository.query.Param("executedPrice") java.math.BigDecimal executedPrice
    );
}
