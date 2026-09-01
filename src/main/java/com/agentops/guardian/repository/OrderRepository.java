package com.agentops.guardian.repository;

import com.agentops.guardian.domain.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByCustomer_Id(String customerId);

    @Query("""
            SELECT DISTINCT o
            FROM Order o
            LEFT JOIN FETCH o.items oi
            LEFT JOIN FETCH oi.product
            WHERE o.customer.id = :customerId
            """)
    List<Order> findByCustomerIdWithItemsAndProducts(@Param("customerId") String customerId);
}