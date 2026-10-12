package com.agentops.guardian.agent;

import com.agentops.guardian.coordinator.context.SharedContextService;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.tool.CustomerDataTools;
import com.agentops.guardian.tool.CustomerTools;
import com.agentops.guardian.tool.OrderTools;
import com.agentops.guardian.tool.ProductTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerSupportAgent {

    private final ChatClient chatClient;
    private final WorkflowContextManager workflowContextManager;
    private final SharedContextService sharedContextService;

    @Autowired
    public CustomerSupportAgent(
            ChatClient.Builder chatClientBuilder,
            CustomerTools customerTools,
            OrderTools orderTools,
            ProductTools productTools,
            CustomerDataTools customerDataTools,
            WorkflowContextManager workflowContextManager,
            SharedContextService sharedContextService) throws IOException {

        this.workflowContextManager = workflowContextManager;
        this.sharedContextService = sharedContextService;

        String systemPrompt = new String(
                new ClassPathResource("prompts/customer-support-system.txt")
                        .getInputStream()
                        .readAllBytes(),
                StandardCharsets.UTF_8
        );

        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultTools(
                        customerTools,
                        orderTools,
                        productTools,
                        customerDataTools)
                .build();
    }

    public String chat(String message) {
        return chat(null, message);
    }

    public String chat(UUID conversationId, String message) {
        workflowContextManager.start(
                "CustomerSupportAgent",
                WorkflowType.CUSTOMER_SUPPORT,
                message
        );

        try {
            ChatClient.ChatClientRequestSpec prompt = chatClient.prompt();
            String response;
            if (conversationId == null || sharedContextService == null) {
                response = prompt.user(message).call().content();
            } else {
                List<Message> conversation = new ArrayList<>(sharedContextService.history(conversationId));
                conversation.add(new UserMessage(message));
                response = prompt.messages(conversation).call().content();
            }

            workflowContextManager.complete();
            return response;

        } catch (GovernanceViolationException ex) {
            workflowContextManager.block();
            throw ex;

        } catch (RuntimeException ex) {
            workflowContextManager.fail();
            throw ex;

        } finally {
            workflowContextManager.clear();
        }
    }
}