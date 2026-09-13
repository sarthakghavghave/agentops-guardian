package com.agentops.guardian.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.DataTransformation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ReportTools {

        private final ToolDataStore toolDataStore;
        private final WorkflowContextManager workflowContextManager;

        @Tool(description = """
                        Generates a business report from the customer data retrieved
                        earlier in the current workflow.

                        ANALYTICAL:
                        Produces aggregated customer-segment information without
                        individual customer identifiers.

                        REDACTED_DETAIL:
                        Produces useful customer-level business information while
                        excluding direct customer identifiers such as names, email
                        addresses, and customer IDs.

                        The report uses the customer data already retrieved during
                        this workflow.
                        """)
        public Report generateReport(ReportType reportType) {

                if (reportType == null) {
                        throw new IllegalArgumentException("Report type is required.");
                }

                CustomerDataTools.CustomerDataSet data = toolDataStore.getCustomerData();

                Report report = switch (reportType) {
                        case ANALYTICAL -> generateAnalyticalReport(data);
                        case REDACTED_DETAIL -> generateRedactedReport(data);
                };

                DataClassification resultClassification = switch (reportType) {
                        case ANALYTICAL -> DataClassification.ANALYTICAL;
                        case REDACTED_DETAIL -> DataClassification.REDACTED_DETAIL;
                };

                workflowContextManager.current().recordTransformation(
                                new DataTransformation(
                                                DataClassification.RAW_CUSTOMER_DATA,
                                                resultClassification,
                                                reportType.name(),
                                                Instant.now()));

                return report;
        }

        private Report generateAnalyticalReport(
                        CustomerDataTools.CustomerDataSet data) {

                int totalOrders = data.customers()
                                .stream()
                                .mapToInt(customer -> customer.orders().size())
                                .sum();

                BigDecimal totalRevenue = data.customers()
                                .stream()
                                .flatMap(customer -> customer.orders().stream())
                                .map(CustomerDataTools.OrderRecord::totalAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal averageOrderValue = totalOrders == 0 ? BigDecimal.ZERO
                                : totalRevenue.divide(BigDecimal.valueOf(totalOrders),
                                                2,
                                                RoundingMode.HALF_UP);

                String content = """
                                Customer Analytics Report
                                =========================

                                Customer Segment: %s
                                Customers Analyzed: %d
                                Total Orders: %d
                                Total Order Value: %s
                                Average Order Value: %s

                                This report contains aggregated business information.
                                """.formatted(
                                data.city(),
                                data.customerCount(),
                                totalOrders,
                                totalRevenue,
                                averageOrderValue);

                return new Report(
                                ReportType.ANALYTICAL,
                                false,
                                data.customerCount(),
                                content);
        }

        private Report generateRedactedReport(
                        CustomerDataTools.CustomerDataSet data) {

                StringBuilder content = new StringBuilder();

                content.append("""
                                Customer Detail Report
                                ======================

                                """);

                content.append("Customer Segment: ")
                                .append(data.city())
                                .append("\n");

                content.append("Customers: ")
                                .append(data.customerCount())
                                .append("\n\n");

                int customerNumber = 1;

                for (CustomerDataTools.CustomerRecord customer : data.customers()) {

                        content.append("Customer ")
                                        .append(customerNumber++)
                                        .append("\n");

                        content.append("----------\n");

                        content.append("City: ")
                                        .append(customer.city())
                                        .append("\n");

                        content.append("Orders: ")
                                        .append(customer.orders().size())
                                        .append("\n");

                        content.append("Products Ordered:\n");

                        for (CustomerDataTools.OrderRecord order : customer.orders()) {
                                for (CustomerDataTools.ProductRecord product : order.products()) {
                                        content.append("- ")
                                                        .append(product.productName())
                                                        .append(" | Category: ")
                                                        .append(product.category())
                                                        .append(" | Quantity: ")
                                                        .append(product.quantity())
                                                        .append(" | Value: ")
                                                        .append(product.itemTotal())
                                                        .append("\n");
                                }
                        }

                        content.append("\n");
                }

                return new Report(
                                ReportType.REDACTED_DETAIL,
                                true,
                                data.customerCount(),
                                content.toString());
        }

        public enum ReportType {
                ANALYTICAL,
                REDACTED_DETAIL
        }

        public record Report(
                        ReportType type,
                        boolean redacted,
                        int customerCount,
                        String content) {
        }
}