package com.agentops.guardian.agent;

import com.agentops.guardian.coordinator.ConversationMemory;
import com.agentops.guardian.coordinator.agent.AgentResult;
import com.agentops.guardian.coordinator.context.Artifact;
import com.agentops.guardian.coordinator.context.ArtifactType;
import com.agentops.guardian.coordinator.context.ExecutionStatus;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowDefinitionRegistry;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.tool.CommunicationTools;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EmailAgentTest {

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
    void resolvesRegisteredReportFromSameConversationWithoutRepeatingItsContent() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        ChatClient client = mockClient("Tool call response.");
        when(builder.build()).thenReturn(client);
        CommunicationTools tools = mock(CommunicationTools.class);
        when(tools.executionResults()).thenReturn(List.of(new CommunicationTools.EmailResult(
                "report-mail-1", "operator@example.test", true, java.time.LocalDateTime.now()
        )));
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        Artifact report = memory.registerArtifact(
                conversationId,
                Artifact.generatedReport("Monthly report", "Verified monthly totals")
        );
        when(client.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                .thenReturn(new EmailAgent.EmailDecision(
                        EmailAgent.EmailDecisionOutcome.SEND,
                        null,
                        "operator@example.test",
                        "Monthly report",
                        "Please see the report below.",
                        true,
                        report.id()
                ));
        EmailAgent agent = new EmailAgent(builder, tools, mock(WorkflowContextManager.class), memory);
        clearInvocations(client.prompt());

        AgentResult result = agent.execute(
                conversationId,
                new AgentCoordinatorRequest("Email that report to operator@example.test", null, null)
        );

        assertTrue(result.isSuccess());
        ArgumentCaptor<List<Message>> decisionInput = ArgumentCaptor.forClass(List.class);
        verify(client.prompt()).messages(decisionInput.capture());
        assertTrue(decisionInput.getValue().stream().anyMatch(message ->
                message.getText().contains("Verified monthly totals")));
        assertTrue(decisionInput.getValue().stream().anyMatch(message ->
                message.getText().contains(report.id())));
        ArgumentCaptor<String> toolPrompt = ArgumentCaptor.forClass(String.class);
        verify(client.prompt()).user(toolPrompt.capture());
        assertTrue(toolPrompt.getValue().contains(report.id()));
        assertTrue(toolPrompt.getValue().contains("Verified monthly totals"));
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
    void registeredWorkflowDefinitionGrantsOnlyEmailCapability() {
        WorkflowDefinitionRegistry registry = new WorkflowDefinitionRegistry();
        var definition = registry.get(WorkflowType.EMAIL_COMMUNICATION);
        var reportingDefinition = registry.get(WorkflowType.CUSTOMER_REPORTING);

        assertEquals(Set.of(WorkflowCapability.SEND_EMAIL), definition.capabilities());
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
