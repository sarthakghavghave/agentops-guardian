package com.agentops.guardian.agent;

import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.tool.CommunicationTools;
import com.agentops.guardian.tool.ReportTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class EmailAgent {

    private final ChatClient chatClient;
    private final WorkflowContextManager workflowContextManager;

    public EmailAgent(
            ChatClient.Builder chatClientBuilder,
            CommunicationTools communicationTools,
            WorkflowContextManager workflowContextManager
    ) throws IOException {
        this.workflowContextManager = workflowContextManager;
        String systemPrompt = new String(
                new ClassPathResource("prompts/email-agent-system.txt")
                        .getInputStream()
                        .readAllBytes(),
                StandardCharsets.UTF_8
        );
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultTools(communicationTools)
                .build();
    }

    public String emailReport(ReportTools.Report report, String recipient) {
        if (report == null) {
            throw new IllegalArgumentException("Report handoff is required.");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Email recipient is required.");
        }
        if (report.content() == null || report.content().isBlank()) {
            throw new IllegalArgumentException("Report content is required.");
        }

        String request = """
                Email the explicitly supplied report to the specified recipient.

                Recipient: %s
                Report type: %s
                Report content:
                <report>
                %s
                </report>
                """.formatted(recipient, report.type(), report.content());

        workflowContextManager.start("EmailAgent", WorkflowType.EMAIL_COMMUNICATION, request);
        try {
            String response = chatClient
                    .prompt()
                    .user(request)
                    .call()
                    .content();
            workflowContextManager.complete();
            return response;
        } catch (GovernanceViolationException exception) {
            workflowContextManager.block();
            throw exception;
        } catch (RuntimeException exception) {
            workflowContextManager.fail();
            throw exception;
        } finally {
            workflowContextManager.clear();
        }
    }
}
