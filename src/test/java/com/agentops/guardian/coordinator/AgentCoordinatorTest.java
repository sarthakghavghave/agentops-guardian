package com.agentops.guardian.coordinator;

import com.agentops.guardian.agent.CustomerReportAgent;
import com.agentops.guardian.agent.EmailAgent;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorResponse;
import com.agentops.guardian.tool.ReportTools;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AgentCoordinatorTest {

    @Test
    void dispatchesReportIntentToReportAgentWithOriginalMessage() {
        Fixture fixture = new Fixture(decision(AgentCoordinator.IntentAction.REPORT_REQUEST, null, null));
        when(fixture.reportAgent.generateReport("Generate a customer report for Boston"))
                .thenReturn("Report generated.");

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Generate a customer report for Boston", null, null)
        );

        assertEquals(AgentCoordinator.IntentAction.REPORT_REQUEST, response.action());
        assertEquals("Report generated.", response.response());
        assertFalse(response.clarificationRequired());
        assertNotNull(response.conversationId());
        assertTrue(fixture.memory.history(java.util.UUID.fromString(response.conversationId()))
                .stream().noneMatch(message -> message.getText().contains("Report generated.")));
        verify(fixture.reportAgent).generateReport("Generate a customer report for Boston");
        verifyNoInteractions(fixture.emailAgent);
    }

    @Test
    void dispatchesEmailIntentWithOnlyExplicitlySuppliedReportAndRecipient() {
        Fixture fixture = new Fixture(decision(AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null));
        AgentCoordinatorRequest.ReportHandoff handoff = new AgentCoordinatorRequest.ReportHandoff(
                ReportTools.ReportType.REDACTED_DETAIL,
                true,
                2,
                "Customer segment report"
        );
        when(fixture.emailAgent.emailReport(any(ReportTools.Report.class), eq("recipient@example.com")))
                .thenReturn("Communication tool confirmed the email.");

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Email this report", "recipient@example.com", handoff)
        );

        assertEquals(AgentCoordinator.IntentAction.EMAIL_REQUEST, response.action());
        assertEquals("Communication tool confirmed the email.", response.response());
        assertFalse(response.clarificationRequired());
        ArgumentCaptor<ReportTools.Report> report = ArgumentCaptor.forClass(ReportTools.Report.class);
        verify(fixture.emailAgent).emailReport(report.capture(), eq("recipient@example.com"));
        assertEquals(
                new ReportTools.Report(
                        ReportTools.ReportType.REDACTED_DETAIL,
                        true,
                        2,
                        "Customer segment report"
                ),
                report.getValue()
        );
        List<Message> retained = fixture.memory.history(java.util.UUID.fromString(response.conversationId()));
        assertTrue(retained.stream().noneMatch(message ->
                message.getText().contains("Customer segment report")
                        || message.getText().contains("recipient@example.com")
                        || message.getText().contains("Communication tool confirmed")
        ));
        verifyNoInteractions(fixture.reportAgent);
    }

    @Test
    void asksForEmailDetailsInsteadOfInventingThem() {
        Fixture fixture = new Fixture(decision(AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null));

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Email the report", null, null)
        );

        assertEquals(AgentCoordinator.IntentAction.CLARIFICATION_REQUIRED, response.action());
        assertTrue(response.clarificationRequired());
        assertTrue(response.response().contains("report"));
        assertTrue(response.response().contains("recipient"));
        verifyNoInteractions(fixture.reportAgent, fixture.emailAgent);
    }

    @Test
    void asksOnlyForMissingEmailRecipient() {
        Fixture fixture = new Fixture(decision(AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null));
        AgentCoordinatorRequest.ReportHandoff handoff = new AgentCoordinatorRequest.ReportHandoff(
                ReportTools.ReportType.ANALYTICAL,
                false,
                0,
                "Supplied report"
        );

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Email the report", null, handoff)
        );

        assertEquals(AgentCoordinator.IntentAction.CLARIFICATION_REQUIRED, response.action());
        assertTrue(response.response().contains("email address"));
        verifyNoInteractions(fixture.reportAgent, fixture.emailAgent);
    }

    @Test
    void recipientAnswerContinuesPendingEmailClarificationInSameConversation() {
        Fixture fixture = new Fixture(decision(AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null));
        AgentCoordinatorRequest.ReportHandoff handoff = new AgentCoordinatorRequest.ReportHandoff(
                ReportTools.ReportType.ANALYTICAL,
                false,
                2,
                "Quarterly revenue summary"
        );
        AgentCoordinatorResponse clarification = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Email this supplied report", null, handoff)
        );
        assertTrue(clarification.clarificationRequired());

        fixture.decision(decision(AgentCoordinator.IntentAction.GENERAL_RESPONSE, null, null));
        when(fixture.emailAgent.emailReport(any(ReportTools.Report.class), eq("recipient@example.com")))
                .thenReturn("Communication tool confirmed success.");
        AgentCoordinatorResponse followUp = fixture.coordinator.handle(
                new AgentCoordinatorRequest(
                        "Please send it to recipient@example.com",
                        null,
                        null,
                        clarification.conversationId()
                )
        );

        assertEquals(AgentCoordinator.IntentAction.EMAIL_REQUEST, followUp.action());
        assertFalse(followUp.clarificationRequired());
        assertEquals(clarification.conversationId(), followUp.conversationId());
        ArgumentCaptor<ReportTools.Report> report = ArgumentCaptor.forClass(ReportTools.Report.class);
        verify(fixture.emailAgent).emailReport(report.capture(), eq("recipient@example.com"));
        assertEquals("Quarterly revenue summary", report.getValue().content());
        assertNull(fixture.memory.pendingEmailReport(
                java.util.UUID.fromString(clarification.conversationId())
        ));
        assertTrue(fixture.memory.history(java.util.UUID.fromString(clarification.conversationId()))
                .stream().noneMatch(message -> message.getText().contains("recipient@example.com")));
    }

    @Test
    void asksForIncompleteReportBeforeEmailDispatch() {
        Fixture fixture = new Fixture(decision(AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null));
        AgentCoordinatorRequest.ReportHandoff handoff = new AgentCoordinatorRequest.ReportHandoff(
                null,
                false,
                0,
                " "
        );

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Email the report", "recipient@example.com", handoff)
        );

        assertTrue(response.clarificationRequired());
        assertTrue(response.response().contains("report type"));
        verifyNoInteractions(fixture.reportAgent, fixture.emailAgent);
    }

    @Test
    void returnsGeneralResponseWithoutInvokingAgents() {
        Fixture fixture = new Fixture(decision(
                AgentCoordinator.IntentAction.GENERAL_RESPONSE,
                "I can help with a customer report or an email handoff.",
                null
        ));

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("What can you do?", null, null)
        );

        assertEquals(AgentCoordinator.IntentAction.GENERAL_RESPONSE, response.action());
        assertEquals(
                "I can help generate a customer report or email a report that you explicitly provide.",
                response.response()
        );
        assertTrue(fixture.memory.history(java.util.UUID.fromString(response.conversationId()))
                .stream().anyMatch(message ->
                        message.getText().contains("I can help generate a customer report")
                ));
        verifyNoInteractions(fixture.reportAgent, fixture.emailAgent);
    }

    @Test
    void returnsTargetedClarificationForAmbiguousIntent() {
        Fixture fixture = new Fixture(decision(
                AgentCoordinator.IntentAction.CLARIFICATION_REQUIRED,
                null,
                "Would you like me to generate a report or email an existing report?"
        ));

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Can you take care of that?", null, null)
        );

        assertTrue(response.clarificationRequired());
        assertEquals(
                "Would you like me to generate a report or email an existing report?",
                response.response()
        );
        verifyNoInteractions(fixture.reportAgent, fixture.emailAgent);
    }

    @Test
    void followUpClarificationTurnReceivesEarlierConversationHistory() {
        Fixture fixture = new Fixture(decision(
                AgentCoordinator.IntentAction.CLARIFICATION_REQUIRED,
                null,
                "Which city should the report cover?"
        ));

        AgentCoordinatorResponse first = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Generate a sales report for Boston", null, null)
        );
        fixture.decision(decision(AgentCoordinator.IntentAction.REPORT_REQUEST, null, null));
        when(fixture.reportAgent.generateReport(anyString())).thenReturn("Report generated.");
        AgentCoordinatorResponse second = fixture.coordinator.handle(
                new AgentCoordinatorRequest(
                        "Can you make that report for Toronto instead?",
                        null,
                        null,
                        first.conversationId()
                )
        );

        assertEquals(first.conversationId(), second.conversationId());
        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(fixture.chatClient, times(2)).prompt();
        verify(fixture.requestSpec, times(2)).messages(messages.capture());
        assertEquals(1, messages.getAllValues().getFirst().size());
        List<Message> followUpHistory = messages.getAllValues().get(1);
        assertEquals(3, followUpHistory.size());
        assertTrue(followUpHistory.get(0).getText().contains("Generate a sales report for Boston"));
        assertTrue(followUpHistory.get(1).getText().contains("Which city should the report cover?"));
        assertTrue(followUpHistory.get(2).getText().contains("Can you make that report for Toronto instead?"));
        ArgumentCaptor<String> reportRequest = ArgumentCaptor.forClass(String.class);
        verify(fixture.reportAgent).generateReport(reportRequest.capture());
        assertTrue(reportRequest.getValue().contains("Generate a sales report for Boston"));
        assertTrue(reportRequest.getValue().contains("Can you make that report for Toronto instead?"));
    }

    @Test
    void newConversationDoesNotReceiveHistoryFromAnotherConversation() {
        Fixture fixture = new Fixture(decision(
                AgentCoordinator.IntentAction.CLARIFICATION_REQUIRED,
                null,
                "Which city?"
        ));

        AgentCoordinatorResponse first = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Generate a customer report", null, null)
        );
        AgentCoordinatorResponse second = fixture.coordinator.handle(
                new AgentCoordinatorRequest("What can you do?", null, null)
        );

        assertNotEquals(first.conversationId(), second.conversationId());
        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(fixture.requestSpec, times(2)).messages(messages.capture());
        List<Message> otherConversationMessages = messages.getAllValues().get(1);
        assertEquals(1, otherConversationMessages.size());
        assertTrue(otherConversationMessages.getFirst().getText().contains("What can you do?"));
    }

    @Test
    void pendingReportCannotLeakIntoANewConversation() {
        Fixture fixture = new Fixture(decision(AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null));
        AgentCoordinatorRequest.ReportHandoff handoff = new AgentCoordinatorRequest.ReportHandoff(
                ReportTools.ReportType.ANALYTICAL,
                false,
                1,
                "Conversation-specific report"
        );
        AgentCoordinatorResponse first = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Email this report", null, handoff)
        );

        AgentCoordinatorResponse second = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Send it to recipient@example.com", null, null)
        );

        assertTrue(first.clarificationRequired());
        assertTrue(second.clarificationRequired());
        assertTrue(second.response().contains("report"));
        verifyNoInteractions(fixture.emailAgent);
    }

    @Test
    void priorReportResultAloneCannotAuthorizeEmailOrBeClaimedAsSent() {
        Fixture fixture = new Fixture(decision(AgentCoordinator.IntentAction.REPORT_REQUEST, null, null));
        when(fixture.reportAgent.generateReport("Generate a sales report for Boston"))
                .thenReturn("A report was generated.");
        AgentCoordinatorResponse reportResponse = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Generate a sales report for Boston", null, null)
        );

        fixture.decision(decision(AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null));
        AgentCoordinatorResponse emailFollowUp = fixture.coordinator.handle(
                new AgentCoordinatorRequest(
                        "Email the report you generated earlier to recipient@example.com",
                        null,
                        null,
                        reportResponse.conversationId()
                )
        );

        assertTrue(emailFollowUp.clarificationRequired());
        assertTrue(emailFollowUp.response().contains("report"));
        assertFalse(emailFollowUp.response().toLowerCase(java.util.Locale.ROOT).contains("sent"));
        verifyNoInteractions(fixture.emailAgent);
        assertTrue(fixture.memory.history(java.util.UUID.fromString(reportResponse.conversationId()))
                .stream().noneMatch(message -> message.getText().contains("A report was generated.")));
    }

    @Test
    void ignoresModelGeneralResponseThatClaimsAnActionSucceeded() {
        Fixture fixture = new Fixture(decision(
                AgentCoordinator.IntentAction.GENERAL_RESPONSE,
                "The email was sent and the report was generated.",
                null
        ));

        AgentCoordinatorResponse response = fixture.coordinator.handle(
                new AgentCoordinatorRequest("Did you send the email?", null, null)
        );

        assertFalse(response.response().toLowerCase(java.util.Locale.ROOT).contains("sent"));
        assertFalse(response.response().toLowerCase(java.util.Locale.ROOT).contains("generated"));
    }

    @Test
    void rejectsMissingModelDecision() {
        Fixture fixture = new Fixture(null);

        assertThrows(
                IllegalStateException.class,
                () -> fixture.coordinator.handle(new AgentCoordinatorRequest("Do something", null, null))
        );

        verifyNoInteractions(fixture.reportAgent, fixture.emailAgent);
    }

    @Test
    void doesNotReturnSuccessWhenAnAgentFailsOrReturnsNoResult() {
        Fixture fixture = new Fixture(decision(AgentCoordinator.IntentAction.REPORT_REQUEST, null, null));
        when(fixture.reportAgent.generateReport("Generate a report"))
                .thenThrow(new IllegalStateException("Report execution failed."));

        assertThrows(
                IllegalStateException.class,
                () -> fixture.coordinator.handle(new AgentCoordinatorRequest("Generate a report", null, null))
        );

        doReturn(" ").when(fixture.reportAgent).generateReport("Generate a report");
        assertThrows(
                IllegalStateException.class,
                () -> fixture.coordinator.handle(new AgentCoordinatorRequest("Generate a report", null, null))
        );
    }

    @Test
    void propagatesEmailAgentFailureWithoutReturningSuccess() {
        Fixture fixture = new Fixture(decision(AgentCoordinator.IntentAction.EMAIL_REQUEST, null, null));
        AgentCoordinatorRequest.ReportHandoff handoff = new AgentCoordinatorRequest.ReportHandoff(
                ReportTools.ReportType.ANALYTICAL,
                false,
                1,
                "Supplied report"
        );
        when(fixture.emailAgent.emailReport(any(ReportTools.Report.class), anyString()))
                .thenThrow(new IllegalStateException("Communication was blocked."));

        assertThrows(
                IllegalStateException.class,
                () -> fixture.coordinator.handle(
                        new AgentCoordinatorRequest("Email the report", "recipient@example.com", handoff)
                )
        );
    }

    @Test
    void requiresNonBlankUserMessage() {
        Fixture fixture = new Fixture(null);

        assertThrows(IllegalArgumentException.class, () -> fixture.coordinator.handle(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.coordinator.handle(new AgentCoordinatorRequest(" ", null, null))
        );
        verifyNoInteractions(fixture.reportAgent, fixture.emailAgent);
        verify(fixture.builder, never()).defaultTools(any(Object[].class));
    }

    private AgentCoordinator.IntentDecision decision(
            AgentCoordinator.IntentAction action,
            String response,
            String clarificationQuestion
    ) {
        return new AgentCoordinator.IntentDecision(action, response, clarificationQuestion);
    }

    private static class Fixture {
        private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        private final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        private final ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        private final ChatClient.Builder builder = mock(ChatClient.Builder.class);
        private final CustomerReportAgent reportAgent = mock(CustomerReportAgent.class);
        private final EmailAgent emailAgent = mock(EmailAgent.class);
        private final ConversationMemory memory = new ConversationMemory();
        private final AgentCoordinator coordinator;

        private Fixture(AgentCoordinator.IntentDecision decision) {
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(anyString())).thenReturn(requestSpec);
            when(requestSpec.messages(anyList())).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(builder.build()).thenReturn(chatClient);
            coordinator = new AgentCoordinator(builder, reportAgent, emailAgent, memory);
            decision(decision);
        }

        private void decision(AgentCoordinator.IntentDecision decision) {
            when(callResponseSpec.entity(AgentCoordinator.IntentDecision.class))
                    .thenReturn(decision);
        }
    }
}
