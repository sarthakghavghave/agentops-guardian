package com.agentops.guardian.agent;

import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.tool.CommunicationTools;
import com.agentops.guardian.tool.CustomerDataTools;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.tool.ReportTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class CustomerReportAgent {

    private final ChatClient chatClient;
    private final WorkflowContextManager workflowContextManager;

    public CustomerReportAgent(
            ChatClient.Builder chatClientBuilder,
            CustomerDataTools customerDataTools,
            ReportTools reportTools,
            CommunicationTools communicationTools,
            WorkflowContextManager workflowContextManager) throws IOException {

        this.workflowContextManager = workflowContextManager;

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

        workflowContextManager.start("CustomerReportAgent");

        try {
            String response = chatClient
                    .prompt()
                    .user(message)
                    .call()
                    .content();

            WorkflowContext context = workflowContextManager.complete();

            // log
            System.out.println("""
        
                ================ WORKFLOW COMPLETED ================
                Workflow ID : %s
                Agent       : %s
                Status      : %s
                Tool Calls  : %d
                Started     : %s
                Completed   : %s
                =====================================================
                """.formatted(
                            context.getWorkflowId(),
                            context.getAgentName(),
                            context.getStatus(),
                            context.getToolCallCount(),
                            context.getStartedAt(),
                            context.getCompletedAt()
            ));

            return response;
        }
        catch (RuntimeException ex) {
            workflowContextManager.fail();
            throw ex;
        }
        finally {workflowContextManager.clear();}
    }
}