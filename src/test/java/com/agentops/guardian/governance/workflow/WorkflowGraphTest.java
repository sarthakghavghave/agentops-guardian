package com.agentops.guardian.governance.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowGraphTest {

    @Test
    void allowsDefinedTransition() {
        WorkflowGraph graph = new WorkflowGraph(
                List.of(
                        new WorkflowNode("CUSTOMER_DATA", WorkflowCapability.READ_CUSTOMER_DATA),
                        new WorkflowNode("REPORT", WorkflowCapability.GENERATE_REPORT)
                ),
                Set.of(
                        new WorkflowTransition("CUSTOMER_DATA", "REPORT")
                )
        );

        assertTrue(
                graph.canTransition("CUSTOMER_DATA", "REPORT"))
        ;
    }

    @Test
    void rejectsUndefinedTransition() {
        WorkflowGraph graph = new WorkflowGraph(
                List.of(
                        new WorkflowNode("CUSTOMER_DATA", WorkflowCapability.READ_CUSTOMER_DATA),
                        new WorkflowNode("REPORT", WorkflowCapability.GENERATE_REPORT)
                ),
                Set.of(
                        new WorkflowTransition("CUSTOMER_DATA", "REPORT")
                )
        );

        assertFalse(
                graph.canTransition("REPORT", "CUSTOMER_DATA")
        );
    }

    @Test
    void rejectsTransitionToUnknownNode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowGraph(
                        List.of(
                                new WorkflowNode("CUSTOMER_DATA", WorkflowCapability.READ_CUSTOMER_DATA)
                        ),
                        Set.of(
                                new WorkflowTransition("CUSTOMER_DATA", "UNKNOWN")
                        )
                )
        );
    }

    @Test
    void rejectsTransitionFromUnknownSourceNode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowGraph(
                        List.of(
                                new WorkflowNode("REPORT", WorkflowCapability.GENERATE_REPORT)
                        ),
                        Set.of(
                                new WorkflowTransition("UNKNOWN", "REPORT")
                        )
                )
        );
    }

    @Test
    void shouldRepresentCustomerReportingWorkflow() {
        WorkflowGraph graph = WorkflowGraph.customerReportingGraph();

        assertTrue(graph.containsNode("START"));
        assertTrue(graph.containsNode("CUSTOMER_DATA"));
        assertTrue(graph.containsNode("REPORT"));
        assertFalse(graph.containsNode("DELIVERY"));
        assertFalse(graph.containsNode("UNKNOWN"));

        assertEquals(3, graph.getNodes().size());
        assertEquals(2, graph.getTransitions().size());

        // Valid transitions: START -> CUSTOMER_DATA -> REPORT
        assertTrue(graph.canTransition("START", "CUSTOMER_DATA"));
        assertTrue(graph.canTransition("CUSTOMER_DATA", "REPORT"));

        // Invalid transitions
        assertFalse(graph.canTransition("START", "REPORT"));
        assertFalse(graph.canTransition("START", "DELIVERY"));
        assertFalse(graph.canTransition("CUSTOMER_DATA", "DELIVERY"));
        assertFalse(graph.canTransition("REPORT", "CUSTOMER_DATA"));
    }

    @Test
    void shouldRepresentEmailCommunicationAsAnEmailOnlyWorkflow() {
        WorkflowGraph graph = WorkflowGraph.emailCommunicationGraph();

        assertEquals(2, graph.getNodes().size());
        assertEquals(1, graph.getTransitions().size());
        assertTrue(graph.canTransition("START", "DELIVERY"));
        assertEquals(WorkflowCapability.SEND_EMAIL, graph.getNode("DELIVERY").capability());
        assertFalse(graph.containsNode("CUSTOMER_DATA"));
        assertFalse(graph.containsNode("REPORT"));
    }

    @Test
    void shouldBeImmutableAfterConstruction() {
        WorkflowGraph graph = WorkflowGraph.customerReportingGraph();

        assertThrows(
                UnsupportedOperationException.class,
                () -> graph.getNodes().add(new WorkflowNode("NEW", WorkflowCapability.READ_ORDER))
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> graph.getTransitions().add(new WorkflowTransition("START", "REPORT"))
        );
    }

    @Test
    void shouldRejectDuplicateNodeIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowGraph(
                        List.of(
                                new WorkflowNode("CUSTOMER_DATA", WorkflowCapability.READ_CUSTOMER_DATA),
                                new WorkflowNode("CUSTOMER_DATA", WorkflowCapability.READ_CUSTOMER_DATA)
                        ),
                        Set.of()
                )
        );
    }

    @Test
    void shouldRejectNullNodesOrTransitions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowGraph(null, Set.of())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowGraph(List.of(), Set.of())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowGraph(List.of(new WorkflowNode("START")), null)
        );

        List<WorkflowNode> nodesWithNull = new java.util.ArrayList<>();
        nodesWithNull.add(null);
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowGraph(nodesWithNull, Set.of())
        );
    }

    @Test
    void shouldSafelyHandleNullOrBlankInContainsNodeAndCanTransition() {
        WorkflowGraph graph = WorkflowGraph.customerReportingGraph();

        assertFalse(graph.containsNode(null));
        assertFalse(graph.containsNode(""));
        assertFalse(graph.containsNode("   "));

        assertFalse(graph.canTransition(null, "CUSTOMER_DATA"));
        assertFalse(graph.canTransition("START", null));
        assertFalse(graph.canTransition(null, null));
        assertFalse(graph.canTransition("", "CUSTOMER_DATA"));
        assertFalse(graph.canTransition("START", "   "));
    }

    @Test
    void shouldSupportBuilderForGenericWorkflows() {
        WorkflowGraph graph = WorkflowGraph.builder()
                .addNode(WorkflowGraph.START_NODE_ID)
                .addNode("STEP_A", WorkflowCapability.READ_PRODUCT)
                .addNode("STEP_B", WorkflowCapability.RETURN_ORDER)
                .addTransition(WorkflowGraph.START_NODE_ID, "STEP_A")
                .addTransition("STEP_A", "STEP_B")
                .build();

        assertEquals(3, graph.getNodes().size());
        assertEquals(2, graph.getTransitions().size());
        assertTrue(graph.canTransition("START", "STEP_A"));
        assertTrue(graph.canTransition("STEP_A", "STEP_B"));
        assertFalse(graph.canTransition("START", "STEP_B"));
    }

    @Test
    void shouldEnforceControlNodeNullCapabilityConstraint() {
        // START node may have null capability
        assertDoesNotThrow(() -> new WorkflowNode("START"));
        assertDoesNotThrow(() -> new WorkflowNode("START", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowNode("start", null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowNode(" START ", null)
        );

        // Non-control nodes must require capability
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowNode("CUSTOMER_DATA", null)
        );
    }
}