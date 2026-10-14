package com.agentops.guardian.governance.audit.live;

import com.agentops.guardian.governance.audit.AuditEventEntity;
import com.agentops.guardian.governance.audit.dto.WorkflowTrajectoryEvent;
import com.agentops.guardian.governance.workflow.WorkflowType;

import java.util.UUID;

public record GovernanceEventStreamPayload(
        Long eventId,
        UUID conversationId,
        String workflowId,
        String agentName,
        WorkflowType workflowType,
        WorkflowTrajectoryEvent event
) {
    public static GovernanceEventStreamPayload from(
            AuditEventEntity entity,
            WorkflowTrajectoryEvent event
    ) {
        return new GovernanceEventStreamPayload(
                entity.getId(),
                entity.getConversationId(),
                entity.getWorkflowId(),
                entity.getAgentName(),
                entity.getWorkflowType(),
                event
        );
    }
}
