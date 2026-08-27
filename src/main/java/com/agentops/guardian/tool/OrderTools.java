package com.agentops.guardian.tool;

import com.agentops.guardian.domain.order.Order;
import com.agentops.guardian.service.OrderItemService;
import com.agentops.guardian.service.OrderService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class OrderTools {

    private final OrderService orderService;
    private final OrderItemService orderItemService;

    public OrderTools(OrderService orderService, OrderItemService orderItemService) {
        this.orderService = orderService;
        this.orderItemService = orderItemService;
    }

    @Tool(description = "Retrieve an order using its unique order ID.")
    public String getOrder(String orderId) {

        Order order = orderService.getOrder(orderId);

        if (order == null) {
            return "No order found with ID " + orderId + ".";
        }

        return String.format("""
                Order ID: %s
                Customer ID: %s
                Order Date: %s
                Status: %s
                Total Amount: %s
                """,
                order.getId(),
                order.getCustomer().getId(),
                order.getOrderDate(),
                order.getOrderStatus(),
                order.getTotalAmount()
        );
    }

    @Tool(description = "Retrieve all products and quantities contained in an order.")
    public String getOrderItems(String orderId) {

        var items = orderItemService.getOrderItems(orderId);

        if (items.isEmpty()) {
            return "No items found for order " + orderId + ".";
        }

        return items.stream()
                .map(item -> String.format(
                        "Product: %s | Quantity: %d | Unit Price: %s | Total: %s",
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getItemPrice(),
                        item.getItemTotal()
                ))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Tool(description = """
            Return an order.
            Use only when the customer explicitly requests that an order be returned.
            Only completed orders are eligible for return.
            """)
    public String returnOrder(String orderId) {

        try {
            Order order = orderService.returnOrder(orderId);
            if (order == null) return "No order found with ID " + orderId + ".";

            return "Order " + orderId + " has been successfully returned.";

        } catch (IllegalStateException e) {
            return "Return request rejected: " + e.getMessage();
        }
    }
}