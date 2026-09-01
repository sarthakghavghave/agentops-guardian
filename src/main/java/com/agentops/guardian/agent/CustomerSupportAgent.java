package com.agentops.guardian.agent;

import com.agentops.guardian.governance.WorkflowContextHolder;
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
    private final WorkflowContextHolder workflowContextHolder;

    public CustomerSupportAgent(
            ChatClient.Builder chatClientBuilder,
            CustomerTools customerTools,
            OrderTools orderTools,
            ProductTools productTools,
            WorkflowContextHolder workflowContextHolder) throws IOException {

        this.workflowContextHolder = workflowContextHolder;

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

        workflowContextHolder.startWorkflow("CustomerSupportAgent");

        try {
            return chatClient
                    .prompt()
                    .user(message)
                    .call()
                    .content();
        }
        finally {workflowContextHolder.clear();}
    }
}