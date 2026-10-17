package com.agentops.guardian.governance.context;

import com.agentops.guardian.coordinator.context.SharedContextService;
import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.audit.AuditService;
import com.agentops.guardian.governance.config.GovernanceSettings;
import com.agentops.guardian.governance.execution.ApprovedActionGrant;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.policy.GovernancePolicyEngine;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.messages.Message;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ConversationHistoryAccessService {

    public static final String ACTION_NAME = "readConversationHistory";
    private static final String AGENT_NAME = "EmailAgent";

    private final WorkflowContextManager workflowContextManager;
    private final GovernancePolicyEngine governancePolicyEngine;
    private final AuditService auditService;
    private final SharedContextService sharedContextService;
    private final GovernanceSettings governanceSettings;
    private final ApprovedActionGrant approvedActionGrant;

    @Autowired
    public ConversationHistoryAccessService(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine governancePolicyEngine,
            AuditService auditService,
            SharedContextService sharedContextService,
            GovernanceSettings governanceSettings,
            ApprovedActionGrant approvedActionGrant
    ) {
        this.workflowContextManager = workflowContextManager;
        this.governancePolicyEngine = governancePolicyEngine;
        this.auditService = auditService;
        this.sharedContextService = sharedContextService;
        this.governanceSettings = governanceSettings;
        this.approvedActionGrant = approvedActionGrant;
    }

    public ConversationHistoryAccessService(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine governancePolicyEngine,
            AuditService auditService,
            SharedContextService sharedContextService,
            GovernanceSettings governanceSettings
    ) {
        this(
                workflowContextManager,
                governancePolicyEngine,
                auditService,
                sharedContextService,
                governanceSettings,
                new ApprovedActionGrant()
        );
    }

    public ConversationHistoryAccessService(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine governancePolicyEngine,
            AuditService auditService,
            SharedContextService sharedContextService,
            boolean governanceEnabled
    ) {
        this(
                workflowContextManager,
                governancePolicyEngine,
                auditService,
                sharedContextService,
                configuredSettings(governanceEnabled),
                new ApprovedActionGrant()
        );
    }

    public HistoryAccessResult readHistory(UUID conversationId) {
        return readHistory(conversationId, null);
    }

    public HistoryAccessResult readHistory(UUID conversationId, WorkflowContext activeWorkflow) {
        if (activeWorkflow != null
                && (activeWorkflow.getWorkflowType() != WorkflowType.EMAIL_COMMUNICATION
                || !Objects.equals(activeWorkflow.getConversationId(), conversationId))) {
            throw new IllegalArgumentException(
                    "Conversation history access must use the matching email workflow context."
            );
        }
        if (!governanceSettings.isEnabled()) {
            return new HistoryAccessResult(
                    new GovernanceDecision(
                            GovernanceDecision.DecisionType.ALLOW,
                            "Governance is disabled; shared conversation history remains available.",
                            "GOVERNANCE_DISABLED"
                    ),
                    sharedContextService.history(conversationId)
            );
        }

        boolean ownsWorkflow = activeWorkflow == null;
        WorkflowContext workflow = ownsWorkflow
                ? workflowContextManager.start(
                        AGENT_NAME,
                        WorkflowType.EMAIL_COMMUNICATION,
                        "Evaluate EmailAgent access to shared conversation history.",
                        conversationId
                )
                : activeWorkflow;
        ToolCallEvent proposedCall = new ToolCallEvent(
                UUID.randomUUID().toString(),
                ACTION_NAME,
                "{}",
                "governed-action",
                Instant.now()
        );
        boolean finalized = false;
        boolean outcomeRecorded = false;
        try {
            workflow.addToolCall(proposedCall);
            auditService.record(withApprovalCorrelation(AuditEvent.proposedAction(
                    workflow.getWorkflowId(),
                    workflow.getAgentName(),
                    workflow.getWorkflowType(),
                    proposedCall.toolCallId(),
                    proposedCall.toolName(),
                    WorkflowCapability.READ_CONVERSATION_HISTORY,
                    1
            ).withConversationId(conversationId)));
            GovernanceDecision decision = governancePolicyEngine.evaluate(workflow, proposedCall);
            auditService.record(withApprovalCorrelation(AuditEvent.policyDecision(
                    workflow.getWorkflowId(),
                    workflow.getAgentName(),
                    workflow.getWorkflowType(),
                    proposedCall.toolCallId(),
                    proposedCall.toolName(),
                    WorkflowCapability.READ_CONVERSATION_HISTORY,
                    decision
            ).withConversationId(conversationId)));

            if (decision.decision() == GovernanceDecision.DecisionType.BLOCK) {
                recordOutcome(workflow, proposedCall, conversationId, AuditEvent.ExecutionStatus.BLOCKED,
                        decision.reason());
                outcomeRecorded = true;
                if (ownsWorkflow) {
                    workflowContextManager.block();
                }
                finalized = true;
                return new HistoryAccessResult(decision, List.of());
            }

            List<Message> messages = sharedContextService.history(conversationId);
            recordOutcome(workflow, proposedCall, conversationId, AuditEvent.ExecutionStatus.SUCCESS, null);
            outcomeRecorded = true;
            if (ownsWorkflow) {
                workflowContextManager.complete();
            }
            finalized = true;
            return new HistoryAccessResult(decision, messages);
        } catch (RuntimeException exception) {
            if (!finalized) {
                if (!outcomeRecorded) {
                    try {
                        recordOutcome(workflow, proposedCall, conversationId, AuditEvent.ExecutionStatus.FAILED,
                                exception.getMessage());
                    } catch (RuntimeException auditFailure) {
                        exception.addSuppressed(auditFailure);
                    }
                }
                if (ownsWorkflow) {
                    workflowContextManager.fail();
                }
            }
            throw exception;
        } finally {
            if (ownsWorkflow) {
                workflowContextManager.clear();
            }
        }
    }

    private void recordOutcome(
            WorkflowContext workflow,
            ToolCallEvent proposedCall,
            UUID conversationId,
            AuditEvent.ExecutionStatus status,
            String reason
    ) {
        auditService.record(withApprovalCorrelation(AuditEvent.executionOutcome(
                workflow.getWorkflowId(),
                workflow.getAgentName(),
                workflow.getWorkflowType(),
                proposedCall.toolCallId(),
                proposedCall.toolName(),
                WorkflowCapability.READ_CONVERSATION_HISTORY,
                status,
                null,
                reason,
                null,
                null,
                null,
                null
        ).withConversationId(conversationId)));
    }

    private AuditEvent withApprovalCorrelation(AuditEvent event) {
        String interventionId = approvedActionGrant.currentInterventionId();
        return interventionId == null ? event : event.withInterventionId(interventionId);
    }

    private static GovernanceSettings configuredSettings(boolean enabled) {
        GovernanceSettings settings = new GovernanceSettings();
        settings.setEnabled(enabled);
        return settings;
    }

    public record HistoryAccessResult(GovernanceDecision decision, List<Message> messages) {
        public HistoryAccessResult {
            if (decision == null || messages == null) {
                throw new IllegalArgumentException("History access decision and messages are required.");
            }
            messages = List.copyOf(messages);
        }

        public boolean allowed() {
            return decision.decision() == GovernanceDecision.DecisionType.ALLOW;
        }
    }
}
