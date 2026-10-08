package com.agentops.guardian.governance.context;

import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.model.WorkflowAction;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowDefinition;
import com.agentops.guardian.governance.workflow.WorkflowDefinitionRegistry;
import com.agentops.guardian.governance.workflow.WorkflowGraph;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowContextTest {

    private WorkflowContext context;

    @BeforeEach
    void setUp() {
        WorkflowDefinition workflowDefinition = mock(WorkflowDefinition.class);
        context = new WorkflowContext(
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "Generate a customer report for New Roberttown",
                workflowDefinition
        );
    }

    @Test
    void shouldStartWorkflowInRunningState() {
        assertNotNull(context.getWorkflowId());
        assertEquals("CustomerReportAgent", context.getAgentName());
        assertEquals(WorkflowType.CUSTOMER_REPORTING, context.getWorkflowType());
        assertEquals("Generate a customer report for New Roberttown", context.getGoal());
        assertEquals(WorkflowStatus.RUNNING, context.getStatus());
        assertEquals(WorkflowState.STARTED, context.getCurrentState());
        assertEquals("START", context.getCurrentNodeId());
        assertNotNull(context.getStartedAt());
        assertNull(context.getCompletedAt());
    }

    @Test
    void shouldRecordToolCall() {
        ToolCallEvent event = new ToolCallEvent(
                "",
                "getCustomerData",
                "{\"city\":\"New Roberttown\",\"maxCustomers\":100}",
                "function",
                Instant.now()
        );

        context.addToolCall(event);

        assertEquals(1, context.getToolCalls().size());
        assertSame(event, context.getToolCalls().get(0));
    }

    @Test
    void shouldReturnImmutableToolCallList() {
        ToolCallEvent event = new ToolCallEvent(
                "",
                "getCustomerData",
                "{\"city\":\"New Roberttown\",\"maxCustomers\":100}",
                "function",
                Instant.now()
        );

        context.addToolCall(event);

        assertThrows(
                UnsupportedOperationException.class,
                () -> context.getToolCalls().add(event)
        );
    }

    @Test
    void shouldStoreAndRetrieveWorkflowData() {
        context.putData("customerCount", 10);

        assertEquals(10, context.getData("customerCount"));
    }

    @Test
    void shouldRemoveWorkflowData() {
        context.putData("customerCount", 10);

        context.removeData("customerCount");

        assertNull(context.getData("customerCount"));
    }

    @Test
    void shouldClearWorkflowData() {
        context.putData("customerCount", 10);
        context.putData("reportType", "ANALYTICAL");

        context.clearData();

        assertNull(context.getData("customerCount"));
        assertNull(context.getData("reportType"));
    }

    @Test
    void shouldTrackDataClassification() {
        context.markDataAcquired(DataClassification.RAW_CUSTOMER_DATA);

        assertEquals(
                DataClassification.RAW_CUSTOMER_DATA,
                context.getCurrentDataClassification()
        );
    }

    @Test
    void shouldCompleteWorkflow() {
        context.complete();

        assertEquals(WorkflowStatus.COMPLETED, context.getStatus());
        assertNotNull(context.getCompletedAt());
    }

    @Test
    void shouldFailWorkflow() {
        context.fail();

        assertEquals(WorkflowStatus.FAILED, context.getStatus());
        assertNotNull(context.getCompletedAt());
    }

    @Test
    void shouldBlockWorkflow() {
        context.block();

        assertEquals(WorkflowStatus.BLOCKED, context.getStatus());
        assertNotNull(context.getCompletedAt());
    }

    @Test
    void shouldNotModifyCompletedWorkflow() {
        context.complete();

        assertThrows(
                IllegalStateException.class,
                () -> context.putData("key", "value")
        );
    }

    @Test
    void shouldNotAddToolCallAfterWorkflowCompletion() {
        context.complete();

        ToolCallEvent event = new ToolCallEvent(
                "",
                "getCustomerData",
                "{}",
                "function",
                Instant.now()
        );

        assertThrows(
                IllegalStateException.class,
                () -> context.addToolCall(event)
        );
    }

    @Test
    void shouldNotAddDataAfterWorkflowFailure() {
        context.fail();

        assertThrows(
                IllegalStateException.class,
                () -> context.putData("key", "value")
        );
    }

    @Test
    void shouldNotAddDataAfterWorkflowIsBlocked() {
        context.block();

        assertThrows(
                IllegalStateException.class,
                () -> context.putData("key", "value")
        );
    }

    @Test
    void shouldGenerateUniqueWorkflowIds() {
        WorkflowDefinition workflowDefinition = mock(WorkflowDefinition.class);

        WorkflowContext first = new WorkflowContext(
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "Generate customer report",
                workflowDefinition
        );

        WorkflowContext second = new WorkflowContext(
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "Generate customer report",
                workflowDefinition
        );

        assertNotEquals(first.getWorkflowId(), second.getWorkflowId());
    }

    @Test
    void shouldRejectNullToolCallEvent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> context.addToolCall(null)
        );
    }

    @Test
    void shouldRejectNullWorkflowAction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> context.addAction(null)
        );
    }

    @Test
    void shouldNotClearDataWhenWorkflowNotRunning() {
        context.putData("k", "v");
        context.complete();

        assertThrows(
                IllegalStateException.class,
                () -> context.clearData()
        );
    }

    @Test
    void shouldAdvanceWorkflowThroughValidGraphTransitions() {
        WorkflowGraph graph = WorkflowGraph.customerReportingGraph();
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.CUSTOMER_REPORTING,
                Set.of(
                        WorkflowCapability.READ_CUSTOMER_DATA,
                        WorkflowCapability.GENERATE_REPORT
                ),
                graph
        );

        WorkflowContext ctx = new WorkflowContext(
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "Generate report",
                definition
        );

        assertEquals("START", ctx.getCurrentNodeId());
        assertEquals(WorkflowState.STARTED, ctx.getCurrentState());

        ctx.advanceTo("CUSTOMER_DATA", WorkflowState.DATA_ACQUIRED);
        assertEquals("CUSTOMER_DATA", ctx.getCurrentNodeId());
        assertEquals(WorkflowState.DATA_ACQUIRED, ctx.getCurrentState());

        ctx.advanceTo("REPORT", WorkflowState.REPORT_GENERATED);
        assertEquals("REPORT", ctx.getCurrentNodeId());
        assertEquals(WorkflowState.REPORT_GENERATED, ctx.getCurrentState());
    }

    @Test
    void shouldAdvanceEmailWorkflowThroughItsOwnCapability() {
        WorkflowDefinition definition = new WorkflowDefinitionRegistry().get(WorkflowType.EMAIL_COMMUNICATION);
        WorkflowContext ctx = new WorkflowContext(
                "EmailAgent",
                WorkflowType.EMAIL_COMMUNICATION,
                "Send supplied report",
                definition
        );

        ctx.advanceAfterCapability(WorkflowCapability.SEND_EMAIL);

        assertEquals("DELIVERY", ctx.getCurrentNodeId());
        assertEquals(WorkflowState.DELIVERY_REQUESTED, ctx.getCurrentState());
    }

    @Test
    void shouldRejectInvalidWorkflowTransition() {
        WorkflowGraph graph = WorkflowGraph.customerReportingGraph();
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.CUSTOMER_REPORTING,
                Set.of(
                        WorkflowCapability.READ_CUSTOMER_DATA,
                        WorkflowCapability.GENERATE_REPORT
                ),
                graph
        );

        WorkflowContext ctx = new WorkflowContext(
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "Generate report",
                definition
        );

        // Attempt to skip straight from START to REPORT
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> ctx.advanceTo("REPORT", WorkflowState.REPORT_GENERATED)
        );
        assertTrue(ex.getMessage().contains("Invalid workflow transition from START to REPORT"));
    }

    @Test
    void shouldRejectTransitionToUnknownNode() {
        WorkflowGraph graph = WorkflowGraph.customerReportingGraph();
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.CUSTOMER_REPORTING,
                Set.of(
                        WorkflowCapability.READ_CUSTOMER_DATA,
                        WorkflowCapability.GENERATE_REPORT
                ),
                graph
        );

        WorkflowContext ctx = new WorkflowContext(
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "Generate report",
                definition
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ctx.advanceTo("NON_EXISTENT", WorkflowState.DATA_ACQUIRED)
        );
        assertTrue(ex.getMessage().contains("Unknown workflow node: NON_EXISTENT"));
    }

    @Test
    void shouldAdvanceAfterCapabilitySequence() {
        WorkflowGraph graph = WorkflowGraph.customerReportingGraph();
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.CUSTOMER_REPORTING,
                Set.of(
                        WorkflowCapability.READ_CUSTOMER_DATA,
                        WorkflowCapability.GENERATE_REPORT
                ),
                graph
        );

        WorkflowContext ctx = new WorkflowContext(
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "Generate report",
                definition
        );

        ctx.advanceAfterCapability(WorkflowCapability.READ_CUSTOMER_DATA);
        assertEquals("CUSTOMER_DATA", ctx.getCurrentNodeId());
        assertEquals(WorkflowState.DATA_ACQUIRED, ctx.getCurrentState());

        ctx.advanceAfterCapability(WorkflowCapability.GENERATE_REPORT);
        assertEquals("REPORT", ctx.getCurrentNodeId());
        assertEquals(WorkflowState.REPORT_GENERATED, ctx.getCurrentState());
    }

        @Test
        void shouldValidateCapabilityTransitionWithoutMutatingWorkflow() {
                WorkflowContext ctx = reportingContext();

                ctx.validateCapabilityTransition(WorkflowCapability.READ_CUSTOMER_DATA);

                assertEquals("START", ctx.getCurrentNodeId());
                assertEquals(WorkflowState.STARTED, ctx.getCurrentState());
        }

        @Test
        void shouldRejectInvalidCapabilityTransition() {
                WorkflowContext ctx = reportingContext();

                IllegalStateException exception = assertThrows(
                                IllegalStateException.class,
                                () -> ctx.validateCapabilityTransition(WorkflowCapability.GENERATE_REPORT)
                );

                                assertTrue(exception.getMessage().contains("Invalid workflow transition from START to REPORT"));
                assertEquals("START", ctx.getCurrentNodeId());
        }

        @Test
        void shouldRejectUnsupportedCapabilityTransition() {
                WorkflowDefinition definition = new WorkflowDefinition(
                                WorkflowType.CUSTOMER_REPORTING,
                                Set.of(WorkflowCapability.READ_CUSTOMER_DATA),
                                WorkflowGraph.customerReportingGraph()
                );
                WorkflowContext ctx = new WorkflowContext(
                                "CustomerReportAgent",
                                WorkflowType.CUSTOMER_REPORTING,
                                "Read customer data",
                                definition
                );

                assertThrows(
                                IllegalArgumentException.class,
                                () -> ctx.validateCapabilityTransition(WorkflowCapability.RETURN_ORDER)
                );
                assertEquals("START", ctx.getCurrentNodeId());
        }

        @Test
        void shouldAdvanceOnlyAfterCapabilityValidationSucceeds() {
                WorkflowContext ctx = reportingContext();

                ctx.advanceAfterCapability(WorkflowCapability.READ_CUSTOMER_DATA);

                assertEquals("CUSTOMER_DATA", ctx.getCurrentNodeId());
                assertEquals(WorkflowState.DATA_ACQUIRED, ctx.getCurrentState());
        }

    @Test
    void shouldRejectAdvanceAfterCapabilitySkippingSteps() {
        WorkflowGraph graph = WorkflowGraph.customerReportingGraph();
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.CUSTOMER_REPORTING,
                Set.of(
                        WorkflowCapability.READ_CUSTOMER_DATA,
                        WorkflowCapability.GENERATE_REPORT
                ),
                graph
        );

        WorkflowContext ctx = new WorkflowContext(
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "Generate report",
                definition
        );

        // Capability is in the definition, but START -> REPORT skips the customer-data step.
        assertThrows(
                IllegalStateException.class,
                () -> ctx.advanceAfterCapability(WorkflowCapability.GENERATE_REPORT)
        );
    }

    @Test
    void shouldRejectAdvanceAfterCapabilityWhenNotAllowedByDefinition() {
        WorkflowGraph graph = WorkflowGraph.customerReportingGraph();
        // Definition only allows READ_CUSTOMER_DATA
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.CUSTOMER_REPORTING,
                Set.of(WorkflowCapability.READ_CUSTOMER_DATA),
                graph
        );

        WorkflowContext ctx = new WorkflowContext(
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "Generate report",
                definition
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ctx.advanceAfterCapability(WorkflowCapability.RETURN_ORDER)
        );
    }

    @Test
    void shouldRejectAdvanceAfterCapabilityWhenNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> context.advanceAfterCapability(null)
        );
    }

    @Test
    void shouldRejectAdvanceWhenWorkflowNotRunning() {
        WorkflowGraph graph = WorkflowGraph.customerReportingGraph();
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.CUSTOMER_REPORTING,
                Set.of(WorkflowCapability.READ_CUSTOMER_DATA),
                graph
        );

        WorkflowContext ctx = new WorkflowContext(
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "Generate report",
                definition
        );

        ctx.complete();

        assertThrows(
                IllegalStateException.class,
                () -> ctx.advanceTo("CUSTOMER_DATA", WorkflowState.DATA_ACQUIRED)
        );

        assertThrows(
                IllegalStateException.class,
                () -> ctx.advanceAfterCapability(WorkflowCapability.READ_CUSTOMER_DATA)
        );
    }

    private WorkflowContext reportingContext() {
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.CUSTOMER_REPORTING,
                Set.of(
                        WorkflowCapability.READ_CUSTOMER_DATA,
                        WorkflowCapability.GENERATE_REPORT
                ),
                WorkflowGraph.customerReportingGraph()
        );

        return new WorkflowContext(
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "Generate a customer report",
                definition
        );
    }
}