package com.agentops.guardian.governance.intervention;

import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;

import java.time.Instant;

public record GovernanceIntervention(
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
        String resolutionReason,
        String workflowNodeId,
        WorkflowState workflowState,
        DataClassification classificationAtCreation
) {

    public GovernanceIntervention {
        requireText(interventionId, "Intervention id is required.");
        requireText(workflowId, "Workflow id is required.");
        requireText(toolCallId, "Tool call id is required.");
        requireText(agentName, "Agent name is required.");
        if (workflowType == null || capability == null || riskLevel == null || intervention == null || status == null) {
            throw new IllegalArgumentException("Intervention workflow, capability, risk, and status are required.");
        }
        if (intervention != com.agentops.guardian.governance.risk.GovernanceIntervention.REQUIRE_APPROVAL
                || riskLevel != RiskLevel.HIGH) {
            throw new IllegalArgumentException("Persistent interventions are only valid for HIGH approval decisions.");
        }
        requireText(reason, "Intervention reason is required.");
        if (createdAt == null || workflowNodeId == null || workflowNodeId.isBlank() || workflowState == null) {
            throw new IllegalArgumentException("Creation time and workflow state snapshot are required.");
        }
        if (status == GovernanceInterventionStatus.PENDING) {
            if (resolvedAt != null || resolvedBy != null || resolutionReason != null) {
                throw new IllegalArgumentException("Pending interventions cannot contain resolution information.");
            }
        } else {
            if (resolvedAt == null || resolvedBy == null || resolvedBy.isBlank()
                    || resolutionReason == null || resolutionReason.isBlank()) {
                throw new IllegalArgumentException("Resolved interventions require resolution information.");
            }
        }
    }

    public GovernanceIntervention approve(String actor, String resolution, Instant resolvedAt) {
        return resolve(GovernanceInterventionStatus.APPROVED, actor, resolution, resolvedAt);
    }

    public GovernanceIntervention reject(String actor, String resolution, Instant resolvedAt) {
        return resolve(GovernanceInterventionStatus.REJECTED, actor, resolution, resolvedAt);
    }

    public GovernanceIntervention expire(String reason, Instant resolvedAt) {
        return resolve(GovernanceInterventionStatus.EXPIRED, "system", reason, resolvedAt);
    }

    private GovernanceIntervention resolve(
            GovernanceInterventionStatus newStatus,
            String actor,
            String resolution,
            Instant resolvedAt
    ) {
        if (status != GovernanceInterventionStatus.PENDING) {
            throw new IllegalStateException("Only pending interventions can be resolved.");
        }
        return new GovernanceIntervention(
                interventionId,
                workflowId,
                toolCallId,
                agentName,
                workflowType,
                capability,
                riskLevel,
                intervention,
                newStatus,
                reason,
                createdAt,
                resolvedAt,
                actor,
                resolution,
                workflowNodeId,
                workflowState,
                classificationAtCreation
        );
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}