package com.agentops.guardian.governance.audit.dto;

import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.workflow.WorkflowType;

import java.time.Instant;

public record WorkflowSummaryResponse(
        String workflowId,
        String agentName,
        WorkflowType workflowType,
        Instant startedAt,
        Instant lastEventAt,
        String currentNodeId,
        WorkflowState currentState,
        long eventCount
) {
}