package com.agentops.guardian.tool;

import com.agentops.guardian.entity.Customer;
import com.agentops.guardian.entity.Order;
import com.agentops.guardian.service.CustomerService;
import com.agentops.guardian.service.OrderService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class CustomerTools {

    private final CustomerService customerService;
    private final OrderService orderService;

    public CustomerTools(CustomerService customerService, OrderService orderService) {

        this.customerService = customerService;
        this.orderService = orderService;
    }

    @Tool(description = "Get customer information using the customer ID")
    public String getCustomer(long customerId) {

        Customer customer = customerService.getCustomer(customerId);
        if (customer == null) return "Customer not found.";

        return String.format(
                """
                Customer ID: %d
                Name: %s
                Email: %s
                Membership: %s
                """,
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getMembership()
        );
    }

    @Tool(description = "Get order information using the order ID")
    public String getOrder(long orderId) {

        Order order = orderService.getOrder(orderId);
        if (order == null) return "Order not found.";

        return String.format(
                """
                Order ID: %d
                Customer ID: %d
                Amount: %s
                Status: %s
                """,
                order.getId(),
                order.getCustomerId(),
                order.getAmount(),
                order.getStatus()
        );
    }

    @Tool(description = """
                    Create a refund for an order.
                    Use this tool ONLY when the user has explicitly requested
                    a refund for the specified order.
                    Do not call this tool merely because an order ID was provided.
                    """)
    public String createRefund(long orderId) {

        try {
            Order order = orderService.refundOrder(orderId);
            if (order == null) return "Order not found.";

            return "Refund successfully created for order " + order.getId() + ".";

        } catch (IllegalStateException e) {
            return "Refund failed: " + e.getMessage();
        }
    }
}