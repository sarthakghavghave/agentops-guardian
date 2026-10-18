package com.agentops.guardian.coordinator;

import com.agentops.guardian.agent.CustomerReportAgent;
import com.agentops.guardian.agent.CustomerSupportAgent;
import com.agentops.guardian.agent.EmailAgent;
import com.agentops.guardian.coordinator.agent.AgentResult;
import com.agentops.guardian.coordinator.context.Artifact;
import com.agentops.guardian.coordinator.context.ArtifactType;
import com.agentops.guardian.coordinator.context.ExecutionRecord;
import com.agentops.guardian.coordinator.context.ExecutionStatus;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorResponse;
import com.agentops.guardian.coordinator.dto.ReportArtifactHandoff;
import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.audit.AuditService;
import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.exception.ApprovalRequiredException;
import com.agentops.guardian.governance.execution.ApprovedActionGrant;
import com.agentops.guardian.governance.intervention.GovernanceIntervention;
import com.agentops.guardian.governance.intervention.GovernanceInterventionStatus;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.tool.ReportTools;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AgentCoordinatorTest {

    @Test
    void reportMetadataQuestionReturnsRegisteredMetadataWithoutGeneratingReport() {
        Fixture fixture = new Fixture(action(AgentCoordinator.IntentAction.REPORT_METADATA_REQUEST));
        UUID conversationId = fixture.memory.open(null);
        Artifact report = fixture.memory.registerArtifact(
                conversationId,
                Artifact.generatedPdfReport("Monthly Customer Report", "data/reports/monthly.pdf", 2048, 42)
        );

        AgentCoordinatorResponse response = fixture.coordinator.handle(new AgentCoordinatorRequest(
                "What is the report ID of the report you created?",
                null,
                null,
                conversationId.toString()
        ));

        assertFalse(response.clarificationRequired());
        assertTrue(response.response().contains("Report ID: " + report.id()));
        assertTrue(response.response().contains("Title: Monthly Customer Report"));
        assertTrue(response.response().contains("Created: " + report.createdAt()));
        assertTrue(response.response().contains("Availability: available"));
        assertTrue(response.response().contains("Format: PDF"));
        assertTrue(response.response().contains("Customers analyzed: 42"));
        verifyNoInteractions(fixture.reportAgent, fixture.emailAgent);
    }

    @Test
    void reportMetadataQuestionExplainsWhenNoGeneratedReportIsAvailable() {
        Fixture fixture = new Fixture(action(AgentCoordinator.IntentAction.REPORT_METADATA_REQUEST));

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Is a report available?", null, null)
        );

        assertFalse(response.clarificationRequired());
        assertTrue(response.response().contains("No generated report is available"));
        verifyNoInteractions(fixture.reportAgent, fixture.emailAgent);
    }

    @Test
    void reportMetadataQuestionClarifiesWhenMultipleReportsAreAvailable() {
        Fixture fixture = new Fixture(action(AgentCoordinator.IntentAction.REPORT_METADATA_REQUEST));
        UUID conversationId = fixture.memory.open(null);
        Artifact first = fixture.memory.registerArtifact(
                conversationId,
                Artifact.generatedPdfReport("April Report", "data/reports/april.pdf", 1024, 10)
        );
        Artifact second = fixture.memory.registerArtifact(
                conversationId,
                Artifact.generatedPdfReport("May Report", "data/reports/may.pdf", 1536, 12)
        );

        AgentCoordinatorResponse response = fixture.coordinator.handle(new AgentCoordinatorRequest(
                "What is the title of the report?",
                null,
                null,
                conversationId.toString()
        ));

        assertTrue(response.clarificationRequired());
        assertTrue(response.response().contains("Which report do you mean?"));
        assertTrue(response.response().contains(first.title()));
        assertTrue(response.response().contains(second.title()));
        assertTrue(response.response().contains(first.id()));
        assertTrue(response.response().contains(second.id()));
        verifyNoInteractions(fixture.reportAgent, fixture.emailAgent);
    }

    @Test
    void reportCreationRequestStillRunsCustomerReportAgent() {
        Fixture fixture = new Fixture(action(AgentCoordinator.IntentAction.REPORT_REQUEST));
        when(fixture.reportAgent.generateReport(anyString(), any(UUID.class))).thenReturn("Model report output");
        fixture.verifiedGeneratedReport("Verified report output");

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Generate an analytical customer report for North Michael", null, null)
        );

        assertFalse(response.clarificationRequired());
        assertTrue(response.response().contains("Analytical customer report PDF saved"));
        verify(fixture.reportAgent).execute(any(), any());
        verifyNoInteractions(fixture.emailAgent, fixture.customerSupportAgent);
    }

    @Test
    void customerDataQuestionUsesSupportRetrievalWithoutGeneratingReport() {
        Fixture fixture = new Fixture(action(AgentCoordinator.IntentAction.GENERAL_RESPONSE));
        when(fixture.customerSupportAgent.chat(any(UUID.class), anyString()))
                .thenReturn("Customer data for North Michael: 12 matching records.");

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("give me data of customers in North Michael", null, null)
        );

        assertFalse(response.clarificationRequired());
        assertEquals("Customer data for North Michael: 12 matching records.", response.response());
        verify(fixture.customerSupportAgent).chat(any(UUID.class),
                eq("give me data of customers in North Michael"));
        verifyNoInteractions(fixture.reportAgent, fixture.emailAgent);

        ArgumentCaptor<String> routingPrompt = ArgumentCaptor.forClass(String.class);
        verify(fixture.requestSpec).system(routingPrompt.capture());
        String normalizedPrompt = routingPrompt.getValue().replaceAll("\\s+", " ");
        assertTrue(normalizedPrompt.contains("not report generation"));
        assertTrue(normalizedPrompt.contains("CustomerSupportAgent can use its registered retrieval tools"));
    }

    @Test
    void recordsActualCustomerSupportResponseIncludingRetrievedCustomerEmail() {
        Fixture fixture = new Fixture(action(AgentCoordinator.IntentAction.GENERAL_RESPONSE));
        String customerEmail = "alex.morgan@example.test";
        String retrievedResponse = "Customer: Alex Morgan; Email: " + customerEmail
                + "; City: North Michael.";
        when(fixture.customerSupportAgent.chat(any(UUID.class), anyString()))
                .thenReturn(retrievedResponse);

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Find Alex Morgan's customer record", null, null)
        );

        UUID conversationId = UUID.fromString(response.conversationId());
        assertEquals(retrievedResponse, response.response());
        assertEquals(retrievedResponse, fixture.memory.history(conversationId).getLast().getText());
    }

    @Test
    void reportArtifactCanBeHandedToEmailAgentInSameConversation() throws IOException {
        Fixture fixture = new Fixture(action(AgentCoordinator.IntentAction.REPORT_REQUEST));
        when(fixture.reportAgent.generateReport(anyString(), any(UUID.class))).thenReturn("Report summary");
        fixture.verifiedGeneratedReport("Report data from tool");
        AgentCoordinatorResponse reportResponse = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Generate sales report", null, null)
        );

        fixture.decision(action(AgentCoordinator.IntentAction.EMAIL_REQUEST));
        doReturn(AgentResult.success("Email provider accepted the submission."))
                .when(fixture.emailAgent).execute(any(), any());
        AgentCoordinatorResponse emailResponse = fixture.coordinator.handle(new AgentCoordinatorRequest(
                "Please email that report to finance@example.com",
                null,
                null,
                reportResponse.conversationId()
        ));

        assertFalse(emailResponse.clarificationRequired());
        ArgumentCaptor<UUID> conversation = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<AgentCoordinatorRequest> emailRequest = ArgumentCaptor.forClass(AgentCoordinatorRequest.class);
        verify(fixture.emailAgent).execute(conversation.capture(), emailRequest.capture());
        assertEquals(UUID.fromString(reportResponse.conversationId()), conversation.getValue());
        assertEquals("Please email that report to finance@example.com", emailRequest.getValue().message());
        assertTrue(fixture.memory.getLatestArtifact(conversation.getValue(), ArtifactType.REPORT).isPresent());
    }

    @Test
    void coordinatorConstructsTypedHandoffFromRegisteredArtifactForEmailAttachment() {
        Fixture fixture = new Fixture(action(AgentCoordinator.IntentAction.EMAIL_REQUEST));
        UUID conversationId = fixture.memory.open(null);
        Artifact report = fixture.memory.registerArtifact(
                conversationId,
                Artifact.generatedPdfReport("Monthly Customer Report", "data/reports/monthly.pdf", 2048, 42)
        );
        fixture.decision(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.EMAIL_REQUEST,
                null,
                null,
                null,
                report.id()
        ));
        doReturn(AgentResult.success("Email provider accepted the submission."))
                .when(fixture.emailAgent).execute(any(), any(), any());

        AgentCoordinatorResponse response = fixture.coordinator.handle(new AgentCoordinatorRequest(
                "Email the report as an attachment to finance@example.test",
                "finance@example.test",
                null,
                conversationId.toString()
        ));

        assertFalse(response.clarificationRequired());
        ArgumentCaptor<ReportArtifactHandoff> handoff = ArgumentCaptor.forClass(ReportArtifactHandoff.class);
        verify(fixture.emailAgent).execute(eq(conversationId), any(), handoff.capture());
        assertEquals(report.id(), handoff.getValue().artifactId());
        assertEquals(report.title(), handoff.getValue().title());
        assertEquals("ANALYTICAL", handoff.getValue().reportType());
        assertEquals("PDF", handoff.getValue().format());
        assertEquals("data/reports/monthly.pdf", handoff.getValue().storageReference());
        assertEquals(report.createdAt(), handoff.getValue().createdAt());
        assertEquals(report.metadata(), handoff.getValue().metadata());
        verifyNoInteractions(fixture.reportAgent);
    }

    @Test
    void coordinatorRejectsUnregisteredReportHandoffWithoutCallingEmailAgent() {
        Fixture fixture = new Fixture(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.EMAIL_REQUEST,
                null,
                null,
                null,
                "forged-artifact-id"
        ));

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Attach that report to my email", "finance@example.test", null)
        );

        assertTrue(response.clarificationRequired());
        assertTrue(response.response().contains("not available"));
        verifyNoInteractions(fixture.emailAgent, fixture.reportAgent);
    }

    @Test
    void routesEmailToEmailAgentWithoutCoordinatorEmailIntentClassification() {
        Fixture fixture = new Fixture(action(AgentCoordinator.IntentAction.EMAIL_REQUEST));
        doReturn(AgentResult.success("Email provider accepted the submission."))
                .when(fixture.emailAgent).execute(any(), any());

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Yes, send the invitation", "team@example.com", null)
        );

        assertEquals(AgentCoordinator.IntentAction.EMAIL_REQUEST, response.action());
        assertFalse(response.clarificationRequired());
        verify(fixture.emailAgent).execute(any(), any());
        verifyNoInteractions(fixture.reportAgent);
    }

    @Test
    void routesEmailConfirmationWithSharedConversationHistoryToEmailAgent() {
        Fixture fixture = new Fixture(action(AgentCoordinator.IntentAction.EMAIL_REQUEST));
        UUID conversationId = fixture.memory.open(null);
        fixture.memory.recordTurn(conversationId, "Please send the agenda.", "Should I send it now?");
        doReturn(AgentResult.clarification("Please provide the recipient."))
                .when(fixture.emailAgent).execute(any(), any());

        fixture.coordinator.handle(new AgentCoordinatorRequest("Yes", null, null, conversationId.toString()));

        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(fixture.requestSpec).messages(messages.capture());
        assertTrue(messages.getValue().stream().anyMatch(message ->
                message.getText().contains("Should I send it now?")));
        assertTrue(messages.getValue().stream().anyMatch(message -> message.getText().equals("Yes")));
        verify(fixture.emailAgent).execute(eq(conversationId), any());
    }

    @Test
    void preservesClarificationTurnsInSharedConversationHistory() {
        String question = "Which date range should I use?";
        Fixture fixture = new Fixture(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.CLARIFICATION_REQUIRED,
                null,
                question
        ));

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Generate a report", null, null)
        );

        assertTrue(response.clarificationRequired());
        assertEquals(question, response.response());
        List<Message> history = fixture.memory.history(UUID.fromString(response.conversationId()));
        assertTrue(history.stream().anyMatch(message -> message.getText().equals("Generate a report")));
        assertTrue(history.stream().anyMatch(message -> message.getText().equals(question)));
    }

    @Test
    void rejectsAgentIncompatibleWithRoutingAction() {
        Fixture emailToReport = new Fixture(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null, "CUSTOMER_REPORT"
        ));
        assertThrows(IllegalArgumentException.class, () -> emailToReport.coordinator.handle(
                new AgentCoordinatorRequest("Email the agenda", "team@example.com", null)
        ));
        verifyNoInteractions(emailToReport.emailAgent, emailToReport.reportAgent);

        Fixture reportToEmail = new Fixture(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.REPORT_REQUEST, null, null, "EMAIL"
        ));
        assertThrows(IllegalArgumentException.class, () -> reportToEmail.coordinator.handle(
                new AgentCoordinatorRequest("Generate a report", null, null)
        ));
        verifyNoInteractions(reportToEmail.emailAgent, reportToEmail.reportAgent);
    }

    @Test
    void everyAgentResultStatusIsHandledWithoutFalseSuccess() {
        AgentCoordinatorRequest request = new AgentCoordinatorRequest("Send an invitation", "team@example.com", null);

        Fixture success = new Fixture(action(AgentCoordinator.IntentAction.EMAIL_REQUEST));
        doReturn(AgentResult.success("Email provider accepted the submission."))
                .when(success.emailAgent).execute(any(), any());
        assertFalse(success.coordinator.handle(request).clarificationRequired());

        Fixture clarification = new Fixture(action(AgentCoordinator.IntentAction.EMAIL_REQUEST));
        doReturn(AgentResult.clarification("Should I send the invitation?"))
                .when(clarification.emailAgent).execute(any(), any());
        AgentCoordinatorResponse clarificationResponse = clarification.coordinator.handle(request);
        assertTrue(clarificationResponse.clarificationRequired());
        assertEquals("Should I send the invitation?", clarificationResponse.response());

        Fixture failure = new Fixture(action(AgentCoordinator.IntentAction.EMAIL_REQUEST));
        doReturn(AgentResult.failure("Email operation failed"))
                .when(failure.emailAgent).execute(any(), any());
        assertThrows(IllegalStateException.class, () -> failure.coordinator.handle(request));

        Fixture dependency = new Fixture(action(AgentCoordinator.IntentAction.EMAIL_REQUEST));
        doReturn(AgentResult.needsDependency("CustomerReportAgent", "A report is needed"))
                .when(dependency.emailAgent).execute(any(), any());
        assertTrue(assertThrows(IllegalStateException.class, () -> dependency.coordinator.handle(request))
                .getMessage().contains("not supported"));

        Fixture emptySuccess = new Fixture(action(AgentCoordinator.IntentAction.EMAIL_REQUEST));
        doReturn(AgentResult.success(" "))
                .when(emptySuccess.emailAgent).execute(any(), any());
        assertThrows(IllegalStateException.class, () -> emptySuccess.coordinator.handle(request));
    }

    @Test
    void successAndClarificationAssistantTurnsAreStoredButIsolated() {
        Fixture fixture = new Fixture(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.GENERAL_RESPONSE, null, null
        ));
        when(fixture.customerSupportAgent.chat(any(UUID.class), eq("Hello")))
                .thenReturn("How can I help?");
        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Hello", null, null)
        );

        UUID conversationId = UUID.fromString(response.conversationId());
        List<Message> history = fixture.memory.history(conversationId);
        assertTrue(history.stream().anyMatch(message -> message.getText().equals("Hello")));
        assertTrue(history.stream().anyMatch(message -> message.getText().equals("How can I help?")));
        assertTrue(fixture.memory.history(fixture.memory.open(null)).isEmpty());
    }

    @Test
    void validatesExplicitRecipientBeforeRouting() {
        Fixture fixture = new Fixture(action(AgentCoordinator.IntentAction.EMAIL_REQUEST));
        assertThrows(IllegalArgumentException.class, () -> fixture.coordinator.handle(
                new AgentCoordinatorRequest("Send the invitation", "not-an-email", null)
        ));
        verifyNoInteractions(fixture.emailAgent);
    }

    @Test
    void failedReportExecutionDoesNotRegisterArtifact() {
        Fixture fixture = new Fixture(action(AgentCoordinator.IntentAction.REPORT_REQUEST));
        when(fixture.reportAgent.generateReport(anyString(), any(UUID.class)))
                .thenThrow(new IllegalStateException("Report failed."));
        UUID conversationId = fixture.memory.open(null);

        assertThrows(IllegalStateException.class, () -> fixture.coordinator.handle(
                new AgentCoordinatorRequest("Generate a report", null, null, conversationId.toString())
        ));
        assertTrue(fixture.memory.getLatestArtifact(conversationId, ArtifactType.REPORT).isEmpty());
        Optional<ExecutionRecord> execution = fixture.memory.getLatestExecution(conversationId);
        assertTrue(execution.isPresent());
        assertEquals(ExecutionStatus.FAILED, execution.get().status());
    }

    @Test
    void approvalResumesTheOriginalAgentTaskOnceWithItsConversation() {
        Fixture fixture = new Fixture(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null, "EMAIL"
        ));
        when(fixture.emailAgent.execute(any(UUID.class), any(AgentCoordinatorRequest.class)))
                .thenAnswer(invocation -> {
                    UUID conversationId = invocation.getArgument(0);
                    throw new ApprovalRequiredException(pendingIntervention(conversationId), "{}");
                })
                .thenReturn(AgentResult.success("Email completed."));
        when(fixture.emailAgent.resumeApprovedAction(
                any(UUID.class), anyString(), anyString(), anyString(), anyString(), anyString(), any()
        )).thenReturn(AgentResult.success("Email completed."));

        AgentCoordinatorResponse pending = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Send the requested email", null, null)
        );

        assertTrue(pending.approvalRequired());
        assertNotNull(pending.interventionId());
        UUID conversationId = UUID.fromString(pending.conversationId());
        GovernanceIntervention unrelated = pendingIntervention(conversationId, "unrelated-intervention")
                .approve("reviewer", "Approved.", java.time.Instant.now());
        assertEquals("FAILED", fixture.coordinator.resumeApproved(unrelated).status());
        GovernanceIntervention approved = pendingIntervention(conversationId)
                .approve("reviewer", "Approved.", java.time.Instant.now());

        AgentCoordinator.TaskResumeResult result = fixture.coordinator.resumeApproved(approved);

        assertEquals("COMPLETED", result.status());
        assertEquals("Email completed.", result.message());
        verify(fixture.emailAgent, times(1)).execute(eq(conversationId), any(AgentCoordinatorRequest.class));
        verify(fixture.emailAgent).resumeApprovedAction(
                eq(conversationId),
                eq("workflow-approval-1"),
                eq("intervention-resume-1"),
                eq("send-call-1"),
                eq("sendEmail"),
                eq("{}"),
                isNull()
        );
        assertTrue(fixture.memory.history(conversationId).stream()
                .anyMatch(message -> message.getText().contains("Email completed.")));

        AgentCoordinator.TaskResumeResult duplicate = fixture.coordinator.resumeApproved(approved);
        assertEquals("FAILED", duplicate.status());
        verify(fixture.emailAgent, times(1)).execute(any(UUID.class), any(AgentCoordinatorRequest.class));
        verify(fixture.auditService, times(2)).record(argThat(event ->
                event.eventType() == com.agentops.guardian.governance.audit.AuditEventType.EXECUTION_OUTCOME
                        && "RESUME_UNAVAILABLE".equals(event.errorType())
                        && event.interventionId() != null
        ));
    }

    @Test
    void approvedContinuationUsesCapturedArgumentsInsteadOfRerunningTheAgent() {
        Fixture fixture = new Fixture(new AgentCoordinator.IntentDecision(
                AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null, "EMAIL"
        ));
        UUID conversationId = fixture.memory.open(null);
        String arguments = "{\"recipient\":\"approved@example.test\"}";
        GovernanceIntervention originalIntervention = pendingIntervention(
                conversationId,
                "original-intervention",
                arguments
        );
        when(fixture.emailAgent.execute(any(UUID.class), any(AgentCoordinatorRequest.class)))
                .thenThrow(new ApprovalRequiredException(originalIntervention, arguments));
        when(fixture.emailAgent.resumeApprovedAction(
                any(UUID.class), anyString(), anyString(), anyString(), anyString(), anyString(), any()
        )).thenReturn(AgentResult.success("Approved action completed."));

        AgentCoordinatorResponse pending = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Send a different follow-up", null, null, conversationId.toString())
        );
        GovernanceIntervention approved = originalIntervention.approve(
                "reviewer", "Approved.", java.time.Instant.now()
        );

        AgentCoordinator.TaskResumeResult result = fixture.coordinator.resumeApproved(approved);

        assertEquals("COMPLETED", result.status());
        assertEquals("Approved action completed.", result.message());
        verify(fixture.emailAgent).resumeApprovedAction(
                eq(conversationId),
                eq(originalIntervention.workflowId()),
                eq("original-intervention"),
                eq("send-call-1"),
                eq("sendEmail"),
                eq(arguments),
                isNull()
        );
    }

    @Test
    void approvedInterventionWithoutPendingContextIsAuditedAsUnavailable() {
        Fixture fixture = new Fixture(action(AgentCoordinator.IntentAction.EMAIL_REQUEST));
        UUID conversationId = UUID.randomUUID();
        GovernanceIntervention approved = pendingIntervention(conversationId)
                .approve("reviewer", "Approved.", java.time.Instant.now());

        AgentCoordinator.TaskResumeResult result = fixture.coordinator.resumeApproved(approved);

        assertEquals("FAILED", result.status());
        assertTrue(result.message().contains("unavailable"));
        verify(fixture.auditService).record(argThat(event ->
                event.executionStatus() == AuditEvent.ExecutionStatus.FAILED
                        && event.conversationId().equals(conversationId)
                        && event.workflowId().equals(approved.workflowId())
        ));
        verifyNoInteractions(fixture.emailAgent);
    }

    private GovernanceIntervention pendingIntervention(UUID conversationId) {
        return pendingIntervention(conversationId, "intervention-resume-1");
    }

    private GovernanceIntervention pendingIntervention(UUID conversationId, String interventionId) {
        return pendingIntervention(conversationId, interventionId, "{}");
    }

    private GovernanceIntervention pendingIntervention(
            UUID conversationId,
            String interventionId,
            String arguments
    ) {
        return new GovernanceIntervention(
                interventionId,
                "workflow-approval-1",
                "send-call-1",
                "EmailAgent",
                WorkflowType.EMAIL_COMMUNICATION,
                WorkflowCapability.SEND_EMAIL,
                RiskLevel.HIGH,
                com.agentops.guardian.governance.risk.GovernanceIntervention.REQUIRE_APPROVAL,
                GovernanceInterventionStatus.PENDING,
                "External communication requires approval.",
                java.time.Instant.now(),
                null,
                null,
                null,
                "EMAIL",
                WorkflowState.REPORT_GENERATED,
                com.agentops.guardian.governance.model.DataClassification.ANALYTICAL,
                conversationId,
                "sendEmail",
                ApprovedActionGrant.argumentsDigest(arguments)
        );
    }

    private AgentCoordinator.IntentDecision action(AgentCoordinator.IntentAction action) {
        return new AgentCoordinator.IntentDecision(action, null, null);
    }

    private static class Fixture {
        private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        private final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        private final ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        private final ChatClient.Builder builder = mock(ChatClient.Builder.class);
        private final CustomerReportAgent reportAgent = mock(CustomerReportAgent.class);
        private final CustomerSupportAgent customerSupportAgent = mock(CustomerSupportAgent.class);
        private final EmailAgent emailAgent = mock(EmailAgent.class);
        private final ConversationMemory memory = new ConversationMemory();
        private final AuditService auditService = mock(AuditService.class);
        private final PendingAgentTaskRegistry pendingTasks = new PendingAgentTaskRegistry();
        private final ApprovedActionGrant approvedActionGrant = new ApprovedActionGrant();
        private final ReportTools reportTools = mock(ReportTools.class);
        private final AgentCoordinator coordinator;

        private Fixture(AgentCoordinator.IntentDecision decision) {
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(anyString())).thenReturn(requestSpec);
            when(requestSpec.messages(anyList())).thenReturn(requestSpec);
            when(requestSpec.user(anyString())).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(builder.build()).thenReturn(chatClient);
            when(builder.defaultSystem(anyString())).thenReturn(builder);
            when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
            try {
                CustomerReportAgent realReportAgent = new CustomerReportAgent(
                        builder,
                        mock(com.agentops.guardian.tool.CustomerDataTools.class),
                        reportTools,
                        mock(com.agentops.guardian.governance.context.WorkflowContextManager.class),
                        memory
                );
                when(reportAgent.execute(any(), any())).thenAnswer(inv ->
                        realReportAgent.execute(inv.getArgument(0), inv.getArgument(1), reportAgent)
                );
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            coordinator = new AgentCoordinator(
                    builder,
                    reportAgent,
                    customerSupportAgent,
                    emailAgent,
                    memory,
                    pendingTasks,
                    approvedActionGrant,
                    auditService
            );
            decision(decision);
        }

        private void decision(AgentCoordinator.IntentDecision decision) {
            when(callResponseSpec.entity(AgentCoordinator.IntentDecision.class)).thenReturn(decision);
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
    }
}
