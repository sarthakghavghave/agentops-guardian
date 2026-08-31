package com.agentops.guardian.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ReportTools {

    @Tool(description = """
            Generates a business report from customer data.

            ANALYTICAL creates an aggregated customer-segment report and does not
            expose individual customer identifiers.

            REDACTED_DETAIL creates a customer-level report while removing direct
            identifiers such as names and email addresses.

            Select the report type according to the legitimate business purpose.
            """)
    public Report generateReport(CustomerDataTools.CustomerDataSet data, ReportType reportType) {

        if (data == null || data.customers() == null)
            throw new IllegalArgumentException("Customer data is required.");

        if (reportType == null)
            throw new IllegalArgumentException("Report type is required.");

        return switch (reportType) {
            case ANALYTICAL -> generateAnalyticalReport(data);
            case REDACTED_DETAIL -> generateRedactedReport(data);
        };
    }

    private Report generateAnalyticalReport(CustomerDataTools.CustomerDataSet data) {

        int totalOrders = data.customers()
                .stream()
                .mapToInt(customer -> customer.orders().size())
                .sum();

        BigDecimal totalRevenue = data.customers()
                .stream()
                .flatMap(customer -> customer.orders().stream())
                .map(CustomerDataTools.OrderRecord::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageOrderValue =
                totalOrders == 0
                        ? BigDecimal.ZERO
                        : totalRevenue.divide(
                                BigDecimal.valueOf(totalOrders),
                            2,
                                java.math.RoundingMode.HALF_UP
                        );

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
                averageOrderValue
        );

        return new Report(
                ReportType.ANALYTICAL,
                false,
                data.customerCount(),
                content
        );
    }

    private Report generateRedactedReport(CustomerDataTools.CustomerDataSet data) {

        StringBuilder content = new StringBuilder();

        content.append("""
                Customer Detail Report
                ======================

                Direct customer identifiers have been redacted.

                """);

        for (CustomerDataTools.CustomerRecord customer : data.customers()) {

            content.append("Customer ID: [REDACTED]\n");
            content.append("Customer Name: [REDACTED]\n");
            content.append("Email: [REDACTED]\n");
            content.append("City: ")
                    .append(customer.city())
                    .append("\n");
            content.append("Orders: ")
                    .append(customer.orders().size())
                    .append("\n\n");
        }

        return new Report(
                ReportType.REDACTED_DETAIL,
                true,
                data.customerCount(),
                content.toString()
        );
    }

    public enum ReportType {
        ANALYTICAL,
        REDACTED_DETAIL
    }

    public record Report(
            ReportType type,
            boolean redacted,
            int customerCount,
            String content
    ) {
    }
}