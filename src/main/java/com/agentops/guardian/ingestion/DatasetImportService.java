package com.agentops.guardian.ingestion;

import com.agentops.guardian.domain.customer.Customer;
import com.agentops.guardian.domain.order.Order;
import com.agentops.guardian.domain.order.OrderItem;
import com.agentops.guardian.domain.order.OrderStatus;
import com.agentops.guardian.domain.product.Product;
import com.agentops.guardian.repository.CustomerRepository;
import com.agentops.guardian.repository.OrderItemRepository;
import com.agentops.guardian.repository.OrderRepository;
import com.agentops.guardian.repository.ProductRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class DatasetImportService {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public DatasetImportService(
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository) {

        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional
    public void importDataset(Path datasetDirectory) throws IOException {

        System.out.println("Starting dataset import...");

        importCustomers(datasetDirectory.resolve("users.csv"));
        importProducts(datasetDirectory.resolve("products.csv"));
        importOrders(datasetDirectory.resolve("orders.csv"));
        importOrderItems(datasetDirectory.resolve("order_items.csv"));

        System.out.println("Dataset import completed.");
    }

    private void importCustomers(Path file) throws IOException {

        System.out.println("Importing customers...");

        try (
                CSVParser parser = CSVParser.parse(
                        file,
                        java.nio.charset.StandardCharsets.UTF_8,
                        CSVFormat.DEFAULT.builder()
                                .setHeader()
                                .setSkipHeaderRecord(true)
                                .build()
                )
        ) {

            int count = 0;
            for (CSVRecord record : parser) {
                Customer customer = Customer.builder()
                        .id(record.get("user_id"))
                        .name(record.get("name"))
                        .email(record.get("email"))
                        .gender(record.get("gender"))
                        .city(record.get("city"))
                        .signupDate(LocalDate.parse(record.get("signup_date"), DATE_FORMAT))
                        .build();

                customerRepository.save(customer);
                count++;
            }
            System.out.println("Customers imported: " + count);
        }
    }

    private void importProducts(Path file) throws IOException {

        System.out.println("Importing products...");

        try (
                CSVParser parser = CSVParser.parse(
                        file,
                        java.nio.charset.StandardCharsets.UTF_8,
                        CSVFormat.DEFAULT.builder()
                                .setHeader()
                                .setSkipHeaderRecord(true)
                                .build()
                )
        ) {
            int count = 0;
            for (CSVRecord record : parser) {
                Product product = Product.builder()
                        .id(record.get("product_id"))
                        .name(record.get("product_name"))
                        .category(record.get("category"))
                        .brand(record.get("brand"))
                        .price(new BigDecimal(record.get("price")))
                        .rating(new BigDecimal(record.get("rating")))
                        .build();

                productRepository.save(product);
                count++;
            }
            System.out.println("Products imported: " + count);
        }
    }

    private void importOrders(Path file) throws IOException {
        System.out.println("Importing orders...");
        try (
                CSVParser parser = CSVParser.parse(
                        file,
                        java.nio.charset.StandardCharsets.UTF_8,
                        CSVFormat.DEFAULT.builder()
                                .setHeader()
                                .setSkipHeaderRecord(true)
                                .build()
                )
        ) {

            int count = 0;
            for (CSVRecord record : parser) {
                Customer customer = customerRepository.findById(record.get("user_id")).orElseThrow(() ->
                        new IllegalStateException("Customer not found: " + record.get("user_id"))
                );

                Order order = Order.builder()
                        .id(record.get("order_id"))
                        .customer(customer)
                        .orderDate(LocalDateTime.parse(record.get("order_date"), DATETIME_FORMAT))
                        .orderStatus(OrderStatus.valueOf(record.get("order_status").trim().toUpperCase()))
                        .totalAmount(new BigDecimal(record.get("total_amount")))
                        .build();

                orderRepository.save(order);
                count++;
            }
            System.out.println("Orders imported: " + count);
        }
    }

    private void importOrderItems(Path file) throws IOException {
        System.out.println("Importing order items...");
        try (
                CSVParser parser = CSVParser.parse(
                        file,
                        java.nio.charset.StandardCharsets.UTF_8,
                        CSVFormat.DEFAULT.builder()
                                .setHeader()
                                .setSkipHeaderRecord(true)
                                .build()
                )
        ) {
            int count = 0;
            for (CSVRecord record : parser) {
                Order order = orderRepository.findById(record.get("order_id")).orElseThrow(() ->
                        new IllegalStateException("Order not found: " + record.get("order_id"))
                );

                Product product = productRepository.findById(record.get("product_id")).orElseThrow(() ->
                        new IllegalStateException("Product not found: " + record.get("product_id"))
                );

                String customerId = record.get("user_id");
                if (!order.getCustomer().getId().equals(customerId)) {
                    throw new IllegalStateException("Customer mismatch for order item: " + record.get("order_item_id"));
                }

                OrderItem orderItem = OrderItem.builder()
                        .id(record.get("order_item_id"))
                        .order(order)
                        .product(product)
                        .quantity(Integer.parseInt(record.get("quantity")))
                        .itemPrice(new BigDecimal(record.get("item_price")))
                        .itemTotal(new BigDecimal(record.get("item_total")))
                        .build();

                orderItemRepository.save(orderItem);
                count++;
            }
            System.out.println("Order items imported: " + count);
        }
    }
}