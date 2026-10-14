package com.agentops.guardian.agent;

import com.agentops.guardian.coordinator.ConversationMemory;
import com.agentops.guardian.coordinator.agent.AgentResult;
import com.agentops.guardian.coordinator.context.Artifact;
import com.agentops.guardian.coordinator.context.ArtifactType;
import com.agentops.guardian.coordinator.context.ExecutionStatus;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.coordinator.dto.ReportArtifactHandoff;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.context.ConversationHistoryAccessService;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowDefinitionRegistry;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.tool.CommunicationTools;
import com.agentops.guardian.tool.ReportFileStorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EmailAgentTest {

    @TempDir
    Path tempDirectory;

    @Test
    void llmClarificationForDraftRequestNeverCallsCommunicationTool() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        ChatClient chatClient = mockClient("Should not be called");
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                .thenReturn(new EmailAgent.EmailDecision(
                        EmailAgent.EmailDecisionOutcome.CLARIFICATION_REQUIRED,
                        "Persistent drafts are not supported. Would you like me to send it?",
                        null, null, null, false, null
                ));
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        CommunicationTools tools = mock(CommunicationTools.class);
        EmailAgent agent = new EmailAgent(builder, tools, manager);
        clearInvocations(chatClient.prompt());

        AgentResult result = agent.execute(
                UUID.randomUUID(),
                new AgentCoordinatorRequest("Draft an invitation for team meeting", "team@example.com", null)
        );

        assertTrue(result.isClarification());
        assertEquals("Persistent drafts are not supported. Would you like me to send it?",
                result.clarificationQuestion());
        verifyNoInteractions(tools);
        verify(chatClient.prompt(), never()).user(anyString());
    }

    @Test
    void llmDecisionUsesHistoryAndAuthorizesConversationalConfirmation() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        ChatClient chatClient = mockClient("Model response is not delivery proof.");
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                .thenReturn(sendDecision("operator@example.test", "Meeting invitation", "Please join our meeting tomorrow."));
        CommunicationTools tools = mock(CommunicationTools.class);
        when(tools.executionResults()).thenReturn(List.of(new CommunicationTools.EmailResult(
                "message-confirmed", "operator@example.test", true, java.time.LocalDateTime.now()
        )));
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        memory.recordTurn(conversationId, "Please send the agenda to operator@example.test", "Should I send it?");
        EmailAgent agent = new EmailAgent(builder, tools, manager, memory);
        clearInvocations(chatClient.prompt());

        AgentResult result = agent.execute(
                conversationId,
                new AgentCoordinatorRequest(
                        "Yes, send it",
                        "operator@example.test",
                        null,
                        null,
                        "Meeting invitation",
                        "Please join our meeting tomorrow."
                )
        );

        assertTrue(result.isSuccess());
        assertTrue(result.response().contains("simulated email request"));
        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(chatClient.prompt()).messages(messages.capture());
        assertTrue(messages.getValue().stream().anyMatch(message ->
                message.getText().contains("Yes, send it")));
        assertTrue(messages.getValue().stream().anyMatch(message ->
                message.getText().contains("Please send the agenda")));
        verify(tools).beginExecution();
        verify(tools).clearExecutionResult();
    }

    @Test
    void laterEmailDecisionCanUseUnredactedCustomerDetailsFromSharedHistoryThroughGovernedSend()
            throws IOException {
        String customerEmail = "alex.morgan@example.test";
        String customerResponse = "Customer: Alex Morgan; Email: " + customerEmail
                + "; City: North Michael.";
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        ChatClient chatClient = mockClient("Tool invocation result.");
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                .thenReturn(sendDecision(
                        customerEmail,
                        "Customer follow-up",
                        "Following up with Alex Morgan at " + customerEmail + "."
                ));
        CommunicationTools tools = mock(CommunicationTools.class);
        when(tools.executionResults()).thenReturn(List.of(new CommunicationTools.EmailResult(
                "customer-email-accepted",
                customerEmail,
                true,
                java.time.LocalDateTime.now()
        )));
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        memory.recordTurn(conversationId, "Find Alex Morgan's customer record", customerResponse);
        EmailAgent agent = new EmailAgent(builder, tools, manager, memory);
        clearInvocations(chatClient.prompt());

        AgentResult result = agent.execute(
                conversationId,
                new AgentCoordinatorRequest("Email Alex about the account", null, null)
        );

        assertTrue(result.isSuccess());
        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(chatClient.prompt()).messages(messages.capture());
        assertTrue(messages.getValue().stream().anyMatch(message ->
                message.getText().equals(customerResponse)));
        verify(manager).start(
                eq("EmailAgent"),
                eq(WorkflowType.EMAIL_COMMUNICATION),
                argThat(goal -> goal.contains(customerEmail)),
                eq(conversationId)
        );
        verify(tools).beginExecution();
        verify(tools).executionResults();
        verify(tools).clearExecutionResult();
    }

    @Test
    void deniedHistoryAccessExcludesTranscriptButKeepsCurrentRequestAndReportMetadata() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        ChatClient chatClient = mockClient("No send");
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                .thenReturn(new EmailAgent.EmailDecision(
                        EmailAgent.EmailDecisionOutcome.CLARIFICATION_REQUIRED,
                        "Please clarify.",
                        null, null, null, false, null
                ));
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        String privateTranscript = "Prior customer email: private.customer@example.test";
        memory.recordTurn(conversationId, "Look up this customer's record", privateTranscript);
        Artifact report = Artifact.generatedPdfReport("Quarterly report", "reports/q1.pdf", 1234, 12);
        memory.registerArtifact(conversationId, report);
        ConversationHistoryAccessService accessService = mock(ConversationHistoryAccessService.class);
        when(accessService.readHistory(conversationId)).thenReturn(
                new ConversationHistoryAccessService.HistoryAccessResult(
                        new GovernanceDecision(
                                GovernanceDecision.DecisionType.BLOCK,
                                "Customer data was previously retrieved in this conversation.",
                                "BLOCK_EMAIL_HISTORY_AFTER_CUSTOMER_READ"
                        ),
                        List.of()
                )
        );
        EmailAgent agent = new EmailAgent(
                builder,
                mock(CommunicationTools.class),
                mock(WorkflowContextManager.class),
                memory,
                null,
                accessService
        );
        clearInvocations(chatClient.prompt());

        AgentResult result = agent.execute(
                conversationId,
                new AgentCoordinatorRequest("Email the quarterly report", null, null)
        );

        assertTrue(result.isClarification());
        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(chatClient.prompt()).messages(messages.capture());
        String promptText = messages.getValue().stream().map(Message::getText).reduce("", (left, right) ->
                left + "\n" + right);
        assertFalse(promptText.contains(privateTranscript));
        assertTrue(promptText.contains("Email the quarterly report"));
        assertTrue(promptText.contains(report.id()));
        assertTrue(promptText.contains("Shared conversation history is unavailable"));
        verify(accessService).readHistory(conversationId);
    }

    @Test
    void governanceDisabledHistoryAccessPreservesExistingTranscriptBehavior() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        ChatClient chatClient = mockClient("No send");
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                .thenReturn(new EmailAgent.EmailDecision(
                        EmailAgent.EmailDecisionOutcome.CLARIFICATION_REQUIRED,
                        "Please clarify.",
                        null, null, null, false, null
                ));
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        String privateTranscript = "Prior customer email: private.customer@example.test";
        memory.recordTurn(conversationId, "Earlier request", privateTranscript);
        ConversationHistoryAccessService accessService =
                new ConversationHistoryAccessService(null, null, null, memory, false);
        EmailAgent agent = new EmailAgent(
                builder,
                mock(CommunicationTools.class),
                mock(WorkflowContextManager.class),
                memory,
                null,
                accessService
        );
        clearInvocations(chatClient.prompt());

        agent.execute(conversationId, new AgentCoordinatorRequest("Please send it", null, null));

        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(chatClient.prompt()).messages(messages.capture());
        assertTrue(messages.getValue().stream().anyMatch(message ->
                message.getText().contains(privateTranscript)));
    }

    @Test
    void llmRefusalAndUnsupportedDecisionsNeverInvokeCommunicationTool() throws IOException {
        assertNonSendDecisionDoesNotCallTool(new EmailAgent.EmailDecision(
                EmailAgent.EmailDecisionOutcome.CLARIFICATION_REQUIRED,
                "Understood. I will not send it.",
                null, null, null, false, null
        ));
        assertNonSendDecisionDoesNotCallTool(new EmailAgent.EmailDecision(
                EmailAgent.EmailDecisionOutcome.UNSUPPORTED,
                "I cannot send this type of message.",
                null, null, null, false, null
        ));
        assertNonSendDecisionDoesNotCallTool(new EmailAgent.EmailDecision(
                EmailAgent.EmailDecisionOutcome.CLARIFICATION_REQUIRED,
                "Do you want me to send this, or only prepare the text?",
                null, null, null, false, null
        ));
    }

    @Test
    void missingRecipientAndReportArtifactDoNotStartCommunication() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        ChatClient client = mockClient("Unused.");
        when(builder.build()).thenReturn(client);
        CommunicationTools tools = mock(CommunicationTools.class);
        when(client.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                .thenReturn(sendDecision(null, "Results", "Report body"));
        EmailAgent missingRecipientAgent = new EmailAgent(
                builder, tools, mock(WorkflowContextManager.class)
        );

        AgentResult missingRecipient = missingRecipientAgent.execute(
                UUID.randomUUID(),
                new AgentCoordinatorRequest("Send the results", null, null)
        );
        assertTrue(missingRecipient.isClarification());

        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        when(client.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                .thenReturn(new EmailAgent.EmailDecision(
                        EmailAgent.EmailDecisionOutcome.SEND,
                        null,
                        "operator@example.test",
                        "Results",
                        "Report body",
                        true,
                        "missing-report"
                ));
        EmailAgent missingReportAgent = new EmailAgent(
                builder, tools, mock(WorkflowContextManager.class), memory
        );
        AgentResult missingReport = missingReportAgent.execute(
                conversationId,
                new AgentCoordinatorRequest("Send the report", "operator@example.test", null)
        );
        assertTrue(missingReport.isClarification());
        verifyNoInteractions(tools);
    }

    @Test
    void validatesRegisteredReportHandoffAndPassesPdfOutOfBandAsAttachment() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        ChatClient client = mockClient("Tool call response.");
        when(builder.build()).thenReturn(client);
        CommunicationTools tools = mock(CommunicationTools.class);
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        byte[] pdfBytes = validPdf();
        ReportFileStorageService storageService = new ReportFileStorageService(tempDirectory.toString());
        ReportFileStorageService.StoredReport stored = storageService.storePdf(pdfBytes);
        String storedFileName = Path.of(stored.reference()).getFileName().toString();
        when(tools.executionResults()).thenReturn(List.of(new CommunicationTools.EmailResult(
                "report-mail-1",
                "operator@example.test",
                true,
                java.time.LocalDateTime.now(),
                storedFileName
        )));
        memory.recordTurn(
                conversationId,
                "The quarterly analysis was completed.",
                "The report artifact is ready for email handoff."
        );
        Artifact report = memory.registerArtifact(
                conversationId,
                Artifact.generatedPdfReport("Monthly report", stored.reference(), stored.sizeBytes(), 42)
        );
        when(client.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                .thenReturn(new EmailAgent.EmailDecision(
                        EmailAgent.EmailDecisionOutcome.SEND,
                        null,
                        "operator@example.test",
                        "Monthly report",
                        "Please see the attached report.",
                        true,
                        report.id()
                ));
        EmailAgent agent = new EmailAgent(
                builder, tools, mock(WorkflowContextManager.class), memory, storageService
        );
        ReportArtifactHandoff handoff = handoff(report);
        clearInvocations(client.prompt());

        AgentResult result = agent.execute(
                conversationId,
                new AgentCoordinatorRequest(
                        "Email that report as an attachment to operator@example.test", null, null),
                handoff
        );

        assertTrue(result.isSuccess());
        ArgumentCaptor<List<Message>> decisionInput = ArgumentCaptor.forClass(List.class);
        verify(client.prompt()).messages(decisionInput.capture());
        assertTrue(decisionInput.getValue().stream().anyMatch(message ->
                message.getText().contains("Monthly report")));
        assertTrue(decisionInput.getValue().stream().anyMatch(message ->
                message.getText().contains(report.id())));
        assertTrue(decisionInput.getValue().stream().anyMatch(message ->
                message.getText().contains("The quarterly analysis was completed.")));
        assertTrue(decisionInput.getValue().stream().anyMatch(message ->
                message.getText().contains("The report artifact is ready for email handoff.")));
        ArgumentCaptor<String> toolPrompt = ArgumentCaptor.forClass(String.class);
        verify(client.prompt()).user(toolPrompt.capture());
        assertTrue(toolPrompt.getValue().contains(storedFileName));
        assertFalse(toolPrompt.getValue().contains(stored.reference()));
        assertFalse(toolPrompt.getValue().contains("%PDF-"));
        ArgumentCaptor<CommunicationTools.EmailAttachment> attachment =
                ArgumentCaptor.forClass(CommunicationTools.EmailAttachment.class);
        verify(tools).setAttachmentForExecution(attachment.capture());
        assertEquals(storedFileName, attachment.getValue().fileName());
        assertEquals("application/pdf", attachment.getValue().contentType());
        assertArrayEquals(pdfBytes, attachment.getValue().content());
        verify(tools).clearAttachmentForExecution();
    }

    @Test
    void rejectsForgedAndStaleReportHandoffsBeforeCommunication() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        ChatClient client = mockClient("Tool must not run.");
        when(builder.build()).thenReturn(client);
        CommunicationTools tools = mock(CommunicationTools.class);
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        ReportFileStorageService storageService = new ReportFileStorageService(tempDirectory.toString());
        ReportFileStorageService.StoredReport stored = storageService.storePdf(validPdf());
        Artifact report = memory.registerArtifact(
                conversationId,
                Artifact.generatedPdfReport("Monthly report", stored.reference(), stored.sizeBytes(), 42)
        );
        when(client.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                .thenReturn(new EmailAgent.EmailDecision(
                        EmailAgent.EmailDecisionOutcome.SEND,
                        null,
                        "operator@example.test",
                        "Monthly report",
                        "Please see attached.",
                        true,
                        report.id()
                ));
        EmailAgent agent = new EmailAgent(
                builder, tools, mock(WorkflowContextManager.class), memory, storageService
        );
        ReportArtifactHandoff validHandoff = handoff(report);
        ReportArtifactHandoff forged = new ReportArtifactHandoff(
                validHandoff.artifactId(),
                validHandoff.title(),
                validHandoff.reportType(),
                validHandoff.format(),
                tempDirectory.resolve("forged.pdf").toString(),
                validHandoff.createdAt(),
                validHandoff.metadata()
        );

        AgentResult forgedResult = agent.execute(
                conversationId,
                new AgentCoordinatorRequest("Send the report attached", "operator@example.test", null),
                forged
        );
        assertTrue(forgedResult.isClarification());
        assertTrue(forgedResult.clarificationQuestion().contains("no longer available"));

        Files.delete(Path.of(stored.reference()));
        AgentResult staleResult = agent.execute(
                conversationId,
                new AgentCoordinatorRequest("Send the report attached", "operator@example.test", null),
                validHandoff
        );
        assertTrue(staleResult.isClarification());
        assertTrue(staleResult.clarificationQuestion().contains("no longer available"));
        verifyNoInteractions(tools);
    }

    @Test
    void registersSuppliedReportWithUserProvenance() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        ChatClient client = mockClient("Unused.");
        when(builder.build()).thenReturn(client);
        when(client.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                .thenReturn(new EmailAgent.EmailDecision(
                        EmailAgent.EmailDecisionOutcome.CLARIFICATION_REQUIRED,
                        "Should I include the supplied report?",
                        null, null, null, false, null
                ));
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        EmailAgent agent = new EmailAgent(
                builder, mock(CommunicationTools.class), mock(WorkflowContextManager.class), memory
        );

        AgentResult result = agent.execute(
                conversationId,
                new AgentCoordinatorRequest(
                        "Send this report to operator@example.test",
                        "operator@example.test",
                        new AgentCoordinatorRequest.ReportHandoff(
                                com.agentops.guardian.tool.ReportTools.ReportType.ANALYTICAL,
                                false,
                                1,
                                "User-provided report data"
                        )
                )
        );

        assertTrue(result.isClarification());
        Artifact supplied = memory.getLatestArtifact(conversationId, ArtifactType.REPORT).orElseThrow();
        assertEquals("User-provided report data", supplied.content());
        assertEquals(Artifact.SUPPLIED_PROVENANCE, supplied.metadata().get(Artifact.PROVENANCE_KEY));
    }

    private void assertNonSendDecisionDoesNotCallTool(EmailAgent.EmailDecision decision) throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        ChatClient client = mockClient("Must not be used for a send.");
        when(builder.build()).thenReturn(client);
        when(client.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                .thenReturn(decision);
        CommunicationTools tools = mock(CommunicationTools.class);
        EmailAgent agent = new EmailAgent(builder, tools, mock(WorkflowContextManager.class));

        AgentResult result = agent.execute(
                UUID.randomUUID(),
                new AgentCoordinatorRequest("Current natural language request", "operator@example.test", null)
        );

        assertTrue(result.isClarification());
        verifyNoInteractions(tools);
    }

    @Test
    void executionRecordsSuccessOnlyAfterConfirmedToolAcceptance() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        ChatClient sendClient = mockClient("Untrusted model claim.");
        when(builder.build()).thenReturn(sendClient);
        when(sendClient.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                .thenReturn(sendDecision("operator@example.test", "Subject", "Complete body"));
        CommunicationTools tools = mock(CommunicationTools.class);
        when(tools.executionResults()).thenReturn(List.of(new CommunicationTools.EmailResult(
                "accepted-1", "operator@example.test", true, java.time.LocalDateTime.now()
        )));
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        EmailAgent agent = new EmailAgent(builder, tools, mock(WorkflowContextManager.class), memory);

        AgentResult result = agent.execute(
                conversationId,
                new AgentCoordinatorRequest("Send this message", "operator@example.test", null)
        );

        assertTrue(result.isSuccess());
        assertEquals(ExecutionStatus.SUCCESS, memory.getLatestExecution(conversationId).orElseThrow().status());
        assertTrue(result.response().contains("simulated email request"));
    }

    @Test
    void governanceBlockAndUnconfirmedExecutionAreNeverRecordedAsSuccess() throws IOException {
        ConversationMemory blockedMemory = new ConversationMemory();
        UUID blockedConversation = blockedMemory.open(null);
        EmailAgent blockedAgent = newEmailAgent(
                mockClientFailure(new GovernanceViolationException("Blocked by governance.")),
                mock(CommunicationTools.class),
                blockedMemory
        );
        assertThrows(GovernanceViolationException.class, () -> blockedAgent.execute(
                blockedConversation,
                new AgentCoordinatorRequest("Send this message", "operator@example.test", null)
        ));
        assertEquals(
                ExecutionStatus.BLOCKED_BY_GOVERNANCE,
                blockedMemory.getLatestExecution(blockedConversation).orElseThrow().status()
        );

        ConversationMemory failedMemory = new ConversationMemory();
        UUID failedConversation = failedMemory.open(null);
        EmailAgent failedAgent = newEmailAgent(mockClient("Model says sent."), mock(CommunicationTools.class), failedMemory);
        assertThrows(IllegalStateException.class, () -> failedAgent.execute(
                failedConversation,
                new AgentCoordinatorRequest("Send this message", "operator@example.test", null)
        ));
        assertEquals(ExecutionStatus.FAILED, failedMemory.getLatestExecution(failedConversation).orElseThrow().status());
        assertFalse(failedMemory.getExecutionRecords(failedConversation).stream()
                .anyMatch(record -> record.status() == ExecutionStatus.SUCCESS));
    }

    @Test
    void sendUsesCommunicationToolConfiguredClientAndGovernedWorkflow() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient sendClient = mockClient("Communication tool confirmed acceptance.");
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(sendClient);
        CommunicationTools tools = mock(CommunicationTools.class);
        when(tools.executionResults()).thenReturn(List.of(new CommunicationTools.EmailResult(
                "message-123", "operator@example.test", true, java.time.LocalDateTime.now()
        )));
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        EmailAgent agent = new EmailAgent(builder, tools, manager);
        clearInvocations(sendClient);
        EmailAgent.ReportContext report = new EmailAgent.ReportContext("report-1", "Sales summary");

        String result = agent.process(new EmailAgent.EmailRequest(
                "operator@example.test",
                "Quarterly results",
                "Send the supplied results to finance.",
                report
        ));

        assertTrue(result.contains("message-123"));
        assertTrue(result.contains("simulated email request"));
        ArgumentCaptor<Object[]> toolCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(builder).defaultTools(toolCaptor.capture());
        assertArrayEquals(new Object[]{tools}, toolCaptor.getValue());
        verify(sendClient).prompt();
        InOrder lifecycle = inOrder(manager);
        lifecycle.verify(manager).start(eq("EmailAgent"), eq(WorkflowType.EMAIL_COMMUNICATION), anyString());
        lifecycle.verify(manager).complete();
        lifecycle.verify(manager).clear();
    }

    @Test
    void governanceViolationBlocksAndClearsWorkflow() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient sendClient = mockClientFailure(new GovernanceViolationException("Blocked by governance."));
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(sendClient);
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        EmailAgent agent = new EmailAgent(builder, mock(CommunicationTools.class), manager);
        clearInvocations(sendClient);

        assertThrows(
                GovernanceViolationException.class,
                () -> agent.process(sendRequest())
        );

        InOrder lifecycle = inOrder(manager);
        lifecycle.verify(manager).start(eq("EmailAgent"), eq(WorkflowType.EMAIL_COMMUNICATION), anyString());
        lifecycle.verify(manager).block();
        lifecycle.verify(manager).clear();
        verify(manager, never()).complete();
        verify(manager, never()).fail();
    }

    @Test
    void runtimeFailureFailsAndClearsWorkflow() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient sendClient = mockClientFailure(new IllegalStateException("Model unavailable."));
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(sendClient);
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        EmailAgent agent = new EmailAgent(builder, mock(CommunicationTools.class), manager);
        clearInvocations(sendClient);

        assertThrows(IllegalStateException.class, () -> agent.process(sendRequest()));

        InOrder lifecycle = inOrder(manager);
        lifecycle.verify(manager).start(eq("EmailAgent"), eq(WorkflowType.EMAIL_COMMUNICATION), anyString());
        lifecycle.verify(manager).fail();
        lifecycle.verify(manager).clear();
        verify(manager, never()).complete();
        verify(manager, never()).block();
    }

    @Test
    void doesNotClaimSendSuccessWithoutCommunicationToolConfirmation() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient sendClient = mockClient("The email was sent.");
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(sendClient);
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        EmailAgent agent = new EmailAgent(builder, mock(CommunicationTools.class), manager);
        clearInvocations(sendClient);

        assertThrows(IllegalStateException.class, () -> agent.process(sendRequest()));
        verify(manager).fail();
        verify(manager, never()).complete();
    }

    @Test
    void rejectsSendingWithoutRecipientOrContentBeforeOpeningWorkflow() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        ChatClient sendClient = mockClient("Unused.");
        when(builder.build()).thenReturn(sendClient);
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        EmailAgent agent = new EmailAgent(builder, mock(CommunicationTools.class), manager);

        assertThrows(IllegalArgumentException.class, () -> agent.process(sendRequestWithRecipient(null)));
        assertThrows(IllegalArgumentException.class, () -> agent.process(new EmailAgent.EmailRequest(
                "operator@example.test", null, " ", null
        )));
        verifyNoInteractions(manager);
    }

    @Test
    void registeredWorkflowDefinitionIncludesEmailAndGovernedHistoryCapabilities() {
        WorkflowDefinitionRegistry registry = new WorkflowDefinitionRegistry();
        var definition = registry.get(WorkflowType.EMAIL_COMMUNICATION);
        var reportingDefinition = registry.get(WorkflowType.CUSTOMER_REPORTING);

        assertEquals(
                Set.of(WorkflowCapability.READ_CONVERSATION_HISTORY, WorkflowCapability.SEND_EMAIL),
                definition.capabilities()
        );
        assertFalse(reportingDefinition.allows(WorkflowCapability.SEND_EMAIL));
    }

    @Test
    void communicationToolRecordsOnlySuccessfulExecutionsInTheCurrentThread() {
        CommunicationTools tools = new CommunicationTools();
        tools.beginExecution();

        CommunicationTools.EmailResult result = tools.sendEmail(
                "operator@example.test",
                "Subject",
                "Body"
        );

        assertEquals(List.of(result), tools.executionResults());
        assertTrue(result.accepted());
        tools.clearExecutionResult();
        assertTrue(tools.executionResults().isEmpty());
    }

    @Test
    void emailValidationAndExtractionWorksAccurately() {
        assertTrue(EmailAgent.isValidEmailAddress("user@example.com"));
        assertFalse(EmailAgent.isValidEmailAddress("invalid-email"));
        assertFalse(EmailAgent.isValidEmailAddress(null));
    }

    private EmailAgent.EmailRequest sendRequest() {
        return sendRequestWithRecipient("operator@example.test");
    }

    private EmailAgent.EmailRequest sendRequestWithRecipient(String recipient) {
        return new EmailAgent.EmailRequest(recipient, "Subject", "Complete body", null);
    }

    private EmailAgent newEmailAgent(ChatClient client, CommunicationTools tools, ConversationMemory memory)
            throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        when(client.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                .thenReturn(sendDecision("operator@example.test", "Subject", "Complete body"));
        return new EmailAgent(builder, tools, mock(WorkflowContextManager.class), memory);
    }

    private EmailAgent.EmailDecision sendDecision(String recipient, String subject, String body) {
        return new EmailAgent.EmailDecision(
                EmailAgent.EmailDecisionOutcome.SEND,
                null,
                recipient,
                subject,
                body,
                false,
                null
        );
    }

    private ReportArtifactHandoff handoff(Artifact report) {
        return new ReportArtifactHandoff(
                report.id(),
                report.title(),
                report.metadata().get(Artifact.REPORT_TYPE_KEY),
                report.metadata().get(Artifact.FORMAT_KEY),
                report.metadata().get(Artifact.FILE_REFERENCE_KEY),
                report.createdAt(),
                report.metadata()
        );
    }

    private byte[] validPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private ChatClient mockClient(String response) {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(client.prompt().user(anyString()).call().content()).thenReturn(response);
        return client;
    }

    private ChatClient mockClientFailure(RuntimeException failure) {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(client.prompt().user(anyString()).call().content()).thenThrow(failure);
        return client;
    }
}
