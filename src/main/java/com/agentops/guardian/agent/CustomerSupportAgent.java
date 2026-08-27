package com.agentops.guardian.agent;

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

    public CustomerSupportAgent(
            ChatClient.Builder chatClientBuilder,
            CustomerTools customerTools,
            OrderTools orderTools,
            ProductTools productTools) throws IOException {

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
        return chatClient
                .prompt()
                .user(message)
                .call()
                .content();
    }
}