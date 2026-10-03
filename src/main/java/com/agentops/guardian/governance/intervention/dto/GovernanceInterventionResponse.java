package com.agentops.guardian.governance.intervention.dto;

import com.agentops.guardian.governance.intervention.GovernanceIntervention;
import com.agentops.guardian.governance.intervention.GovernanceInterventionStatus;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;

import java.time.Instant;

public record GovernanceInterventionResponse(
        String interventionId,
        String workflowId,
        String toolCallId,
        String agentName,
        WorkflowType workflowType,
        WorkflowCapability capability,
        RiskLevel riskLevel,
        com.agentops.guardian.governance.risk.GovernanceIntervention intervention,
        GovernanceInterventionStatus status,
        String reason,
        Instant createdAt,
        Instant resolvedAt,
        String resolvedBy,
        String resolutionReason
) {

    public static GovernanceInterventionResponse from(GovernanceIntervention intervention) {
        return new GovernanceInterventionResponse(
                intervention.interventionId(),
                intervention.workflowId(),
                intervention.toolCallId(),
                intervention.agentName(),
                intervention.workflowType(),
                intervention.capability(),
                intervention.riskLevel(),
                intervention.intervention(),
                intervention.status(),
                intervention.reason(),
                intervention.createdAt(),
                intervention.resolvedAt(),
                intervention.resolvedBy(),
                intervention.resolutionReason()
        );
    }
}