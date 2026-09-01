package com.agentops.guardian.service;

import com.agentops.guardian.domain.customer.Customer;
import com.agentops.guardian.domain.order.Order;
import com.agentops.guardian.repository.CustomerRepository;
import com.agentops.guardian.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    public CustomerService(CustomerRepository customerRepository, OrderRepository orderRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
    }

    public Customer getCustomer(String customerId) {
        return customerRepository.findById(customerId).orElse(null);
    }

    public List<Customer> searchByName(String name){
        return customerRepository.findByNameIgnoreCase(name);
    }

    public List<Order> getCustomerOrders(String customerId){
        return orderRepository.findByCustomerIdWithItemsAndProducts(customerId);
    }

    public List<Customer> getCustomersByCity(String city, int maxCustomers) {

        return customerRepository.findByCityIgnoreCase(city)
                .stream()
                .limit(maxCustomers)
                .toList();
    }
}