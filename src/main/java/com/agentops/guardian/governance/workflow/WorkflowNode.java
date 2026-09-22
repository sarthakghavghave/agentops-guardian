package com.agentops.guardian.governance.workflow;

public record WorkflowNode(String id, WorkflowCapability capability) {

    public static final String START_NODE_ID = "START";

    public WorkflowNode(String id) {
        this(id, null);
    }

    public WorkflowNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Workflow node id is required.");
        }

        if (capability == null && !START_NODE_ID.equals(id)) {
            throw new IllegalArgumentException("Workflow node capability is required for non-control nodes.");
        }
    }
}