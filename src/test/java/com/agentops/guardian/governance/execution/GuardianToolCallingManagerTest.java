package com.agentops.guardian.governance.execution;

import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.policy.GovernancePolicyEngine;
import com.agentops.guardian.governance.workflow.ToolCapabilityRegistry;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowDefinition;
import com.agentops.guardian.governance.workflow.WorkflowGraph;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class GuardianToolCallingManagerTest {

    private WorkflowContext workflowContext;
    private WorkflowContextManager workflowContextManager;
    private GovernancePolicyEngine policyEngine;
    private ToolCapabilityRegistry toolCapabilityRegistry;
    private ToolCallingManager delegate;
    private GuardianToolCallingManager guardian;
    private Prompt prompt;

    @BeforeEach
    void setUp() {
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.CUSTOMER_REPORTING,
                Set.of(
                        WorkflowCapability.READ_CUSTOMER_DATA,
                        WorkflowCapability.SEND_EMAIL
                ),
                WorkflowGraph.customerReportingGraph()
        );
        workflowContext = new WorkflowContext(
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "Read customer data",
                definition
        );
        workflowContextManager = mock(WorkflowContextManager.class);
        when(workflowContextManager.current()).thenReturn(workflowContext);

        policyEngine = mock(GovernancePolicyEngine.class);
        when(policyEngine.evaluate(any(), any())).thenReturn(
                new GovernanceDecision(GovernanceDecision.DecisionType.ALLOW, "Allowed for test.")
        );

        toolCapabilityRegistry = mock(ToolCapabilityRegistry.class);
        when(toolCapabilityRegistry.getCapability("getCustomerData"))
                .thenReturn(WorkflowCapability.READ_CUSTOMER_DATA);

        delegate = mock(ToolCallingManager.class);
        guardian = new GuardianToolCallingManager(
                workflowContextManager,
                policyEngine,
                toolCapabilityRegistry,
                delegate
        );
        prompt = mock(Prompt.class);
    }

    @Test
    void executesDelegateExactlyOnceAndAdvancesAfterSuccessfulExecution() {
        String toolCallId = "call-1";
        when(delegate.executeToolCalls(any(), any())).thenReturn(
                successfulResult(toolCallId)
        );

        guardian.executeToolCalls(prompt, chatResponse(toolCallId));

        verify(delegate, times(1)).executeToolCalls(eq(prompt), any(ChatResponse.class));
        assertEquals("CUSTOMER_DATA", workflowContext.getCurrentNodeId());
    }

    @Test
    void doesNotAdvanceWorkflowWhenDelegateExecutionFails() {
        String toolCallId = "call-1";
        when(delegate.executeToolCalls(any(), any()))
                .thenThrow(new IllegalStateException("Tool execution failed."));

        assertThrows(
                IllegalStateException.class,
                () -> {
            guardian.executeToolCalls(prompt, chatResponse(toolCallId));
                }
                );

        verify(delegate, times(1)).executeToolCalls(eq(prompt), any(ChatResponse.class));
        assertEquals("START", workflowContext.getCurrentNodeId());
    }

    @Test
    void blocksInvalidGraphTransitionBeforeDelegateExecution() {
        when(toolCapabilityRegistry.getCapability("sendEmail"))
                .thenReturn(WorkflowCapability.SEND_EMAIL);

        assertThrows(
                IllegalStateException.class,
                () -> guardian.executeToolCalls(prompt, chatResponse("call-1", "sendEmail"))
        );

        verifyNoInteractions(delegate);
        assertEquals("START", workflowContext.getCurrentNodeId());
    }

    @Test
    void blocksPolicyViolationBeforeDelegateExecution() {
        when(policyEngine.evaluate(any(), any())).thenReturn(
                new GovernanceDecision(GovernanceDecision.DecisionType.BLOCK, "Blocked for test.")
        );

        assertThrows(
                GovernanceViolationException.class,
                () -> guardian.executeToolCalls(prompt, chatResponse("call-1"))
        );

        verifyNoInteractions(delegate);
        assertEquals("START", workflowContext.getCurrentNodeId());
    }

    @Test
    void rejectsMultipleRecognizedCapabilitiesInOneBatch() {
        when(toolCapabilityRegistry.getCapability("generateReport"))
                .thenReturn(WorkflowCapability.GENERATE_REPORT);

        assertThrows(
                GovernanceViolationException.class,
                () -> guardian.executeToolCalls(
                        prompt,
                        chatResponse(
                                List.of(
                                        new AssistantMessage.ToolCall(
                                                "call-1", "function", "getCustomerData", "{}"
                                        ),
                                        new AssistantMessage.ToolCall(
                                                "call-2", "function", "generateReport", "{}"
                                        )
                                )
                        )
                )
        );

        verifyNoInteractions(delegate);
        assertEquals("START", workflowContext.getCurrentNodeId());
    }

    private ChatResponse chatResponse(String toolCallId) {
        return chatResponse(toolCallId, "getCustomerData");
    }

    private ChatResponse chatResponse(String toolCallId, String toolName) {
        return chatResponse(List.of(new AssistantMessage.ToolCall(
                toolCallId,
                "function",
                toolName,
                "{\"city\":\"New Roberttown\",\"maxCustomers\":1}"
        )));
    }

    private ChatResponse chatResponse(List<AssistantMessage.ToolCall> toolCalls) {
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(toolCalls)
                .build();
        return new ChatResponse(List.of(new Generation(assistantMessage)));
    }

    private ToolExecutionResult successfulResult(String toolCallId) {
        ToolResponseMessage responseMessage = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        toolCallId,
                        "getCustomerData",
                        "executed"
                )))
                .build();
        return ToolExecutionResult.builder()
                .conversationHistory(List.of(responseMessage))
                .build();
    }
}
