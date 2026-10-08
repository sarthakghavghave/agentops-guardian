package com.agentops.guardian.agent;

import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.tool.CustomerDataTools;
import com.agentops.guardian.tool.ReportTools;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.chat.client.ChatClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
