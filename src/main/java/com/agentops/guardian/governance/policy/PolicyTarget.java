package com.agentops.guardian.governance.policy;

import com.agentops.guardian.governance.workflow.WorkflowCapability;

public record PolicyTarget(String toolName, WorkflowCapability capability) {

    public PolicyTarget {
        if (toolName != null && toolName.isBlank()) {
            throw new IllegalArgumentException("Tool name must not be blank.");
        }
        if (toolName == null && capability == null) {
            throw new IllegalArgumentException("A tool name or workflow capability is required.");
        }
    }
}
