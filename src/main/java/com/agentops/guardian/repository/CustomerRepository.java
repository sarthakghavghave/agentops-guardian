package com.agentops.guardian.repository;

import com.agentops.guardian.domain.customer.Customer;
import com.agentops.guardian.domain.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, String> {
    List<Customer> findByNameIgnoreCase(String name);
    List<Customer> findByCityIgnoreCase(String city);

    @Query("select distinct c.city from Customer c where c.city is not null and trim(c.city) <> '' order by c.city")
    List<String> findDistinctCities();
}