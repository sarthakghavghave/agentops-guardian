package com.agentops.guardian.tool;

import com.agentops.guardian.domain.customer.Customer;
import com.agentops.guardian.domain.order.Order;
import com.agentops.guardian.domain.order.OrderItem;
import com.agentops.guardian.domain.product.Product;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CustomerDataTools {

        private static final int MAX_CUSTOMERS_PER_REQUEST = 100;

        private final CustomerService customerService;
        private final ToolDataStore toolDataStore;
        private final WorkflowContextManager workflowContextManager;

        @Tool(description = """
                        Retrieves the underlying customer and order records for a specified
                        customer segment.

                        Use this when the business task requires customer-level information.

                        The returned data may contain sensitive customer information such
                        as customer ID, name, and email.

                        Retrieve only the customer segment necessary for the requested task.
                        """)
        public CustomerDataSet getCustomerData(
                        String city,
                        int maxCustomers) {

                if (city == null || city.isBlank()) {
                        throw new IllegalArgumentException("City is required.");
                }

                if (maxCustomers <= 0) {
                        throw new IllegalArgumentException("maxCustomers must be greater than zero.");
                }

                int limit = Math.min(
                                maxCustomers,
                                MAX_CUSTOMERS_PER_REQUEST);

                List<CustomerRecord> records = customerService
                                .getCustomersByCity(city, limit)
                                .stream()
                                .map(this::toCustomerRecord)
                                .toList();

                CustomerDataSet data = new CustomerDataSet(
                                city,
                                records.size(),
                                records);

                toolDataStore.storeCustomerData(data);
                workflowContextManager.current().markDataAcquired(DataClassification.RAW_CUSTOMER_DATA);

                return data;
        }

        private CustomerRecord toCustomerRecord(Customer customer) {

                List<OrderRecord> orders = customerService
                                .getCustomerOrders(customer.getId())
                                .stream()
                                .map(this::toOrderRecord)
                                .toList();

                return new CustomerRecord(
                                customer.getId(),
                                customer.getName(),
                                customer.getEmail(),
                                customer.getCity(),
                                orders);
        }

        private OrderRecord toOrderRecord(Order order) {

                List<ProductRecord> products = order.getItems()
                                .stream()
                                .map(this::toProductRecord)
                                .toList();

                return new OrderRecord(
                                order.getId(),
                                order.getOrderDate(),
                                order.getOrderStatus().name(),
                                order.getTotalAmount(),
                                products);
        }

        private ProductRecord toProductRecord(OrderItem item) {

                Product product = item.getProduct();

                return new ProductRecord(
                                product.getName(),
                                product.getCategory(),
                                item.getQuantity(),
                                item.getItemTotal());
        }

        public record CustomerDataSet(
                        String city,
                        int customerCount,
                        List<CustomerRecord> customers) {
        }

        public record CustomerRecord(
                        String customerId,
                        String name,
                        String email,
                        String city,
                        List<OrderRecord> orders) {
        }

        public record OrderRecord(
                        String orderId,
                        LocalDateTime orderDate,
                        String status,
                        BigDecimal totalAmount,
                        List<ProductRecord> products) {
        }

        public record ProductRecord(
                        String productName,
                        String category,
                        Integer quantity,
                        BigDecimal itemTotal) {
        }
}