package com.agentops.guardian.tool;

import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowDefinition;
import com.agentops.guardian.governance.workflow.WorkflowGraph;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportToolsTest {

    @Test
    void recordsRawToAnalyticalTransformation() {
        WorkflowContext workflow = workflowWithRawData();
        ReportTools tools = toolsFor(workflow);

        tools.generateReport(ReportTools.ReportType.ANALYTICAL);

        assertEquals(DataClassification.RAW_CUSTOMER_DATA,
                workflow.getLastTransformation().sourceClassification());
        assertEquals(DataClassification.ANALYTICAL,
                workflow.getLastTransformation().resultClassification());
        assertEquals("ANALYTICAL", workflow.getLastTransformation().transformationType());
    }

    @Test
    void recordsRawToRedactedDetailTransformation() {
        WorkflowContext workflow = workflowWithRawData();
        ReportTools tools = toolsFor(workflow);

        tools.generateReport(ReportTools.ReportType.REDACTED_DETAIL);

        assertEquals(DataClassification.RAW_CUSTOMER_DATA,
                workflow.getLastTransformation().sourceClassification());
        assertEquals(DataClassification.REDACTED_DETAIL,
                workflow.getLastTransformation().resultClassification());
        assertEquals("REDACTED_DETAIL", workflow.getLastTransformation().transformationType());
    }

    @Test
    void failsWithoutKnownSourceClassification() {
        WorkflowContext workflow = workflow();
        ReportTools tools = toolsFor(workflow);

        assertThrows(
                IllegalStateException.class,
                () -> tools.generateReport(ReportTools.ReportType.ANALYTICAL)
        );
        assertNull(workflow.getLastTransformation());
        assertNull(workflow.getCurrentDataClassification());
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

    private ReportTools toolsFor(WorkflowContext workflow) {
        WorkflowContextManager workflowContextManager = mock(WorkflowContextManager.class);
        when(workflowContextManager.current()).thenReturn(workflow);
        ToolDataStore toolDataStore = new ToolDataStore(workflowContextManager);
        toolDataStore.storeCustomerData(new CustomerDataTools.CustomerDataSet("Test City", 0, List.of()));
        return new ReportTools(toolDataStore, workflowContextManager);
    }
}