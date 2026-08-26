package com.agentops.guardian.repository;

import com.agentops.guardian.domain.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByCustomer_Id(String customerId);
}