package com.agentops.guardian.agent;

import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.coordinator.ConversationMemory;
import com.agentops.guardian.coordinator.context.Artifact;
import com.agentops.guardian.coordinator.context.ArtifactType;
import com.agentops.guardian.coordinator.context.ExecutionStatus;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.tool.CustomerDataTools;
import com.agentops.guardian.tool.ReportTools;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.chat.client.ChatClient;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CustomerReportAgentTest {

    @Test
    void registersOnlyCustomerDataAndReportTools() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mockChatClient(builder, "Report generated.");
        CustomerDataTools customerDataTools = mock(CustomerDataTools.class);
        ReportTools reportTools = mock(ReportTools.class);
        WorkflowContextManager workflowContextManager = mock(WorkflowContextManager.class);

        CustomerReportAgent agent = new CustomerReportAgent(
                builder, customerDataTools, reportTools, workflowContextManager
        );

        ArgumentCaptor<Object[]> tools = ArgumentCaptor.forClass(Object[].class);
        verify(builder).defaultTools(tools.capture());
        assertEquals(2, tools.getValue().length);
        assertEquals(customerDataTools, tools.getValue()[0]);
        assertEquals(reportTools, tools.getValue()[1]);
        assertFalse(java.util.Arrays.stream(tools.getValue())
                .anyMatch(tool -> tool instanceof com.agentops.guardian.tool.CommunicationTools));
        assertEquals("Report generated.", agent.generateReport("Generate report"));
    }

    @Test
    void preservesSuccessfulWorkflowLifecycle() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        mockChatClient(builder, "Report generated.");
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        CustomerReportAgent agent = new CustomerReportAgent(
                builder, mock(CustomerDataTools.class), mock(ReportTools.class), manager
        );

        assertEquals("Report generated.", agent.generateReport("Generate report"));

        InOrder lifecycle = inOrder(manager);
        lifecycle.verify(manager).start("CustomerReportAgent", WorkflowType.CUSTOMER_REPORTING, "Generate report");
        lifecycle.verify(manager).complete();
        lifecycle.verify(manager).clear();
        verify(manager, never()).block();
        verify(manager, never()).fail();
    }

    @Test
    void startsReportWorkflowWithCoordinatorProvidedIdentity() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        mockChatClient(builder, "Report generated.");
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        CustomerReportAgent agent = new CustomerReportAgent(
                builder, mock(CustomerDataTools.class), mock(ReportTools.class), manager
        );
        UUID conversationId = UUID.randomUUID();
        String workflowId = UUID.randomUUID().toString();

        agent.generateReport("Generate report.", conversationId, workflowId);

        verify(manager).start(
                workflowId,
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "Generate report.",
                conversationId
        );
        verify(manager).complete();
        verify(manager).clear();
    }

    @Test
    void governanceViolationBlocksAndClearsWorkflow() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        mockChatClientFailure(builder, new GovernanceViolationException("Blocked by governance."));
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        CustomerReportAgent agent = new CustomerReportAgent(
                builder, mock(CustomerDataTools.class), mock(ReportTools.class), manager
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                GovernanceViolationException.class,
                () -> agent.generateReport("Generate report")
        );

        InOrder lifecycle = inOrder(manager);
        lifecycle.verify(manager).start("CustomerReportAgent", WorkflowType.CUSTOMER_REPORTING, "Generate report");
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
        CustomerReportAgent agent = new CustomerReportAgent(
                builder, mock(CustomerDataTools.class), mock(ReportTools.class), manager
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> agent.generateReport("Generate report")
        );

        InOrder lifecycle = inOrder(manager);
        lifecycle.verify(manager).start("CustomerReportAgent", WorkflowType.CUSTOMER_REPORTING, "Generate report");
        lifecycle.verify(manager).fail();
        lifecycle.verify(manager).clear();
        verify(manager, never()).complete();
        verify(manager, never()).block();
    }

    @Test
    void registersOnlyReportToolOutputAfterVerifiedGeneration() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        mockChatClient(builder, "Assistant summary");
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        ReportTools reportTools = mock(ReportTools.class);
        when(reportTools.executionResults()).thenReturn(java.util.List.of(
                new ReportTools.Report(
                        ReportTools.ReportType.ANALYTICAL,
                        false,
                        3,
                        "Verified report output",
                        "data/reports/generated.pdf",
                        128
                )
        ));
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        CustomerReportAgent agent = new CustomerReportAgent(
                builder, mock(CustomerDataTools.class), reportTools, manager, memory
        );

        agent.execute(conversationId, new AgentCoordinatorRequest("Generate the report", null, null));

        verify(manager).start(
                eq("CustomerReportAgent"),
                eq(WorkflowType.CUSTOMER_REPORTING),
                anyString(),
                eq(conversationId)
        );
        Artifact report = memory.getLatestArtifact(conversationId, ArtifactType.REPORT).orElseThrow();
        assertEquals("data/reports/generated.pdf", report.content());
        assertEquals(Artifact.GENERATED_PROVENANCE, report.metadata().get(Artifact.PROVENANCE_KEY));
        assertEquals("data/reports/generated.pdf", report.metadata().get(Artifact.FILE_REFERENCE_KEY));
        assertEquals("PDF", report.metadata().get(Artifact.FORMAT_KEY));
        assertEquals("ANALYTICAL", report.metadata().get(Artifact.REPORT_TYPE_KEY));
        assertEquals("3", report.metadata().get("customerCount"));
        assertFalse(report.content().contains("Verified report output"));
        assertEquals(ExecutionStatus.SUCCESS, memory.getLatestExecution(conversationId).orElseThrow().status());
        verify(reportTools).beginExecution();
        verify(reportTools).clearExecutionResult();
    }

    @Test
    void reportAgentReceivesRecentUserAndAssistantConversationContext() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mockChatClient(builder, "Assistant summary");
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        ReportTools reportTools = mock(ReportTools.class);
        when(reportTools.executionResults()).thenReturn(java.util.List.of(
                new ReportTools.Report(
                        ReportTools.ReportType.ANALYTICAL,
                        false,
                        3,
                        "Verified report output",
                        "data/reports/generated.pdf",
                        128
                )
        ));
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        memory.recordTurn(
                conversationId,
                "Generate the report for Seattle",
                "The report was created with ID report-123."
        );
        CustomerReportAgent agent = new CustomerReportAgent(
                builder, mock(CustomerDataTools.class), reportTools, manager, memory
        );

        clearInvocations(client.prompt());
        agent.execute(
                conversationId,
                new AgentCoordinatorRequest("Generate another report for the same city", null, null)
        );

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client.prompt()).user(prompt.capture());
        assertTrue(prompt.getValue().contains("User: Generate the report for Seattle"));
        assertTrue(prompt.getValue().contains("Assistant: The report was created with ID report-123."));
        assertTrue(prompt.getValue().contains("Generate another report for the same city"));
    }

    @Test
    void doesNotRegisterOrReportSuccessWhenAnalyticalPdfWasNotStored() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        mockChatClient(builder, "Assistant summary");
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        ReportTools reportTools = mock(ReportTools.class);
        when(reportTools.executionResults()).thenReturn(java.util.List.of(
                new ReportTools.Report(
                        ReportTools.ReportType.ANALYTICAL, false, 3, "Report text", null, 0
                )
        ));
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        CustomerReportAgent agent = new CustomerReportAgent(
                builder, mock(CustomerDataTools.class), reportTools, manager, memory
        );

        assertThrows(
                IllegalStateException.class,
                () -> agent.execute(conversationId, new AgentCoordinatorRequest("Generate the report", null, null))
        );

        assertTrue(memory.getLatestArtifact(conversationId, ArtifactType.REPORT).isEmpty());
        assertEquals(ExecutionStatus.FAILED, memory.getLatestExecution(conversationId).orElseThrow().status());
    }

    @Test
    void doesNotRegisterReportWhenToolExecutionIsUnconfirmed() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        mockChatClient(builder, "Fabricated-looking assistant report");
        WorkflowContextManager manager = mock(WorkflowContextManager.class);
        ReportTools reportTools = mock(ReportTools.class);
        when(reportTools.executionResults()).thenReturn(java.util.List.of());
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        CustomerReportAgent agent = new CustomerReportAgent(
                builder, mock(CustomerDataTools.class), reportTools, manager, memory
        );

        assertThrows(
                IllegalStateException.class,
                () -> agent.execute(conversationId, new AgentCoordinatorRequest("Generate a report", null, null))
        );

        assertTrue(memory.getLatestArtifact(conversationId, ArtifactType.REPORT).isEmpty());
        assertEquals(ExecutionStatus.FAILED, memory.getLatestExecution(conversationId).orElseThrow().status());
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
