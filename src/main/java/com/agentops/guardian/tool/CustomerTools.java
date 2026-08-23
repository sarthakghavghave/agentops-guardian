package com.agentops.guardian.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class CustomerTools {

    @Tool(description = "Get customer information using the customer ID")
    public String getCustomer(int customerId) {

        if (customerId == 101) {
            return """
                    Customer ID: 101
                    Name: Rahul Sharma
                    Email: rahul@example.com
                    Membership: Premium
                    """;
        }

        return "Customer not found.";
    }

    @Tool(description = "Get order information using the order ID")
    public String getOrder(String orderId) {

        if ("ORD-101".equalsIgnoreCase(orderId)) {
            return """
                    Order ID: ORD-101
                    Customer ID: 101
                    Amount: INR 4999
                    Status: PAID
                    """;
        }

        return "Order not found.";
    }

    @Tool(description = "Create a refund for a paid order")
    public String createRefund(String orderId) {

        if ("ORD-101".equalsIgnoreCase(orderId)) {
            return "Refund successfully created for order ORD-101.";
        }

        return "Refund could not be created. Order not found.";
    }
}