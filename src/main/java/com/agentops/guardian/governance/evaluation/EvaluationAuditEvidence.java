package com.agentops.guardian.governance.evaluation;

import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.audit.AuditEventEntity;
import com.agentops.guardian.governance.audit.AuditEventType;
import com.agentops.guardian.governance.intervention.GovernanceInterventionStatus;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.risk.GovernanceIntervention;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;

import java.time.Instant;

public record EvaluationAuditEvidence(
        Long auditEventId,
        String workflowId,
        WorkflowType workflowType,
        String toolCallId,
        String toolName,
        WorkflowCapability capability,
        Instant timestamp,
        AuditEventType eventType,
        GovernanceDecision.DecisionType policyDecision,
        String policyId,
        AuditEvent.ExecutionStatus executionStatus,
        GovernanceIntervention intervention,
        String interventionId,
        GovernanceInterventionStatus interventionStatus
) {
    public static EvaluationAuditEvidence from(AuditEventEntity event) {
        return new EvaluationAuditEvidence(
                event.getId(),
                event.getWorkflowId(),
                event.getWorkflowType(),
                event.getToolCallId(),
                event.getToolName(),
                event.getCapability(),
                event.getTimestamp(),
                event.getEventType(),
                event.getPolicyDecision(),
                event.getPolicyId(),
                event.getExecutionStatus(),
                event.getIntervention(),
                event.getInterventionId(),
                event.getInterventionStatus()
        );
    }
}
