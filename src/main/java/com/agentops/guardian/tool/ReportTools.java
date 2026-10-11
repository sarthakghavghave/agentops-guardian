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
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
                                data.city(),
                                totalOrders,
                                totalRevenue,
                                averageOrderValue,
                                null,
                                0);
        }

        private byte[] createPdf(Report report) {
                try (PDDocument document = new PDDocument();
                                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        PDPage page = new PDPage(PDRectangle.LETTER);
                        document.addPage(page);

                        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                                drawAnalyticalReport(contentStream, page, report);
                        }

                        document.save(output);
                        return output.toByteArray();
                } catch (IOException exception) {
                        throw new UncheckedIOException("Unable to generate analytical report PDF.", exception);
                }
        }

        private void drawAnalyticalReport(
                        PDPageContentStream stream,
                        PDPage page,
                        Report report) throws IOException {
                float pageWidth = page.getMediaBox().getWidth();
                float pageHeight = page.getMediaBox().getHeight();
                float margin = 48;
                float contentWidth = pageWidth - margin * 2;
                Color navy = new Color(24, 46, 78);
                Color darkGray = new Color(55, 65, 81);
                Color midGray = new Color(105, 115, 128);
                Color lightGray = new Color(244, 246, 249);
                Color borderGray = new Color(224, 228, 233);
                PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

                stream.setNonStrokingColor(navy);
                stream.addRect(0, pageHeight - 158, pageWidth, 158);
                stream.fill();
                drawText(stream, bold, 25, margin, pageHeight - 62, "Customer Analytics Report", Color.WHITE);
                drawText(stream, bold, 9, margin, pageHeight - 94, "CUSTOMER SEGMENT", new Color(205, 216, 230));
                drawText(stream, regular, 15, margin, pageHeight - 119, report.customerSegment(), Color.WHITE);

                drawText(stream, bold, 13, margin, pageHeight - 194, "Performance Snapshot", darkGray);
                stream.setStrokingColor(borderGray);
                stream.setLineWidth(0.8f);
                stream.moveTo(margin, pageHeight - 205);
                stream.lineTo(pageWidth - margin, pageHeight - 205);
                stream.stroke();

                float gap = 16;
                float cardWidth = (contentWidth - gap) / 2;
                float cardHeight = 102;
                float firstRowY = pageHeight - 322;
                float secondRowY = firstRowY - cardHeight - 14;
                drawMetricCard(
                                stream, margin, firstRowY, cardWidth, cardHeight,
                                "CUSTOMERS ANALYZED", formatCount(report.customerCount()), false,
                                lightGray, borderGray, midGray, navy, bold);
                drawMetricCard(
                                stream, margin + cardWidth + gap, firstRowY, cardWidth, cardHeight,
                                "TOTAL ORDERS", formatCount(report.totalOrders()), false,
                                lightGray, borderGray, midGray, navy, bold);
                drawMetricCard(
                                stream, margin, secondRowY, cardWidth, cardHeight,
                                "TOTAL ORDER VALUE", formatAmount(report.totalOrderValue()), true,
                                lightGray, borderGray, midGray, navy, bold);
                drawMetricCard(
                                stream, margin + cardWidth + gap, secondRowY, cardWidth, cardHeight,
                                "AVERAGE ORDER VALUE", formatAmount(report.averageOrderValue()), true,
                                lightGray, borderGray, midGray, navy, bold);

                float noteY = secondRowY - 27;
                drawText(
                                stream,
                                regular,
                                9,
                                margin,
                                noteY,
                                "Monetary values use source-data units; the source does not specify a currency.",
                                midGray);
                drawText(
                                stream,
                                regular,
                                10,
                                margin,
                                noteY - 24,
                                "This report contains aggregated business information.",
                                darkGray);

                stream.setStrokingColor(borderGray);
                stream.setLineWidth(0.8f);
                stream.moveTo(margin, 66);
                stream.lineTo(pageWidth - margin, 66);
                stream.stroke();
                drawText(stream, bold, 9, margin, 45, "AgentOps Guardian", darkGray);
                drawText(
                                stream,
                                regular,
                                9,
                                pageWidth - margin - 145,
                                45,
                                "Generated " + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                                                + "  |  Page 1 of 1",
                                midGray);
        }

        private void drawMetricCard(
                        PDPageContentStream stream,
                        float x,
                        float y,
                        float width,
                        float height,
                        String label,
                        String value,
                        boolean monetary,
                        Color background,
                        Color border,
                        Color labelColor,
                        Color valueColor,
                        PDType1Font bold) throws IOException {
                stream.setNonStrokingColor(background);
                stream.setStrokingColor(border);
                stream.setLineWidth(0.7f);
                stream.addRect(x, y, width, height);
                stream.fillAndStroke();

                drawText(stream, bold, 9, x + 16, y + height - 25, label, labelColor);
                drawText(stream, bold, monetary ? 21 : 24, x + 16, y + 39, value, valueColor);
        }

        private void drawText(
                        PDPageContentStream stream,
                        PDType1Font font,
                        float fontSize,
                        float x,
                        float y,
                        String value,
                        Color color) throws IOException {
                stream.beginText();
                stream.setNonStrokingColor(color);
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(x, y);
                stream.showText(toPdfText(value));
                stream.endText();
        }

        private String formatCount(int value) {
                return NumberFormat.getIntegerInstance(Locale.US).format(value);
        }

        private String formatAmount(BigDecimal value) {
                NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
                format.setGroupingUsed(true);
                format.setMinimumFractionDigits(2);
                format.setMaximumFractionDigits(2);
                format.setRoundingMode(RoundingMode.HALF_UP);
                return format.format(value);
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
                        String customerSegment,
                        int totalOrders,
                        BigDecimal totalOrderValue,
                        BigDecimal averageOrderValue,
                        String fileReference,
                        long fileSizeBytes) {

                public Report(ReportType type, boolean redacted, int customerCount, String content) {
                        this(type, redacted, customerCount, content, null, 0, null, null, null, 0);
                }

                public Report(
                                ReportType type,
                                boolean redacted,
                                int customerCount,
                                String content,
                                String fileReference,
                                long fileSizeBytes) {
                        this(type, redacted, customerCount, content, null, 0, null, null,
                                        fileReference, fileSizeBytes);
                }

                private Report withStoredFile(String reference, long sizeBytes) {
                        return new Report(
                                        type,
                                        redacted,
                                        customerCount,
                                        content,
                                        customerSegment,
                                        totalOrders,
                                        totalOrderValue,
                                        averageOrderValue,
                                        reference,
                                        sizeBytes);
                }
        }
}