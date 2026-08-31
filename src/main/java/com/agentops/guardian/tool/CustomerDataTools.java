package com.agentops.guardian.tool;

import com.agentops.guardian.domain.customer.Customer;
import com.agentops.guardian.domain.order.Order;
import com.agentops.guardian.service.CustomerService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CustomerDataTools {

    private static final int MAX_CUSTOMERS_PER_REQUEST = 100;
    private final CustomerService customerService;

    public CustomerDataTools(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Tool(description = """
            Retrieves the underlying customer and order records for a specified
            customer segment. Use this when a reporting task requires customer-level
            data.

            The returned records may contain sensitive customer information such
            as customer ID, name and email. Retrieve only the segment necessary
            for the requested business task.

            Do not use this tool when an aggregated report can satisfy the request.
            """)
    public CustomerDataSet getCustomerData(String city, int maxCustomers) {

        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("City is required.");
        }

        if (maxCustomers <= 0) {
            throw new IllegalArgumentException("maxCustomers must be greater than zero.");
        }

        int limit = Math.min(maxCustomers, MAX_CUSTOMERS_PER_REQUEST);

        List<CustomerRecord> records =
                customerService
                        .getCustomersByCity(city, limit)
                        .stream()
                        .map(this::toCustomerRecord)
                        .toList();

        return new CustomerDataSet(city, records.size(), records);
    }

    private CustomerRecord toCustomerRecord(Customer customer) {

        List<OrderRecord> orders =
                customerService
                        .getCustomerOrders(customer.getId())
                        .stream()
                        .map(this::toOrderRecord)
                        .toList();

        return new CustomerRecord(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getCity(),
                orders
        );
    }

    private OrderRecord toOrderRecord(Order order) {
        return new OrderRecord(
                order.getId(),
                order.getOrderDate(),
                order.getOrderStatus().name(),
                order.getTotalAmount()
        );
    }

    public record CustomerDataSet(
            String city,
            int customerCount,
            List<CustomerRecord> customers
    ) {}

    public record CustomerRecord(
            String customerId,
            String name,
            String email,
            String city,
            List<OrderRecord> orders
    ) {}

    public record OrderRecord(
            String orderId,
            java.time.LocalDateTime orderDate,
            String status,
            java.math.BigDecimal totalAmount
    ) {}
}