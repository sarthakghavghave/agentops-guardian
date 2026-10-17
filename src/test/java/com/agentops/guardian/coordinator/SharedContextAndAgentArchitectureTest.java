package com.agentops.guardian.coordinator;

import com.agentops.guardian.agent.CustomerReportAgent;
import com.agentops.guardian.agent.CustomerSupportAgent;
import com.agentops.guardian.agent.EmailAgent;
import com.agentops.guardian.coordinator.context.Artifact;
import com.agentops.guardian.coordinator.context.ArtifactType;
import com.agentops.guardian.coordinator.context.ExecutionRecord;
import com.agentops.guardian.coordinator.context.ExecutionStatus;
import com.agentops.guardian.coordinator.agent.AgentResult;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorResponse;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.tool.CommunicationTools;
import com.agentops.guardian.tool.CustomerDataTools;
import com.agentops.guardian.tool.ReportTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SharedContextAndAgentArchitectureTest {

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
    private ChatClient.Builder builder;
    private CustomerDataTools customerDataTools;
    private ReportTools reportTools;
    private CommunicationTools communicationTools;
    private WorkflowContextManager workflowContextManager;
    private ConversationMemory memory;
    private CustomerReportAgent reportAgent;
    private CustomerSupportAgent customerSupportAgent;
    private EmailAgent emailAgent;
    private AgentCoordinator coordinator;

    @BeforeEach
    void setUp() throws IOException {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        builder = mock(ChatClient.Builder.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(builder.build()).thenReturn(chatClient);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);

        customerDataTools = mock(CustomerDataTools.class);
        reportTools = mock(ReportTools.class);
        communicationTools = mock(CommunicationTools.class);
        workflowContextManager = mock(WorkflowContextManager.class);
        memory = new ConversationMemory();

        reportAgent = spy(new CustomerReportAgent(builder, customerDataTools, reportTools, workflowContextManager, memory));
        customerSupportAgent = mock(CustomerSupportAgent.class);
        emailAgent = spy(new EmailAgent(builder, communicationTools, workflowContextManager, memory));
        coordinator = new AgentCoordinator(builder, reportAgent, customerSupportAgent, emailAgent, memory);
    }

    private void mockIntentDecision(AgentCoordinator.IntentDecision decision) {
        when(callResponseSpec.entity(AgentCoordinator.IntentDecision.class)).thenReturn(decision);
    }

    private void mockEmailDecision(EmailAgent.EmailDecision decision) {
        when(callResponseSpec.entity(EmailAgent.EmailDecision.class)).thenReturn(decision);
    }

    // 1. A report request routes to CustomerReportAgent.
    @Test
    void coordinatorRoutesReportRequestToCustomerReportAgent() {
        mockIntentDecision(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.REPORT_REQUEST, null, null
        ));
        doReturn("Generated Boston Q3 Report").when(reportAgent).generateReport(anyString(), any(UUID.class));
        verifiedGeneratedReport("Verified Boston report data");

        AgentCoordinatorResponse response = coordinator.handle(
                new AgentCoordinatorRequest("Generate sales report for Boston", null, null)
        );

        assertEquals(AgentCoordinator.IntentAction.REPORT_REQUEST, response.action());
        assertEquals("Analytical customer report PDF saved to data/reports/test-report.pdf.", response.response());
        verify(reportAgent).execute(any(), any());
        verify(reportAgent).generateReport(anyString(), any(UUID.class));
        verifyNoInteractions(emailAgent);
    }

    // 2. A generic send request routes to EmailAgent.
    @Test
    void coordinatorRoutesGenericSendRequestToEmailAgent() {
        mockIntentDecision(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null
        ));
        mockEmailDecision(sendDecision("team@example.com", "Meeting invitation", "Please join our meeting."));
        when(callResponseSpec.content()).thenReturn("Tool call completed.");
        when(communicationTools.executionResults()).thenReturn(List.of(
                new CommunicationTools.EmailResult("msg-1", "team@example.com", true, LocalDateTime.now())
        ));

        AgentCoordinatorResponse response = coordinator.handle(
                new AgentCoordinatorRequest("Send meeting invitation", "team@example.com", null)
        );

        assertEquals(AgentCoordinator.IntentAction.EMAIL_REQUEST, response.action());
        assertTrue(response.response().contains("Reference: msg-1"));
        verify(emailAgent).execute(any(), any());
        verify(communicationTools).beginExecution();
        verify(communicationTools).clearExecutionResult();
        verifyNoInteractions(reportAgent);
    }

    // 3. A generated report is available through shared context only after verified success.
    @Test
    void generatedReportAvailableThroughSharedContextOnlyAfterVerifiedSuccess() {
        mockIntentDecision(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.REPORT_REQUEST, null, null
        ));
        doReturn("Verified report content").when(reportAgent).generateReport(anyString(), any(UUID.class));
        verifiedGeneratedReport("Verified report content");

        AgentCoordinatorResponse response = coordinator.handle(
                new AgentCoordinatorRequest("Generate report", null, null)
        );

        UUID conversationId = UUID.fromString(response.conversationId());
        Optional<Artifact> artifact = memory.getLatestArtifact(conversationId, ArtifactType.REPORT);
        assertTrue(artifact.isPresent());
        assertEquals("data/reports/test-report.pdf", artifact.get().content());
        assertEquals("data/reports/test-report.pdf",
                artifact.get().metadata().get(Artifact.FILE_REFERENCE_KEY));
        assertEquals("PDF", artifact.get().metadata().get(Artifact.FORMAT_KEY));
        assertEquals("ANALYTICAL", artifact.get().metadata().get(Artifact.REPORT_TYPE_KEY));
        assertEquals(Artifact.GENERATED_PROVENANCE, artifact.get().metadata().get(Artifact.PROVENANCE_KEY));

        List<ExecutionRecord> executions = memory.getExecutionRecords(conversationId);
        assertFalse(executions.isEmpty());
        ExecutionRecord record = executions.getLast();
        assertEquals(ExecutionStatus.SUCCESS, record.status());
        assertEquals("CustomerReportAgent", record.agentId());
    }

    // 4. A later email request can resolve that report from the same conversation.
    @Test
    void laterEmailRequestResolvesReportFromSameConversation() {
        mockIntentDecision(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.REPORT_REQUEST, null, null
        ));
        doReturn("Q3 Sales: $200k").when(reportAgent).generateReport(anyString(), any(UUID.class));
        verifiedGeneratedReport("Q3 Sales: $200k");

        AgentCoordinatorResponse reportResponse = coordinator.handle(
                new AgentCoordinatorRequest("Generate sales report", null, null)
        );

        Artifact report = memory.getLatestArtifact(
                UUID.fromString(reportResponse.conversationId()),
                ArtifactType.REPORT
        ).orElseThrow();
        mockIntentDecision(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null, null, report.id()
        ));
        doReturn(AgentResult.success("Email provider accepted. Reference: msg-2"))
                .when(emailAgent).execute(any(), any(), any());

        AgentCoordinatorResponse emailResponse = coordinator.handle(
                new AgentCoordinatorRequest(
                        "Send that report to execs@example.com",
                        null,
                        null,
                        reportResponse.conversationId()
                )
        );

        assertFalse(emailResponse.clarificationRequired());
        ArgumentCaptor<com.agentops.guardian.coordinator.dto.ReportArtifactHandoff> captor =
                ArgumentCaptor.forClass(com.agentops.guardian.coordinator.dto.ReportArtifactHandoff.class);
        verify(emailAgent).execute(any(), any(), captor.capture());
        assertEquals(report.id(), captor.getValue().artifactId());
        assertEquals("data/reports/test-report.pdf", captor.getValue().storageReference());
    }

    // 5. A report-email request without an available report asks for clarification or otherwise handles the missing dependency safely.
    @Test
    void reportEmailWithoutAvailableReportAsksForClarification() {
        mockIntentDecision(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null
        ));
        mockEmailDecision(new EmailAgent.EmailDecision(
                EmailAgent.EmailDecisionOutcome.CLARIFICATION_REQUIRED,
                "Which report should I include?",
                null, null, null, false, null
        ));

        AgentCoordinatorResponse response = coordinator.handle(
                new AgentCoordinatorRequest("Send report to boss@example.com", null, null)
        );

        assertTrue(response.clarificationRequired());
        assertTrue(response.response().contains("Which report"));
        verify(emailAgent, never()).process(any());
    }

    // 6. An ambiguous or missing recipient does not result in a send.
    @Test
    void ambiguousOrMissingRecipientDoesNotResultInSend() {
        mockIntentDecision(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null
        ));
        mockEmailDecision(sendDecision(null, "Announcement", "Here is the announcement."));

        AgentCoordinatorResponse response = coordinator.handle(
                new AgentCoordinatorRequest("Send an announcement to the team", null, null)
        );

        assertTrue(response.clarificationRequired());
        assertTrue(response.response().contains("Which email address"));
        verify(emailAgent, never()).process(any());
    }

    // 7. Drafting language alone does not trigger sending.
    @Test
    void draftingLanguageAloneDoesNotTriggerSending() {
        mockIntentDecision(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null
        ));
        mockEmailDecision(new EmailAgent.EmailDecision(
                EmailAgent.EmailDecisionOutcome.CLARIFICATION_REQUIRED,
                "Persistent drafts are not supported. Would you like me to send it?",
                null, null, null, false, null
        ));

        AgentCoordinatorResponse response = coordinator.handle(
                new AgentCoordinatorRequest("Draft an email about our product updates", "team@example.com", null)
        );

        assertTrue(response.clarificationRequired());
        assertTrue(response.response().contains("Persistent drafts are not supported"));
        verify(emailAgent, never()).process(any());
    }

    // 8. Explicit send requests use the governed communication tool.
    @Test
    void explicitSendRequestsUseGovernedCommunicationTool() {
        mockIntentDecision(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null
        ));
        mockEmailDecision(sendDecision("finance@example.com", "Invoice", "Here is the invoice."));
        when(communicationTools.executionResults()).thenReturn(List.of(
                new CommunicationTools.EmailResult("msg-101", "finance@example.com", true, LocalDateTime.now())
        ));
        when(requestSpec.user(anyString()).call().content()).thenReturn("Simulated email response");

        AgentCoordinatorResponse response = coordinator.handle(
                new AgentCoordinatorRequest("Send the meeting invoice to finance@example.com", null, null)
        );

        assertFalse(response.clarificationRequired());
        assertTrue(response.response().contains("msg-101"));
        verify(communicationTools).beginExecution();
        verify(communicationTools).clearExecutionResult();
        verify(workflowContextManager).start(eq("EmailAgent"), any(), anyString(), any(UUID.class));
        verify(workflowContextManager).complete();
    }

    // 9. Failed, blocked, or unconfirmed tool execution does not produce a success record or success claim.
    @Test
    void failedOrBlockedToolExecutionDoesNotProduceSuccessRecordOrClaim() {
        mockIntentDecision(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.REPORT_REQUEST, null, null
        ));
        doThrow(new GovernanceViolationException("Blocked by data governance policy"))
                .when(reportAgent).generateReport(anyString(), any(UUID.class));

        UUID conversationId = memory.open(null);
        assertThrows(GovernanceViolationException.class, () -> coordinator.handle(
                new AgentCoordinatorRequest("Generate PII report", null, null, conversationId.toString())
        ));

        assertTrue(memory.getLatestArtifact(conversationId, ArtifactType.REPORT).isEmpty());
        List<ExecutionRecord> records = memory.getExecutionRecords(conversationId);
        assertFalse(records.isEmpty());
        assertEquals(ExecutionStatus.BLOCKED_BY_GOVERNANCE, records.getLast().status());
    }

    // 10. Conversation history and artifact access remain isolated by conversation ID.
    @Test
    void conversationHistoryAndArtifactAccessRemainIsolatedByConversationId() {
        UUID sessionA = memory.open(null);
        UUID sessionB = memory.open(null);

        Artifact artifactA = memory.registerArtifact(sessionA, Artifact.report("Report A", "Confidential Data A"));

        // Session B cannot retrieve session A's artifact even with exact ID
        Optional<Artifact> crossAccess = memory.getArtifact(sessionB, artifactA.id());
        assertTrue(crossAccess.isEmpty(), "Cross-session artifact retrieval must be blocked");

        Optional<Artifact> latestB = memory.getLatestArtifact(sessionB, ArtifactType.REPORT);
        assertTrue(latestB.isEmpty(), "Session B must have no report artifacts");
    }

    // 11. Successful assistant responses are recorded appropriately for follow-up context.
    @Test
    void successfulAssistantResponsesRecordedForFollowUpContext() {
        mockIntentDecision(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.GENERAL_RESPONSE, null, null
        ));
        when(customerSupportAgent.chat(any(UUID.class), eq("Hello there")))
                .thenReturn("Hello, how can I help you today?");

        AgentCoordinatorResponse response = coordinator.handle(
                new AgentCoordinatorRequest("Hello there", null, null)
        );

        UUID conversationId = UUID.fromString(response.conversationId());
        List<Message> history = memory.history(conversationId);
        assertFalse(history.isEmpty());
        assertTrue(history.stream().anyMatch(m -> m.getText().contains("Hello there")));
        assertTrue(history.stream().anyMatch(m -> m.getText().contains("Hello, how can I help you today?")));
        assertTrue(memory.history(memory.open(null)).isEmpty());
    }

    // 12. Existing memory limits, expiration, and governance tests continue to pass.
    @Test
    void conversationMemorySafeguardsAndLimitsPreserved() {
        UUID conversationId = memory.open(null);

        for (int i = 0; i < 15; i++) {
            memory.recordTurn(conversationId, "User turn " + i, "Assistant turn " + i);
        }
        List<Message> history = memory.history(conversationId);
        assertTrue(history.size() <= 12, "Chat history must not exceed message window limit of 12");

        String oversizedContent = "A".repeat(30001);
        assertThrows(IllegalArgumentException.class, () ->
                memory.registerArtifact(conversationId, Artifact.report("Large", oversizedContent))
        );

        memory.recordTurn(conversationId, "My email is user@example.com and phone is +1 555-123-4567", null);
        List<Message> updatedHistory = memory.history(conversationId);
        String lastTurnText = updatedHistory.getLast().getText();
        assertFalse(lastTurnText.contains("user@example.com"));
        assertFalse(lastTurnText.contains("555-123-4567"));
        assertTrue(lastTurnText.contains("[redacted]"));
    }

    private void verifiedGeneratedReport(String content) {
        when(reportTools.executionResults()).thenReturn(List.of(
                new ReportTools.Report(
                        ReportTools.ReportType.ANALYTICAL,
                        false,
                        1,
                        content,
                        "data/reports/test-report.pdf",
                        128
                )
        ));
    }

    private EmailAgent.EmailDecision sendDecision(String recipient, String subject, String body) {
        return sendDecision(recipient, subject, body, null);
    }

    private EmailAgent.EmailDecision sendDecision(
            String recipient,
            String subject,
            String body,
            String reportReference
    ) {
        return new EmailAgent.EmailDecision(
                EmailAgent.EmailDecisionOutcome.SEND,
                null,
                recipient,
                subject,
                body,
                reportReference != null,
                reportReference
        );
    }
}
