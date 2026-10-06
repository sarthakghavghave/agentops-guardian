package com.agentops.guardian.governance.audit.live;

import com.agentops.guardian.governance.audit.AuditEventEntity;
import com.agentops.guardian.governance.audit.dto.WorkflowTrajectoryEvent;
import com.agentops.guardian.governance.workflow.WorkflowType;

public record GovernanceEventStreamPayload(
        Long eventId,
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
                entity.getWorkflowId(),
                entity.getAgentName(),
                entity.getWorkflowType(),
                event
        );
    }
}
