package com.agentops.guardian.governance.audit.dto;

import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.workflow.WorkflowType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowTrajectoryResponse(
        String workflowId,
        UUID conversationId,
        String agentName,
        WorkflowType workflowType,
        Instant startedAt,
        Instant lastEventAt,
        String currentNodeId,
        WorkflowState currentState,
        long eventCount,
        List<WorkflowTrajectoryEvent> events
) {
    public WorkflowTrajectoryResponse {
        events = List.copyOf(events);
    }
}