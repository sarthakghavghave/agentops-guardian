package com.agentops.guardian.repository;

import com.agentops.guardian.domain.customer.Customer;
import com.agentops.guardian.domain.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, String> {
    List<Customer> findByNameIgnoreCase(String name);
    List<Customer> findByCityIgnoreCase(String city);
}