package com.agentops.guardian.coordinator;

import com.agentops.guardian.agent.CustomerReportAgent;
import com.agentops.guardian.agent.EmailAgent;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorResponse;
import com.agentops.guardian.tool.ReportTools;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

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
        assertEquals("I can help with a customer report or an email handoff.", response.response());
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
    void rejectsMissingOrUnsupportedModelDecision() {
        Fixture fixture = new Fixture(null);

        assertThrows(
                IllegalStateException.class,
                () -> fixture.coordinator.handle(new AgentCoordinatorRequest("Do something", null, null))
        );

        fixture.decision(decision(AgentCoordinator.IntentAction.GENERAL_RESPONSE, null, null));
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
        private final ChatClient.Builder builder = mock(ChatClient.Builder.class);
        private final CustomerReportAgent reportAgent = mock(CustomerReportAgent.class);
        private final EmailAgent emailAgent = mock(EmailAgent.class);
        private final AgentCoordinator coordinator;

        private Fixture(AgentCoordinator.IntentDecision decision) {
            when(builder.build()).thenReturn(chatClient);
            coordinator = new AgentCoordinator(builder, reportAgent, emailAgent);
            decision(decision);
        }

        private void decision(AgentCoordinator.IntentDecision decision) {
            when(chatClient.prompt()
                    .system(anyString())
                    .user(anyString())
                    .call()
                    .entity(AgentCoordinator.IntentDecision.class))
                    .thenReturn(decision);
        }
    }
}
