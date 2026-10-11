package com.agentops.guardian.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.DataTransformation;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ReportTools {

        private final ToolDataStore toolDataStore;
        private final WorkflowContextManager workflowContextManager;
        private final ReportFileStorageService reportFileStorageService;
        private final ThreadLocal<List<Report>> executionResults = new ThreadLocal<>();

        public ReportTools(
                        ToolDataStore toolDataStore,
                        WorkflowContextManager workflowContextManager,
                        ReportFileStorageService reportFileStorageService) {
                this.toolDataStore = toolDataStore;
                this.workflowContextManager = workflowContextManager;
                this.reportFileStorageService = reportFileStorageService;
        }

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

                WorkflowContext workflowContext = workflowContextManager.current();
                DataClassification sourceClassification = workflowContext.getCurrentDataClassification();
                if (sourceClassification == null) {
                        throw new IllegalStateException(
                                        "Cannot generate a report without a known source data classification.");
                }

                workflowContext.recordTransformation(
                                new DataTransformation(
                                                sourceClassification,
                                                resultClassification,
                                                reportType.name(),
                                                Instant.now()));

                if (reportType == ReportType.ANALYTICAL) {
                        ReportFileStorageService.StoredReport storedReport =
                                        reportFileStorageService.storePdf(createPdf(report));
                        report = report.withStoredFile(storedReport.reference(), storedReport.sizeBytes());
                }

                List<Report> results = executionResults.get();
                if (results != null) {
                        results.add(report);
                }
                return report;
        }

        public void beginExecution() {
                executionResults.set(new ArrayList<>());
        }

        public void clearExecutionResult() {
                executionResults.remove();
        }

        public List<Report> executionResults() {
                List<Report> results = executionResults.get();
                return results == null ? List.of() : List.copyOf(results);
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
                                content,
                                null,
                                0);
        }

        private byte[] createPdf(Report report) {
                try (PDDocument document = new PDDocument();
                                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        PDPage page = new PDPage(PDRectangle.LETTER);
                        document.addPage(page);

                        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                                float y = page.getMediaBox().getHeight() - 54;
                                String[] lines = report.content().split("\\R", -1);
                                for (int index = 0; index < lines.length; index++) {
                                        String line = toPdfText(lines[index]);
                                        if (line.isBlank()) {
                                                y -= 10;
                                                continue;
                                        }
                                        PDType1Font font = index == 0
                                                        ? new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
                                                        : new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                                        float fontSize = index == 0 ? 18 : 11;
                                        contentStream.beginText();
                                        contentStream.setFont(font, fontSize);
                                        contentStream.newLineAtOffset(54, y);
                                        contentStream.showText(line);
                                        contentStream.endText();
                                        y -= index == 0 ? 28 : 18;
                                }
                        }

                        document.save(output);
                        return output.toByteArray();
                } catch (IOException exception) {
                        throw new UncheckedIOException("Unable to generate analytical report PDF.", exception);
                }
        }

        private String toPdfText(String value) {
                return value.codePoints()
                                .map(codePoint -> codePoint >= 32 && codePoint <= 255 ? codePoint : '?')
                                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                                .toString();
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
                        String content,
                        String fileReference,
                        long fileSizeBytes) {

                public Report(ReportType type, boolean redacted, int customerCount, String content) {
                        this(type, redacted, customerCount, content, null, 0);
                }

                private Report withStoredFile(String reference, long sizeBytes) {
                        return new Report(type, redacted, customerCount, content, reference, sizeBytes);
                }
        }
}