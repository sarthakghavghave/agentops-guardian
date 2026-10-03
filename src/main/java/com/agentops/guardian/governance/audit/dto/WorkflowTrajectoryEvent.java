package com.agentops.guardian.governance.audit.dto;

import com.agentops.guardian.governance.audit.AuditEventEntity;
import com.agentops.guardian.governance.audit.AuditEventType;
import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.risk.GovernanceIntervention;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.workflow.WorkflowCapability;

import java.time.Instant;

public record WorkflowTrajectoryEvent(
        Instant timestamp,
        AuditEventType eventType,
        String toolCallId,
        String toolName,
        WorkflowCapability capability,
        Integer sequence,
        GovernanceDecision.DecisionType policyDecision,
        String policyId,
        String policyReason,
        com.agentops.guardian.governance.audit.AuditEvent.ExecutionStatus executionStatus,
        String errorType,
        String errorMessage,
        String nodeBeforeId,
        WorkflowState nodeBeforeState,
        WorkflowCapability nodeBeforeCapability,
        String nodeAfterId,
        WorkflowState nodeAfterState,
        WorkflowCapability nodeAfterCapability,
        WorkflowState stateBefore,
        WorkflowState stateAfter,
        DataClassification classificationBefore,
        DataClassification classificationAfter,
        String transformationType,
        RiskLevel riskLevel,
        GovernanceIntervention intervention,
        String riskReason
) {

    public static WorkflowTrajectoryEvent from(AuditEventEntity event) {
        return new WorkflowTrajectoryEvent(
                event.getTimestamp(),
                event.getEventType(),
                event.getToolCallId(),
                event.getToolName(),
                event.getCapability(),
                event.getSequence(),
                event.getPolicyDecision(),
                event.getPolicyId(),
                event.getPolicyReason(),
                event.getExecutionStatus(),
                event.getErrorType(),
                event.getErrorMessage(),
                event.getNodeBeforeId(),
                event.getNodeBeforeState(),
                event.getNodeBeforeCapability(),
                event.getNodeAfterId(),
                event.getNodeAfterState(),
                event.getNodeAfterCapability(),
                event.getStateBefore(),
                event.getStateAfter(),
                event.getClassificationBefore(),
                event.getClassificationAfter(),
                event.getTransformationType(),
                event.getRiskLevel(),
                event.getIntervention(),
                event.getRiskReason()
        );
    }
}