package com.agentops.guardian.agent;

import com.agentops.guardian.tool.CommunicationTools;
import com.agentops.guardian.tool.CustomerDataTools;
import com.agentops.guardian.governance.WorkflowContextHolder;
import com.agentops.guardian.tool.ReportTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class CustomerReportAgent {

    private final ChatClient chatClient;
    private final WorkflowContextHolder workflowContextHolder;

    public CustomerReportAgent(
            ChatClient.Builder chatClientBuilder,
            CustomerDataTools customerDataTools,
            ReportTools reportTools,
            CommunicationTools communicationTools,
            WorkflowContextHolder workflowContextHolder) throws IOException {

        this.workflowContextHolder = workflowContextHolder;

        String systemPrompt = new String(
                new ClassPathResource(
                        "prompts/customer-report-system.txt")
                        .getInputStream()
                        .readAllBytes(),
                StandardCharsets.UTF_8
        );

        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultTools(
                        customerDataTools,
                        reportTools,
                        communicationTools
                )
                .build();
    }

    public String generateReport(String message) {

        workflowContextHolder.startWorkflow("CustomerReportAgent");

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