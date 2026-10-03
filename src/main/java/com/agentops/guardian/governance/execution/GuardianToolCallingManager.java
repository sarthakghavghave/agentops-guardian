package com.agentops.guardian.governance.execution;

import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.audit.AuditService;
import com.agentops.guardian.governance.audit.NoOpAuditService;
import com.agentops.guardian.governance.audit.WorkflowNodeSnapshot;
import com.agentops.guardian.governance.risk.GovernanceIntervention;
import com.agentops.guardian.governance.risk.RiskDecision;
import com.agentops.guardian.governance.risk.RiskEvaluator;
import com.agentops.guardian.governance.policy.GovernancePolicyEngine;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.exception.ApprovalRequiredException;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.DataTransformation;
import com.agentops.guardian.governance.model.WorkflowAction;
import com.agentops.guardian.governance.workflow.ToolCapabilityRegistry;
import com.agentops.guardian.governance.workflow.WorkflowCapability;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class GuardianToolCallingManager implements ToolCallingManager {

    private final ToolCallingManager delegate;
    private final GovernancePolicyEngine policyEngine;
    private final WorkflowContextManager workflowContextManager;
    private final ToolCapabilityRegistry toolCapabilityRegistry;
    private final AuditService auditService;
    private final RiskEvaluator riskEvaluator;

    @Autowired
    public GuardianToolCallingManager(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine policyEngine,
            ToolCapabilityRegistry toolCapabilityRegistry,
            AuditService auditService,
            RiskEvaluator riskEvaluator) {
        this(
                workflowContextManager,
                policyEngine,
                toolCapabilityRegistry,
                ToolCallingManager.builder().build(),
                auditService,
                riskEvaluator
        );
    }

    GuardianToolCallingManager(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine policyEngine,
            ToolCapabilityRegistry toolCapabilityRegistry,
            ToolCallingManager delegate) {
        this(
                workflowContextManager,
                policyEngine,
                toolCapabilityRegistry,
                delegate,
                new NoOpAuditService(),
                new RiskEvaluator()
        );
    }

    GuardianToolCallingManager(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine policyEngine,
            ToolCapabilityRegistry toolCapabilityRegistry,
            ToolCallingManager delegate,
            AuditService auditService) {
        this(
                workflowContextManager,
                policyEngine,
                toolCapabilityRegistry,
                delegate,
                auditService,
                new RiskEvaluator()
        );
    }

    GuardianToolCallingManager(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine policyEngine,
            ToolCapabilityRegistry toolCapabilityRegistry,
            ToolCallingManager delegate,
            AuditService auditService,
            RiskEvaluator riskEvaluator) {
        this.workflowContextManager = workflowContextManager;
        this.policyEngine = policyEngine;
        this.toolCapabilityRegistry = toolCapabilityRegistry;
        this.delegate = delegate;
        this.auditService = auditService;
        this.riskEvaluator = riskEvaluator;
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {

        if (chatResponse != null
                && chatResponse.getResult() != null
                && chatResponse.getResult().getOutput() != null) {

            var toolCalls = chatResponse.getResult().getOutput().getToolCalls();

            WorkflowContext workflowContext = workflowContextManager.current();
                List<ProposedToolCall> proposedToolCalls = toolCalls.stream()
                    .map(toolCall -> new ProposedToolCall(
                        toolCall,
                        toolCapabilityRegistry.getCapability(toolCall.name())
                    ))
                    .toList();
                long recognizedCapabilityCount = proposedToolCalls.stream()
                    .filter(proposedToolCall -> proposedToolCall.capability() != null)
                    .count();

                if (recognizedCapabilityCount > 1) {
                throw new GovernanceViolationException(
                    "Batch execution containing multiple workflow capabilities is not supported."
                );
                }

            List<ProposedCapability> proposedCapabilities = new ArrayList<>();

            for (ProposedToolCall proposedToolCall : proposedToolCalls) {
                var toolCall = proposedToolCall.toolCall();
                ToolCallEvent event = new ToolCallEvent(
                        toolCall.id(),
                        toolCall.name(),
                        toolCall.arguments(),
                        toolCall.type(),
                        Instant.now()
                );

                WorkflowCapability capability = proposedToolCall.capability();

                auditService.record(AuditEvent.proposedAction(
                        workflowContext.getWorkflowId(),
                        workflowContext.getAgentName(),
                        workflowContext.getWorkflowType(),
                        event.toolCallId(),
                        event.toolName(),
                        capability,
                        null
                ));

                workflowContext.addToolCall(event);

                GovernanceDecision decision = policyEngine.evaluate(workflowContext, event);

                auditService.record(AuditEvent.policyDecision(
                        workflowContext.getWorkflowId(),
                        workflowContext.getAgentName(),
                        workflowContext.getWorkflowType(),
                        event.toolCallId(),
                        event.toolName(),
                        capability,
                        decision
                ));

                if (decision.decision() == GovernanceDecision.DecisionType.BLOCK) {
                    auditService.record(AuditEvent.executionOutcome(
                            workflowContext.getWorkflowId(),
                            workflowContext.getAgentName(),
                            workflowContext.getWorkflowType(),
                            event.toolCallId(),
                            event.toolName(),
                            capability,
                            AuditEvent.ExecutionStatus.BLOCKED,
                            null,
                            decision.reason(),
                            null,
                            null,
                            null,
                            null
                    ));

                    System.out.println("[GUARDIAN] BLOCKED");
                    System.out.println("  Workflow: " + workflowContext.getWorkflowId());
                    System.out.println("  Agent: " + workflowContext.getAgentName());
                    System.out.println("  Tool: " + event.toolName());
                    System.out.println("  Reason: " + decision.reason());

                    throw new GovernanceViolationException(decision.reason());
                }

                if (capability != null) {
                    workflowContext.validateCapabilityTransition(capability);

                    RiskDecision riskDecision = RiskDecision.from(riskEvaluator.assess(workflowContext, capability));
                    auditService.record(AuditEvent.riskAssessment(
                            workflowContext.getWorkflowId(),
                            workflowContext.getAgentName(),
                            workflowContext.getWorkflowType(),
                            event.toolCallId(),
                            event.toolName(),
                            capability,
                            workflowContext.nextActionSequence(),
                            riskDecision
                    ));

                    if (riskDecision.intervention() == GovernanceIntervention.REQUIRE_APPROVAL) {
                        throw new ApprovalRequiredException(event.toolCallId(), riskDecision);
                    }
                    if (riskDecision.intervention() == GovernanceIntervention.BLOCK) {
                        throw new GovernanceViolationException(
                                "Risk intervention BLOCK: " + riskDecision.reason()
                        );
                    }

                    Integer sequence = workflowContext.nextActionSequence();

                    WorkflowAction action = WorkflowAction.create(
                            toolCall.id(),
                            sequence,
                            toolCall.name(),
                            capability,
                            toolCall.arguments()
                    );

                    workflowContext.addAction(action);
                    proposedCapabilities.add(new ProposedCapability(toolCall.id(), capability));
                }

                logToolCall(event);

                System.out.println("[GUARDIAN] Governance decision: ALLOW");
                System.out.println("  Workflow: " + workflowContext.getWorkflowId());
                System.out.println("  Tool: " + event.toolName());
                System.out.println("  Reason: " + decision.reason());
            }

            DataTransformation transformationBeforeExecution = workflowContext.getLastTransformation();
            ToolExecutionResult result;
            try {
                result = delegate.executeToolCalls(prompt, chatResponse);
                Set<String> executedToolCallIds = successfulToolCallIds(result);

                for (ProposedToolCall proposedToolCall : proposedToolCalls) {
                    String toolCallId = proposedToolCall.toolCall().id();
                    boolean executed = executedToolCallIds.contains(toolCallId);
                    WorkflowCapability capability = proposedToolCall.capability();

                    if (executed) {
                        DataTransformation transformation = null;
                        if (capability == WorkflowCapability.GENERATE_REPORT) {
                            DataTransformation latestTransformation = workflowContext.getLastTransformation();
                            if (latestTransformation != transformationBeforeExecution) {
                                transformation = latestTransformation;
                            }
                        }

                        WorkflowNodeSnapshot beforeSuccess = capability == null ? null : new WorkflowNodeSnapshot(
                                workflowContext.getCurrentNodeId(),
                                workflowContext.getCurrentState(),
                                capability
                        );

                        auditService.record(AuditEvent.executionOutcome(
                                workflowContext.getWorkflowId(),
                                workflowContext.getAgentName(),
                                workflowContext.getWorkflowType(),
                                toolCallId,
                                proposedToolCall.toolCall().name(),
                                capability,
                                AuditEvent.ExecutionStatus.SUCCESS,
                                null,
                                null,
                                beforeSuccess,
                                null,
                                beforeSuccess == null ? null : beforeSuccess.state(),
                                null,
                                transformation == null ? null : transformation.sourceClassification(),
                                transformation == null ? null : transformation.resultClassification(),
                                transformation == null ? null : transformation.transformationType()
                        ));

                        if (capability != null) {
                            WorkflowNodeSnapshot nodeBefore = new WorkflowNodeSnapshot(
                                    workflowContext.getCurrentNodeId(),
                                    workflowContext.getCurrentState(),
                                    capability
                            );

                            workflowContext.advanceAfterCapability(capability);

                            WorkflowNodeSnapshot nodeAfter = new WorkflowNodeSnapshot(
                                    workflowContext.getCurrentNodeId(),
                                    workflowContext.getCurrentState(),
                                    capability
                            );

                            auditService.record(AuditEvent.workflowTransition(
                                    workflowContext.getWorkflowId(),
                                    workflowContext.getAgentName(),
                                    workflowContext.getWorkflowType(),
                                    toolCallId,
                                    proposedToolCall.toolCall().name(),
                                    capability,
                                    nodeBefore,
                                    nodeAfter,
                                    nodeBefore.state(),
                                    nodeAfter.state()
                            ));
                        }
                    }
                }

                return result;
            } catch (RuntimeException ex) {
                for (ProposedToolCall proposedToolCall : proposedToolCalls) {
                    WorkflowCapability capability = proposedToolCall.capability();
                    String toolId = proposedToolCall.toolCall().id();
                    WorkflowNodeSnapshot failedBefore = capability == null ? null : new WorkflowNodeSnapshot(
                            workflowContext.getCurrentNodeId(),
                            workflowContext.getCurrentState(),
                            capability
                    );
                    auditService.record(AuditEvent.executionOutcome(
                            workflowContext.getWorkflowId(),
                            workflowContext.getAgentName(),
                            workflowContext.getWorkflowType(),
                            toolId,
                            proposedToolCall.toolCall().name(),
                            capability,
                            AuditEvent.ExecutionStatus.FAILED,
                            ex.getClass().getName(),
                            sanitizeError(ex),
                            failedBefore,
                            null,
                            failedBefore == null ? null : failedBefore.state(),
                            null
                    ));
                }
                throw ex;
            }
        }

        return delegate.executeToolCalls(prompt, chatResponse);
    }

    @Override
    public List<org.springframework.ai.tool.definition.ToolDefinition>
    resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    private void logToolCall(ToolCallEvent event) {

        WorkflowContext workflowContext = workflowContextManager.current();

        System.out.println(
                "[GUARDIAN] Tool call intercepted"
                        + "\n  Workflow: "
                        + (workflowContext != null ? workflowContext.getWorkflowId() : "NONE")
                        + "\n  Agent: "
                        + (workflowContext != null ? workflowContext.getAgentName() : "NONE")
                        + "\n  Tool: " + event.toolName()
                        + "\n  Call ID: " + event.toolCallId()
                        + "\n  Arguments: " + event.arguments()
                        + "\n  Type: " + event.toolType()
                        + "\n  Timestamp: " + event.timestamp()
        );
    }

    private Set<String> successfulToolCallIds(ToolExecutionResult result) {
        Set<String> toolCallIds = new HashSet<>();

        if (result == null || result.conversationHistory() == null) {
            return toolCallIds;
        }

        result.conversationHistory().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())
                .map(ToolResponseMessage.ToolResponse::id)
                .forEach(toolCallIds::add);

        return toolCallIds;
    }

    private String sanitizeError(RuntimeException ex) {
        if (ex == null) {
            return null;
        }
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private record ProposedToolCall(
            AssistantMessage.ToolCall toolCall,
            WorkflowCapability capability
    ) {}

    private record ProposedCapability(String toolCallId, WorkflowCapability capability) {}
}