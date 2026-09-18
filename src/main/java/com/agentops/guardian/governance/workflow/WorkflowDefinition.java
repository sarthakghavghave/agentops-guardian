package com.agentops.guardian.governance.workflow;

import java.util.Set;

public record WorkflowDefinition(
        WorkflowType type,
        Set<WorkflowCapability> capabilities,
        WorkflowGraph graph
) {

    public WorkflowDefinition {
        if (type == null) {
            throw new IllegalArgumentException("Workflow type is required.");
        }

        if (capabilities == null || capabilities.isEmpty()) {
            throw new IllegalArgumentException("Workflow capabilities are required.");
        }

        if (graph == null) {
            throw new IllegalArgumentException("Workflow graph is required.");
        }

        capabilities = Set.copyOf(capabilities);
    }

    public WorkflowGraph getGraph() {
        return graph;
    }

    public boolean allows(WorkflowCapability capability) {
        return capabilities.contains(capability);
    }
}