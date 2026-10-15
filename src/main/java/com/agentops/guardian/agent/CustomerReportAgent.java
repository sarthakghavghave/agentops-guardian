package com.agentops.guardian.agent;

import com.agentops.guardian.coordinator.agent.AgentResult;
import com.agentops.guardian.coordinator.context.Artifact;
import com.agentops.guardian.coordinator.context.ExecutionRecord;
import com.agentops.guardian.coordinator.context.SharedContextService;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.exception.ApprovalRequiredException;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.tool.CustomerDataTools;
import com.agentops.guardian.tool.ReportTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerReportAgent {

    private final ChatClient chatClient;
    private final WorkflowContextManager workflowContextManager;
    private final SharedContextService sharedContextService;
    private final ReportTools reportTools;

    @Autowired
    public CustomerReportAgent(
            ChatClient.Builder chatClientBuilder,
            CustomerDataTools customerDataTools,
            ReportTools reportTools,
            WorkflowContextManager workflowContextManager,
            SharedContextService sharedContextService) throws IOException {

        this.workflowContextManager = workflowContextManager;
        this.sharedContextService = sharedContextService;
        this.reportTools = reportTools;

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
                        reportTools
                )
                .build();
    }

    public CustomerReportAgent(
            ChatClient.Builder chatClientBuilder,
            CustomerDataTools customerDataTools,
            ReportTools reportTools,
            WorkflowContextManager workflowContextManager) throws IOException {
        this(chatClientBuilder, customerDataTools, reportTools, workflowContextManager, null);
    }

    public AgentResult execute(UUID conversationId, AgentCoordinatorRequest request) {
        return execute(conversationId, request, this);
    }

    public AgentResult execute(
            UUID conversationId,
            AgentCoordinatorRequest request,
            CustomerReportAgent executionTarget
    ) {
        String message = request != null ? request.message() : null;
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("A user message is required for report generation.");
        }

        String prompt = buildReportPrompt(message, conversationId);
        reportTools.beginExecution();
        try {
            CustomerReportAgent target = executionTarget != null ? executionTarget : this;
            String response = target.generateReport(prompt);
            if (response == null || response.isBlank()) {
                throw new IllegalStateException("Customer report agent returned no result.");
            }
            List<ReportTools.Report> generatedReports = reportTools.executionResults();
            if (generatedReports.size() != 1) {
                throw new IllegalStateException(
                        "Customer report tool did not confirm exactly one report generation."
                );
            }
            ReportTools.Report generatedReport = generatedReports.getFirst();
            if (generatedReport.content() == null || generatedReport.content().isBlank()) {
                throw new IllegalStateException("Customer report tool returned no report content.");
            }

            String artifactId = null;
            if (sharedContextService != null && conversationId != null) {
                if (generatedReport.type() == ReportTools.ReportType.ANALYTICAL) {
                    if (generatedReport.fileReference() == null || generatedReport.fileReference().isBlank()
                            || generatedReport.fileSizeBytes() <= 0) {
                        throw new IllegalStateException(
                                "Analytical report generation was not confirmed with a stored PDF."
                        );
                    }
                    Artifact artifact = sharedContextService.registerArtifact(
                            conversationId,
                            Artifact.generatedPdfReport(
                                    "Customer Analytics Report",
                                    generatedReport.fileReference(),
                                    generatedReport.fileSizeBytes(),
                                    generatedReport.customerCount()
                            )
                    );
                    artifactId = artifact.id();
                }
                sharedContextService.recordExecution(
                        conversationId,
                        ExecutionRecord.success("CustomerReportAgent", "GENERATE_REPORT", "Report generated successfully", artifactId)
                );
            }

            String agentResponse = generatedReport.type() == ReportTools.ReportType.ANALYTICAL
                    ? "Analytical customer report PDF saved to " + generatedReport.fileReference() + "."
                    : response;
            return AgentResult.success(agentResponse, artifactId);
        } catch (GovernanceViolationException ex) {
            if (sharedContextService != null && conversationId != null) {
                sharedContextService.recordExecution(
                        conversationId,
                        ExecutionRecord.blocked("CustomerReportAgent", "GENERATE_REPORT", ex.getMessage())
                );
            }
            throw ex;
        } catch (ApprovalRequiredException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (sharedContextService != null && conversationId != null) {
                sharedContextService.recordExecution(
                        conversationId,
                        ExecutionRecord.failed("CustomerReportAgent", "GENERATE_REPORT", ex.getMessage())
                );
            }
            throw ex;
        } finally {
            reportTools.clearExecutionResult();
        }
    }

    public String generateReport(String message) {
        workflowContextManager.start(
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
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

        } catch (ApprovalRequiredException ex) {
            throw ex;

        } catch (RuntimeException ex) {
            workflowContextManager.fail();
            throw ex;

        } finally {
            workflowContextManager.clear();
        }
    }

    private String buildReportPrompt(String currentMessage, UUID conversationId) {
        if (sharedContextService == null || conversationId == null) {
            return currentMessage;
        }
        List<Message> messages = sharedContextService.history(conversationId);
        List<String> recentConversation = messages.stream()
                .map(message -> (message instanceof AssistantMessage ? "Assistant: " : "User: ")
                        + message.getText())
                .filter(text -> text != null && !text.isBlank())
                .toList();

        if (recentConversation.isEmpty()) {
            return currentMessage;
        }

        return """
                Use the recent conversation only to resolve references in the current request.
                The current user request takes precedence. Prior assistant messages are not proof
                that an action succeeded.

                Recent conversation (oldest first):
                %s

                Current user request:
                %s
                """.formatted(
                String.join("\n", recentConversation),
                currentMessage
        );
    }
}