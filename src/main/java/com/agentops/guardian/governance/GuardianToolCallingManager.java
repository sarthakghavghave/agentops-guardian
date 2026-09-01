package com.agentops.guardian.governance;

import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.model.WorkflowContext;
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
    private final WorkflowContextHolder workflowContextHolder;

    public GuardianToolCallingManager(WorkflowContextHolder workflowContextHolder) {
        this.workflowContextHolder = workflowContextHolder;
        this.delegate = ToolCallingManager.builder().build();
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {

        if (chatResponse != null
                && chatResponse.getResult() != null
                && chatResponse.getResult().getOutput() != null) {

            var toolCalls = chatResponse
                    .getResult()
                    .getOutput()
                    .getToolCalls();

            for (var toolCall : toolCalls) {

                ToolCallEvent event = new ToolCallEvent(
                        toolCall.id(),
                        toolCall.name(),
                        toolCall.arguments(),
                        toolCall.type(),
                        Instant.now()
                );

                WorkflowContext workflowContext = workflowContextHolder.getCurrentWorkflow();

                if (workflowContext != null) {
                    workflowContext.addToolCall(event);
                }

                logToolCall(event);
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

        WorkflowContext workflowContext = workflowContextHolder.getCurrentWorkflow();

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