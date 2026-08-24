package com.agentops.guardian.config;

import com.agentops.guardian.entity.Customer;
import com.agentops.guardian.entity.Order;
import com.agentops.guardian.repository.CustomerRepository;
import com.agentops.guardian.repository.OrderRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initializeData(CustomerRepository customerRepository, OrderRepository orderRepository) {

        return args -> {

            if (customerRepository.count() == 0) {
                Customer customer = customerRepository.save(
                        Customer.builder()
                                .name("Rahul Sharma")
                                .email("rahul@example.com")
                                .membership("Premium")
                                .build()
                );

                orderRepository.save(
                        Order.builder()
                                .customerId(customer.getId())
                                .amount(new BigDecimal("4999.00"))
                                .status("PAID")
                                .build()
                );
            }
        };
    }
}