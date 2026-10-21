package com.agentops.guardian.tool;

import com.agentops.guardian.domain.customer.Customer;
import com.agentops.guardian.domain.order.Order;
import com.agentops.guardian.governance.execution.ToolInvocationCorrelation;
import com.agentops.guardian.service.CustomerService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CustomerTools {

    private final CustomerService customerService;
    private final ToolInvocationCorrelation toolInvocationCorrelation;

    public CustomerTools(
            CustomerService customerService,
            ToolInvocationCorrelation toolInvocationCorrelation
    ) {
        this.customerService = customerService;
        this.toolInvocationCorrelation = toolInvocationCorrelation;
    }

    public static final String GET_CUSTOMER_TOOL_NAME = "getCustomer";
    public static final String SEARCH_CUSTOMERS_BY_NAME_TOOL_NAME = "searchCustomersByName";
        public static final String GET_CUSTOMER_CITIES_TOOL_NAME = "getCustomerCities";

    public static boolean isCustomerPiiReadTool(String toolName) {
        return GET_CUSTOMER_TOOL_NAME.equals(toolName)
                || SEARCH_CUSTOMERS_BY_NAME_TOOL_NAME.equals(toolName);
    }

    @Tool(description = "Find a customer by their unique customer ID.")
    public String getCustomer(String customerId) {

        Customer customer = customerService.getCustomer(customerId);

        if (customer == null) return "No customer found with ID " + customerId + ".";

        String result = String.format("""
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
        toolInvocationCorrelation.recordCustomerLookupResult(
                GET_CUSTOMER_TOOL_NAME,
                customer.getEmail() == null || customer.getEmail().isBlank()
                        ? List.of()
                        : List.of(customer.getEmail())
        );
        return result;
    }

    @Tool(description = """
            Search for customers by name.
            Use this when the user identifies a customer by name instead of customer ID.
            Multiple customers may have the same name.
            """)
    public String searchCustomersByName(String name) {

        List<Customer> customers = customerService.searchByName(name);

        if (customers.isEmpty()) return "No customers found with name: " + name;
        if (customers.size() > 10) {
            toolInvocationCorrelation.recordCustomerLookupResult(SEARCH_CUSTOMERS_BY_NAME_TOOL_NAME, List.of());
            return "Too many customers found. Please provide additional information such as email or city.";
        }

        String result = customers.stream()
                .map(customer -> String.format(
                        "Customer ID: %s | Name: %s | Email: %s | City: %s",
                        customer.getId(),
                        customer.getName(),
                        customer.getEmail(),
                        customer.getCity()
                ))
                .collect(java.util.stream.Collectors.joining("\n"));
        toolInvocationCorrelation.recordCustomerLookupResult(
                SEARCH_CUSTOMERS_BY_NAME_TOOL_NAME,
                customers.stream()
                        .map(Customer::getEmail)
                        .filter(email -> email != null && !email.isBlank())
                        .distinct()
                        .toList()
        );
        return result;
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

        @Tool(description = "List the distinct city names in customer records and return their count. Use for questions about which cities or how many cities are in the database.")
        public String getCustomerCities() {
                List<String> cities = customerService.getDistinctCities();
                if (cities.isEmpty()) return "No cities found in customer records. City count: 0.";

                return String.format("Cities in customer records (%d):\n%s", cities.size(),
                                String.join("\n", cities));
        }
}