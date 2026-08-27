package com.agentops.guardian.repository;

import com.agentops.guardian.domain.order.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, String> {

    @Query("""
            SELECT oi
            FROM OrderItem oi
            JOIN FETCH oi.product
            WHERE oi.order.id = :orderId
            """)
    List<OrderItem> findByOrderIdWithProduct(@Param("orderId") String orderId);
}