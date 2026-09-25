package com.agentops.guardian.governance.audit;

import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.workflow.WorkflowCapability;

public record WorkflowNodeSnapshot(
        String nodeId,
        WorkflowState state,
        WorkflowCapability capability
) {
    public WorkflowNodeSnapshot {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("Workflow node id is required");
        }
        if (state == null) {
            throw new IllegalArgumentException("Workflow state is required");
        }
    }
}
