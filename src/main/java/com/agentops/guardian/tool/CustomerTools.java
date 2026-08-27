package com.agentops.guardian.tool;

import com.agentops.guardian.domain.customer.Customer;
import com.agentops.guardian.domain.order.Order;
import com.agentops.guardian.service.CustomerService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CustomerTools {

    private final CustomerService customerService;
    public CustomerTools(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Tool(description = "Find a customer by their unique customer ID.")
    public String getCustomer(String customerId) {

        Customer customer = customerService.getCustomer(customerId);

        if (customer == null) return "No customer found with ID " + customerId + ".";

        return String.format("""
                Customer ID: %s
                Name: %s
                Email: %s
                Gender: %s
                City: %s
                Signup Date: %s
                """,
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getGender(),
                customer.getCity(),
                customer.getSignupDate()
        );
    }

    @Tool(description = """
            Search for customers by name.
            Use this when the user identifies a customer by name instead of customer ID.
            Multiple customers may have the same name.
            """)
    public String searchCustomersByName(String name) {

        List<Customer> customers = customerService.searchByName(name);

        if (customers.isEmpty()) return "No customers found with name: " + name;
        if (customers.size() > 10) return "Too many customers found. Please provide additional information such as email or city.";

        return customers.stream()
                .map(customer -> String.format(
                        "Customer ID: %s | Name: %s | Email: %s | City: %s",
                        customer.getId(),
                        customer.getName(),
                        customer.getEmail(),
                        customer.getCity()
                ))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Tool(description = "Retrieve all orders belonging to a specific customer.")
    public String getCustomerOrders(String customerId) {

        List<Order> orders = customerService.getCustomerOrders(customerId);

        if (orders.isEmpty()) return "No orders found for customer " + customerId + ".";

        return orders.stream()
                .map(order -> String.format(
                        "Order ID: %s | Status: %s | Amount: %s | Date: %s",
                        order.getId(),
                        order.getOrderStatus(),
                        order.getTotalAmount(),
                        order.getOrderDate()
                ))
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}