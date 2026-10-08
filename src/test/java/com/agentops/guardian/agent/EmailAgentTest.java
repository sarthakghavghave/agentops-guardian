package com.agentops.guardian.agent;

import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.workflow.WorkflowDefinitionRegistry;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.tool.CommunicationTools;
import com.agentops.guardian.tool.ReportTools;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.chat.client.ChatClient;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EmailAgentTest {

    @Test
    void configuresOnlyCommunicationToolAndHandsInExplicitReport() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mockChatClient(builder, "Email sent.");
        CommunicationTools communicationTools = mock(CommunicationTools.class);
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        EmailAgent agent = new EmailAgent(builder, communicationTools, manager);
        ReportTools.Report report = new ReportTools.Report(
                ReportTools.ReportType.ANALYTICAL,
                false,
                3,
                "Aggregated revenue: 1250.00"
        );

        assertEquals("Email sent.", agent.emailReport(report, "operator@example.test"));

        ArgumentCaptor<Object[]> tools = ArgumentCaptor.forClass(Object[].class);
        verify(builder).defaultTools(tools.capture());
        assertEquals(1, tools.getValue().length);
        assertSame(communicationTools, tools.getValue()[0]);

        ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
        InOrder lifecycle = inOrder(manager);
        lifecycle.verify(manager).start(
                eq("EmailAgent"),
                eq(WorkflowType.EMAIL_COMMUNICATION),
                request.capture()
        );
        assertTrue(request.getValue().contains("operator@example.test"));
        assertTrue(request.getValue().contains("ANALYTICAL"));
        assertTrue(request.getValue().contains("Aggregated revenue: 1250.00"));
        lifecycle.verify(manager).complete();
        lifecycle.verify(manager).clear();
    }

    @Test
    void governanceViolationBlocksAndClearsWorkflow() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        mockChatClientFailure(builder, new GovernanceViolationException("Blocked by governance."));
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        EmailAgent agent = new EmailAgent(builder, mock(CommunicationTools.class), manager);

        assertThrows(
                GovernanceViolationException.class,
                () -> agent.emailReport(report(), "operator@example.test")
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
        mockChatClientFailure(builder, new IllegalStateException("Model unavailable."));
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        EmailAgent agent = new EmailAgent(builder, mock(CommunicationTools.class), manager);

        assertThrows(
                IllegalStateException.class,
                () -> agent.emailReport(report(), "operator@example.test")
        );

        InOrder lifecycle = inOrder(manager);
        lifecycle.verify(manager).start(eq("EmailAgent"), eq(WorkflowType.EMAIL_COMMUNICATION), anyString());
        lifecycle.verify(manager).fail();
        lifecycle.verify(manager).clear();
        verify(manager, never()).complete();
        verify(manager, never()).block();
    }

    @Test
    void validatesReportHandoffBeforeOpeningWorkflow() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        mockChatClient(builder, "Email sent.");
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        EmailAgent agent = new EmailAgent(builder, mock(CommunicationTools.class), manager);

        assertThrows(IllegalArgumentException.class, () -> agent.emailReport(null, "operator@example.test"));
        assertThrows(IllegalArgumentException.class, () -> agent.emailReport(report(), " "));
        verifyNoInteractions(manager);
    }

    @Test
    void registeredWorkflowDefinitionGrantsOnlyEmailCapability() throws IOException {
        WorkflowDefinitionRegistry registry = new WorkflowDefinitionRegistry();
        var definition = registry.get(WorkflowType.EMAIL_COMMUNICATION);
        var reportingDefinition = registry.get(WorkflowType.CUSTOMER_REPORTING);

        assertEquals(Set.of(WorkflowCapability.SEND_EMAIL), definition.capabilities());
        assertEquals(WorkflowType.EMAIL_COMMUNICATION, definition.type());
        assertFalse(reportingDefinition.allows(WorkflowCapability.SEND_EMAIL));
        assertTrue(reportingDefinition.allows(WorkflowCapability.GENERATE_REPORT));
    }

    private ReportTools.Report report() {
        return new ReportTools.Report(ReportTools.ReportType.ANALYTICAL, false, 1, "Report content");
    }

    private ChatClient mockChatClient(ChatClient.Builder builder, String response) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(response);
        return chatClient;
    }

    private void mockChatClientFailure(ChatClient.Builder builder, RuntimeException failure) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().user(anyString()).call().content()).thenThrow(failure);
    }
}
