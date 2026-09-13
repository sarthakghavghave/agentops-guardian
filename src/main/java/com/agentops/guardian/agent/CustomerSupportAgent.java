package com.agentops.guardian.agent;

import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.tool.CustomerTools;
import com.agentops.guardian.tool.OrderTools;
import com.agentops.guardian.tool.ProductTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class CustomerSupportAgent {

    private final ChatClient chatClient;
    private final WorkflowContextManager workflowContextManager;

    public CustomerSupportAgent(
            ChatClient.Builder chatClientBuilder,
            CustomerTools customerTools,
            OrderTools orderTools,
            ProductTools productTools,
            WorkflowContextManager workflowContextManager) throws IOException {

        this.workflowContextManager = workflowContextManager;

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
                        productTools)
                .build();
    }

    public String chat(String message) {
        workflowContextManager.start(
                "CustomerSupportAgent",
                WorkflowType.CUSTOMER_SUPPORT,
                message
        );

        try {
            String response = chatClient
                    .prompt()
                    .user(message)
                    .call()
                    .content();

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