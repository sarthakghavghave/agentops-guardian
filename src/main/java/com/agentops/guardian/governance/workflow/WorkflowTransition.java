package com.agentops.guardian.governance.workflow;

public record WorkflowTransition(String fromNodeId, String toNodeId) {

    public WorkflowTransition {
        if (fromNodeId == null || fromNodeId.isBlank()) {
            throw new IllegalArgumentException("Source node id is required.");
        }

        if (toNodeId == null || toNodeId.isBlank()) {
            throw new IllegalArgumentException("Target node id is required.");
        }
    }
}