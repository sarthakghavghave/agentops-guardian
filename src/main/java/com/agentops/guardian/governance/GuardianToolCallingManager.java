package com.agentops.guardian.governance;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class GuardianToolCallingManager implements ToolCallingManager {

    private final ToolCallingManager delegate;

    public GuardianToolCallingManager() {
        this.delegate = ToolCallingManager.builder().build();
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {

        if (chatResponse != null
                && chatResponse.getResult() != null
                && chatResponse.getResult().getOutput() != null) {

            var toolCalls = chatResponse.getResult().getOutput().getToolCalls();

            for (var toolCall : toolCalls) {
                System.out.println(
                        "[GUARDIAN] Tool call intercepted"
                                + "\n  Tool: " + toolCall.name()
                                + "\n  Arguments: " + toolCall.arguments()
                );
            }
        }
        return delegate.executeToolCalls(prompt, chatResponse);
    }

    @Override
    public java.util.List<org.springframework.ai.tool.definition.ToolDefinition>
    resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }
}