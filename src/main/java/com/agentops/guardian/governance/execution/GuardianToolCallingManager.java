package com.agentops.guardian.governance.execution;

import com.agentops.guardian.governance.policy.GovernancePolicyEngine;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.model.GovernanceDecision;
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

    @Autowired
    public GuardianToolCallingManager(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine policyEngine,
            ToolCapabilityRegistry toolCapabilityRegistry) {
        this(
                workflowContextManager,
                policyEngine,
                toolCapabilityRegistry,
                ToolCallingManager.builder().build()
        );
    }

    GuardianToolCallingManager(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine policyEngine,
            ToolCapabilityRegistry toolCapabilityRegistry,
            ToolCallingManager delegate) {
        this.workflowContextManager = workflowContextManager;
        this.policyEngine = policyEngine;
        this.toolCapabilityRegistry = toolCapabilityRegistry;
        this.delegate = delegate;
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

                GovernanceDecision decision = policyEngine.evaluate(workflowContext, event);

                if (decision.decision() == GovernanceDecision.DecisionType.BLOCK) {
                    System.out.println("[GUARDIAN] BLOCKED");
                    System.out.println("  Workflow: " + workflowContext.getWorkflowId());
                    System.out.println("  Agent: " + workflowContext.getAgentName());
                    System.out.println("  Tool: " + event.toolName());
                    System.out.println("  Reason: " + decision.reason());
                    throw new GovernanceViolationException(decision.reason());
                }

                WorkflowCapability capability = proposedToolCall.capability();

                if (capability != null) {
                    workflowContext.validateCapabilityTransition(capability);

                    WorkflowAction action = WorkflowAction.create(
                            toolCall.id(),
                            workflowContext.nextActionSequence(),
                            toolCall.name(),
                            capability,
                            toolCall.arguments()
                    );

                    workflowContext.addAction(action);
                    proposedCapabilities.add(new ProposedCapability(toolCall.id(), capability));
                }

                workflowContext.addToolCall(event);
                logToolCall(event);

                System.out.println("[GUARDIAN] Governance decision: ALLOW");
                System.out.println("  Workflow: " + workflowContext.getWorkflowId());
                System.out.println("  Tool: " + event.toolName());
                System.out.println("  Reason: " + decision.reason());
            }

            ToolExecutionResult result = delegate.executeToolCalls(prompt, chatResponse);
            Set<String> executedToolCallIds = successfulToolCallIds(result);

            for (ProposedCapability proposedCapability : proposedCapabilities) {
                if (executedToolCallIds.contains(proposedCapability.toolCallId())) {
                    workflowContext.advanceAfterCapability(proposedCapability.capability());
                }
            }

            return result;
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

    private record ProposedToolCall(
            AssistantMessage.ToolCall toolCall,
            WorkflowCapability capability
    ) {}

    private record ProposedCapability(String toolCallId, WorkflowCapability capability) {}
}