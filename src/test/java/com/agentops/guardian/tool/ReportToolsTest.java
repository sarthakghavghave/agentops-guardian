package com.agentops.guardian.tool;

import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowDefinition;
import com.agentops.guardian.governance.workflow.WorkflowGraph;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportToolsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storesAnalyticalReportAsReadablePdfAndRecordsTransformation() throws IOException {
        WorkflowContext workflow = workflowWithRawData();
        CustomerDataTools.CustomerDataSet customerData = new CustomerDataTools.CustomerDataSet(
                "Test City",
                1,
                List.of(new CustomerDataTools.CustomerRecord(
                        "customer-id",
                        "Private Customer",
                        "private@example.test",
                        "Test City",
                        List.of(new CustomerDataTools.OrderRecord(
                                "order-id",
                                LocalDateTime.parse("2026-01-01T12:00:00"),
                                "COMPLETED",
                                new BigDecimal("12345.67"),
                                List.of()
                        ), new CustomerDataTools.OrderRecord(
                                "order-id-2",
                                LocalDateTime.parse("2026-01-02T12:00:00"),
                                "COMPLETED",
                                new BigDecimal("300.00"),
                                List.of()
                        ))
                ))
        );
        ReportTools tools = toolsFor(workflow, tempDirectory, customerData);
        tools.beginExecution();

        ReportTools.Report report = tools.generateReport(ReportTools.ReportType.ANALYTICAL);

        assertEquals(ReportTools.ReportType.ANALYTICAL, report.type());
        assertTrue(report.fileReference().endsWith(".pdf"));
        assertTrue(Files.isRegularFile(Path.of(report.fileReference())));
        assertTrue(report.fileSizeBytes() > 0);
        try (PDDocument document = Loader.loadPDF(Path.of(report.fileReference()).toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Customer Analytics Report"));
            assertTrue(text.contains("CUSTOMER SEGMENT"));
            assertTrue(text.contains("Test City"));
            assertTrue(text.contains("CUSTOMERS ANALYZED"));
            assertTrue(text.contains("TOTAL ORDERS"));
            assertTrue(text.contains("1"));
            assertTrue(text.contains("2"));
            assertTrue(text.contains("12,645.67"));
            assertTrue(text.contains("6,322.84"));
            assertTrue(text.contains("currency."));
            assertTrue(text.contains("AgentOps Guardian"));
            assertTrue(text.contains("Generated " + LocalDateTime.now().toLocalDate()));
            assertTrue(text.contains("Page 1 of 1"));
            assertFalse(text.contains("Private Customer"));
            assertFalse(text.contains("private@example.test"));
        }
        assertEquals(DataClassification.ANALYTICAL, workflow.getCurrentDataClassification());
        assertEquals(DataClassification.RAW_CUSTOMER_DATA,
                workflow.getLastTransformation().sourceClassification());
        assertEquals(DataClassification.ANALYTICAL,
                workflow.getLastTransformation().resultClassification());
        assertEquals("ANALYTICAL", workflow.getLastTransformation().transformationType());
        assertEquals(List.of(report), tools.executionResults());
        tools.clearExecutionResult();
        assertEquals(List.of(), tools.executionResults());
    }

    @Test
    void preservesExistingRedactedDetailTransformation() {
        WorkflowContext workflow = workflowWithRawData();
        ReportTools tools = toolsFor(workflow, tempDirectory);

        ReportTools.Report report = tools.generateReport(ReportTools.ReportType.REDACTED_DETAIL);

        assertEquals(ReportTools.ReportType.REDACTED_DETAIL, report.type());
        assertEquals(DataClassification.REDACTED_DETAIL, workflow.getCurrentDataClassification());
        assertEquals(DataClassification.RAW_CUSTOMER_DATA,
                workflow.getLastTransformation().sourceClassification());
        assertEquals(DataClassification.REDACTED_DETAIL,
                workflow.getLastTransformation().resultClassification());
        assertEquals("REDACTED_DETAIL", workflow.getLastTransformation().transformationType());
    }

    @Test
    void failsWithoutKnownSourceClassification() {
        WorkflowContext workflow = workflow();
        ReportTools tools = toolsFor(workflow, tempDirectory);
        tools.beginExecution();

        assertThrows(
                IllegalStateException.class,
                () -> tools.generateReport(ReportTools.ReportType.ANALYTICAL)
        );
        assertNull(workflow.getLastTransformation());
        assertNull(workflow.getCurrentDataClassification());
        assertEquals(List.of(), tools.executionResults());
        tools.clearExecutionResult();
    }

    @Test
    void failedPdfWriteDoesNotConfirmReportGeneration() throws IOException {
        WorkflowContext workflow = workflowWithRawData();
        Path blockedDirectory = tempDirectory.resolve("not-a-directory");
        Files.writeString(blockedDirectory, "block directory creation");
        ReportTools tools = toolsFor(workflow, blockedDirectory);
        tools.beginExecution();

        assertThrows(
                java.io.UncheckedIOException.class,
                () -> tools.generateReport(ReportTools.ReportType.ANALYTICAL)
        );
        assertEquals(List.of(), tools.executionResults());
        tools.clearExecutionResult();
    }

    @Test
    void rejectsReportStorageInsideSourceTree() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReportFileStorageService(Path.of("src", "main", "resources", "reports").toString())
        );
    }

    private WorkflowContext workflowWithRawData() {
        WorkflowContext workflow = workflow();
        workflow.markDataAcquired(DataClassification.RAW_CUSTOMER_DATA);
        return workflow;
    }

    private WorkflowContext workflow() {
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.CUSTOMER_REPORTING,
                Set.of(
                        WorkflowCapability.READ_CUSTOMER_DATA,
                        WorkflowCapability.GENERATE_REPORT,
                        WorkflowCapability.SEND_EMAIL
                ),
                WorkflowGraph.customerReportingGraph()
        );
        return new WorkflowContext("ReportToolsTestAgent", WorkflowType.CUSTOMER_REPORTING, "Test report", definition);
    }

    private ReportTools toolsFor(WorkflowContext workflow, Path reportDirectory) {
        return toolsFor(
                workflow,
                reportDirectory,
                new CustomerDataTools.CustomerDataSet("Test City", 0, List.of())
        );
    }

    private ReportTools toolsFor(
            WorkflowContext workflow,
            Path reportDirectory,
            CustomerDataTools.CustomerDataSet customerData
    ) {
        WorkflowContextManager workflowContextManager = mock(WorkflowContextManager.class);
        when(workflowContextManager.current()).thenReturn(workflow);
        ToolDataStore toolDataStore = new ToolDataStore(workflowContextManager);
        toolDataStore.storeCustomerData(customerData);
        return new ReportTools(
                toolDataStore,
                workflowContextManager,
                new ReportFileStorageService(reportDirectory.toString())
        );
    }
}