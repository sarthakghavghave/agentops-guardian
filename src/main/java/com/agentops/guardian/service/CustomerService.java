package com.agentops.guardian.service;

import com.agentops.guardian.domain.customer.Customer;
import com.agentops.guardian.repository.CustomerRepository;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer getCustomer(String customerId) {
        return customerRepository.findById(customerId).orElse(null);
    }
}