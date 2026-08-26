package com.agentops.guardian.tool;

import com.agentops.guardian.domain.customer.Customer;
import com.agentops.guardian.domain.order.Order;
import com.agentops.guardian.domain.order.OrderItem;
import com.agentops.guardian.domain.product.Product;
import com.agentops.guardian.service.CustomerService;
import com.agentops.guardian.service.OrderItemService;
import com.agentops.guardian.service.OrderService;
import com.agentops.guardian.service.ProductService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CustomerTools {

    private final CustomerService customerService;
    private final OrderService orderService;
    private final OrderItemService orderItemService;
    private final ProductService productService;

    public CustomerTools(
            CustomerService customerService,
            OrderService orderService,
            OrderItemService orderItemService,
            ProductService productService) {

        this.customerService = customerService;
        this.orderService = orderService;
        this.orderItemService = orderItemService;
        this.productService = productService;
    }

    @Tool(description = "Retrieve customer information using the customer ID.")
    public String getCustomer(String customerId) {

        Customer customer = customerService.getCustomer(customerId);

        if (customer == null) {
            return "Customer " + customerId + " was not found.";
        }

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

    @Tool(description = "Retrieve order information using the order ID.")
    public String getOrder(String orderId) {

        Order order = orderService.getOrder(orderId);

        if (order == null) {
            return "Order " + orderId + " was not found.";
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

    @Tool(description = "Retrieve all orders belonging to a customer.")
    public String getCustomerOrders(String customerId) {

        List<Order> orders = orderService.getCustomerOrders(customerId);

        if (orders.isEmpty()) {
            return "No orders found for customer " + customerId + ".";
        }

        StringBuilder result = new StringBuilder();

        for (Order order : orders) {
            result.append(String.format(
                    "Order ID: %s | Status: %s | Amount: %s | Date: %s%n",
                    order.getId(),
                    order.getOrderStatus(),
                    order.getTotalAmount(),
                    order.getOrderDate()
            ));
        }

        return result.toString();
    }

    @Tool(description = "Retrieve all items belonging to an order.")
    public String getOrderItems(String orderId) {

        List<OrderItem> items = orderItemService.getOrderItems(orderId);

        if (items.isEmpty()) {
            return "No items found for order " + orderId + ".";
        }

        StringBuilder result = new StringBuilder();

        for (OrderItem item : items) {

            Product product = item.getProduct();

            result.append(String.format(
                    "Product ID: %s | Product: %s | Quantity: %d | Unit Price: %s | Total: %s%n",
                    product.getId(),
                    product.getName(),
                    item.getQuantity(),
                    item.getItemPrice(),
                    item.getItemTotal()
            ));
        }

        return result.toString();
    }

    @Tool(description = "Retrieve product information using the product ID.")
    public String getProduct(String productId) {

        Product product = productService.getProduct(productId);

        if (product == null) {
            return "Product " + productId + " was not found.";
        }

        return String.format("""
                Product ID: %s
                Name: %s
                Category: %s
                Brand: %s
                Price: %s
                Rating: %s
                """,
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getBrand(),
                product.getPrice(),
                product.getRating()
        );
    }

    @Tool(description = """
            Return an order.
            This operation changes the order state and must only be used
            when the user explicitly requests that the order be returned.
            Only completed orders are eligible for return.
            """)
    public String returnOrder(String orderId) {

        try {
            Order order = orderService.returnOrder(orderId);

            if (order == null) {
                return "Order " + orderId + " was not found.";
            }

            return "Order " + orderId + " has been successfully returned.";

        } catch (IllegalStateException e) {
            return "Return request rejected: " + e.getMessage();
        }
    }
}