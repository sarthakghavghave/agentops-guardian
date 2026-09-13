package com.agentops.guardian.governance.execution;

import com.agentops.guardian.governance.policy.GovernancePolicyEngine;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.workflow.ToolCapabilityRegistry;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class GuardianToolCallingManager implements ToolCallingManager {

    private final ToolCallingManager delegate;
    private final GovernancePolicyEngine policyEngine;
    private final WorkflowContextManager workflowContextManager;
    private final ToolCapabilityRegistry toolCapabilityRegistry;

    public GuardianToolCallingManager(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine policyEngine,
            ToolCapabilityRegistry toolCapabilityRegistry) {
        this.workflowContextManager = workflowContextManager;
        this.policyEngine = policyEngine;
        this.delegate = ToolCallingManager.builder().build();
        this.toolCapabilityRegistry = toolCapabilityRegistry;
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {

        if (chatResponse != null
                && chatResponse.getResult() != null
                && chatResponse.getResult().getOutput() != null) {

            var toolCalls = chatResponse.getResult().getOutput().getToolCalls();

            for (var toolCall : toolCalls) {
                ToolCallEvent event = new ToolCallEvent(
                        toolCall.id(),
                        toolCall.name(),
                        toolCall.arguments(),
                        toolCall.type(),
                        Instant.now()
                );

                WorkflowContext workflowContext = workflowContextManager.current();
                GovernanceDecision decision = policyEngine.evaluate(workflowContext, event);

                if (decision.decision() == GovernanceDecision.DecisionType.BLOCK) {
                    System.out.println("[GUARDIAN] BLOCKED");
                    System.out.println("  Workflow: " + workflowContext.getWorkflowId());
                    System.out.println("  Agent: " + workflowContext.getAgentName());
                    System.out.println("  Tool: " + event.toolName());
                    System.out.println("  Reason: " + decision.reason());
                    throw new GovernanceViolationException(decision.reason());
                }

                workflowContext.addToolCall(event);
                logToolCall(event);

                System.out.println("[GUARDIAN] Governance decision: ALLOW");
                System.out.println("  Workflow: " + workflowContext.getWorkflowId());
                System.out.println("  Tool: " + event.toolName());
                System.out.println("  Reason: " + decision.reason());
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
}