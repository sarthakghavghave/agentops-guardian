package com.agentops.guardian.governance.audit;

import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;

import java.time.Instant;

public record AuditEvent(
        String workflowId,
        String agentName,
        WorkflowType workflowType,
        Instant timestamp,
        AuditEventType eventType,
        String toolCallId,
        String toolName,
        WorkflowCapability capability,
        Integer sequence,
        GovernanceDecision.DecisionType policyDecision,
        String policyId,
        String policyReason,
        ExecutionStatus executionStatus,
        String errorType,
        String errorMessage,
        WorkflowNodeSnapshot nodeBefore,
        WorkflowNodeSnapshot nodeAfter,
        WorkflowState stateBefore,
        WorkflowState stateAfter,
        DataClassification classificationBefore,
        DataClassification classificationAfter,
        String transformationType
) {
        public AuditEvent(
            String workflowId,
            String agentName,
            WorkflowType workflowType,
            Instant timestamp,
            AuditEventType eventType,
            String toolCallId,
            String toolName,
            WorkflowCapability capability,
            Integer sequence,
            GovernanceDecision.DecisionType policyDecision,
            String policyId,
            String policyReason,
            ExecutionStatus executionStatus,
            String errorType,
            String errorMessage,
            WorkflowNodeSnapshot nodeBefore,
            WorkflowNodeSnapshot nodeAfter,
            WorkflowState stateBefore,
            WorkflowState stateAfter
        ) {
        this(
            workflowId,
            agentName,
            workflowType,
            timestamp,
            eventType,
            toolCallId,
            toolName,
            capability,
            sequence,
            policyDecision,
            policyId,
            policyReason,
            executionStatus,
            errorType,
            errorMessage,
            nodeBefore,
            nodeAfter,
            stateBefore,
            stateAfter,
            null,
            null,
            null
        );
        }

    public AuditEvent {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("Workflow id is required.");
        }
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("Agent name is required.");
        }
        if (workflowType == null) {
            throw new IllegalArgumentException("Workflow type is required.");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp is required.");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("Event type is required.");
        }
    }

    public static AuditEvent proposedAction(
            String workflowId,
            String agentName,
            WorkflowType workflowType,
            String toolCallId,
            String toolName,
            WorkflowCapability capability,
            Integer sequence
    ) {
        return new AuditEvent(
                workflowId,
                agentName,
                workflowType,
                Instant.now(),
                AuditEventType.PROPOSED_ACTION,
                toolCallId,
                toolName,
                capability,
                sequence,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static AuditEvent policyDecision(
            String workflowId,
            String agentName,
            WorkflowType workflowType,
            String toolCallId,
            String toolName,
            WorkflowCapability capability,
            GovernanceDecision decision
    ) {
        return new AuditEvent(
                workflowId,
                agentName,
                workflowType,
                Instant.now(),
                AuditEventType.POLICY_DECISION,
                toolCallId,
                toolName,
                capability,
                null,
                decision.decision(),
                decision.policyId(),
                decision.reason(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static AuditEvent executionOutcome(
            String workflowId,
            String agentName,
            WorkflowType workflowType,
            String toolCallId,
            String toolName,
            WorkflowCapability capability,
            ExecutionStatus status,
            String errorType,
            String errorMessage,
            WorkflowNodeSnapshot nodeBefore,
            WorkflowNodeSnapshot nodeAfter,
            WorkflowState stateBefore,
            WorkflowState stateAfter
    ) {
            return executionOutcome(
                workflowId,
                agentName,
                workflowType,
                toolCallId,
                toolName,
                capability,
                status,
                errorType,
                errorMessage,
                nodeBefore,
                nodeAfter,
                stateBefore,
                stateAfter,
                null,
                null,
                null
            );
            }

            public static AuditEvent executionOutcome(
                String workflowId,
                String agentName,
                WorkflowType workflowType,
                String toolCallId,
                String toolName,
                WorkflowCapability capability,
                ExecutionStatus status,
                String errorType,
                String errorMessage,
                WorkflowNodeSnapshot nodeBefore,
                WorkflowNodeSnapshot nodeAfter,
                WorkflowState stateBefore,
                WorkflowState stateAfter,
                DataClassification classificationBefore,
                DataClassification classificationAfter,
                String transformationType
            ) {
        return new AuditEvent(
                workflowId,
                agentName,
                workflowType,
                Instant.now(),
                AuditEventType.EXECUTION_OUTCOME,
                toolCallId,
                toolName,
                capability,
                null,
                null,
                null,
                null,
                status,
                errorType,
                errorMessage,
                nodeBefore,
                nodeAfter,
                stateBefore,
                stateAfter,
                classificationBefore,
                classificationAfter,
                transformationType
        );
    }

    public static AuditEvent workflowTransition(
            String workflowId,
            String agentName,
            WorkflowType workflowType,
            String toolCallId,
            String toolName,
            WorkflowCapability capability,
            WorkflowNodeSnapshot nodeBefore,
            WorkflowNodeSnapshot nodeAfter,
            WorkflowState stateBefore,
            WorkflowState stateAfter
    ) {
        return new AuditEvent(
                workflowId,
                agentName,
                workflowType,
                Instant.now(),
                AuditEventType.WORKFLOW_TRANSITION,
                toolCallId,
                toolName,
                capability,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                nodeBefore,
                nodeAfter,
                stateBefore,
                stateAfter,
                null,
                null,
                null
        );
    }

    public enum ExecutionStatus {
        SUCCESS,
        FAILED,
        BLOCKED
    }
}
