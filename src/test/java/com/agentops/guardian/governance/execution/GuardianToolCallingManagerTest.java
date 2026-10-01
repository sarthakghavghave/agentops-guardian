package com.agentops.guardian.governance.execution;

import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.audit.AuditEventType;
import com.agentops.guardian.governance.audit.AuditService;
import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.DataTransformation;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GuardianToolCallingManagerTest {

    private WorkflowContext workflowContext;
    private WorkflowContextManager workflowContextManager;
    private GovernancePolicyEngine policyEngine;
    private ToolCapabilityRegistry toolCapabilityRegistry;
    private ToolCallingManager delegate;
    private AuditService auditService;
    private GuardianToolCallingManager guardian;
    private Prompt prompt;

    @BeforeEach
    void setUp() {
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.CUSTOMER_REPORTING,
                Set.of(
                        WorkflowCapability.READ_CUSTOMER_DATA,
                        WorkflowCapability.GENERATE_REPORT,
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
                new GovernanceDecision(GovernanceDecision.DecisionType.ALLOW, "Allowed for test.", "TEST_POLICY")
        );

        toolCapabilityRegistry = mock(ToolCapabilityRegistry.class);
        when(toolCapabilityRegistry.getCapability("getCustomerData"))
                .thenReturn(WorkflowCapability.READ_CUSTOMER_DATA);

        delegate = mock(ToolCallingManager.class);
        auditService = mock(AuditService.class);
        guardian = new GuardianToolCallingManager(
                workflowContextManager,
                policyEngine,
                toolCapabilityRegistry,
                delegate,
                auditService
        );
        prompt = mock(Prompt.class);
    }

    @Test
    void recordsAuditEventsAndAdvancesAfterSuccessfulExecution() {
        String toolCallId = "call-1";
        when(delegate.executeToolCalls(any(), any())).thenReturn(
                successfulResult(toolCallId)
        );

        guardian.executeToolCalls(prompt, chatResponse(toolCallId));

        verify(delegate, times(1)).executeToolCalls(eq(prompt), any(ChatResponse.class));
        assertEquals("CUSTOMER_DATA", workflowContext.getCurrentNodeId());

        verify(auditService).record(argThat(event ->
                event.eventType() == AuditEventType.PROPOSED_ACTION
                        && event.toolCallId().equals(toolCallId)
                        && event.toolName().equals("getCustomerData")
        ));
        verify(auditService).record(argThat(event ->
                event.eventType() == AuditEventType.POLICY_DECISION
                        && event.policyDecision() == GovernanceDecision.DecisionType.ALLOW
        ));
        verify(auditService).record(argThat(event ->
                event.eventType() == AuditEventType.EXECUTION_OUTCOME
                        && event.executionStatus() == AuditEvent.ExecutionStatus.SUCCESS
        ));
        verify(auditService).record(argThat(event ->
                event.eventType() == AuditEventType.WORKFLOW_TRANSITION
                        && event.nodeBefore() != null
                        && event.nodeAfter() != null
                        && "CUSTOMER_DATA".equals(event.nodeAfter().nodeId())
        ));
    }

    @Test
    void recordsBlockedExecutionWithoutDelegateCall() {
        when(policyEngine.evaluate(any(), any())).thenReturn(
                new GovernanceDecision(GovernanceDecision.DecisionType.BLOCK, "Blocked for test.", "TEST_POLICY")
        );

        assertThrows(
                GovernanceViolationException.class,
                () -> guardian.executeToolCalls(prompt, chatResponse("call-1"))
        );

        verify(auditService).record(argThat(event ->
        event.eventType() == AuditEventType.PROPOSED_ACTION
                && event.toolCallId().equals("call-1")
                && event.toolName().equals("getCustomerData")
        ));

        verifyNoInteractions(delegate);
        verify(auditService).record(argThat(event ->
                event.eventType() == AuditEventType.POLICY_DECISION
                        && event.policyDecision() == GovernanceDecision.DecisionType.BLOCK
                        && "TEST_POLICY".equals(event.policyId())
        ));
        verify(auditService).record(argThat(event ->
                event.eventType() == AuditEventType.EXECUTION_OUTCOME
                        && event.executionStatus() == AuditEvent.ExecutionStatus.BLOCKED
        ));
        assertEquals("START", workflowContext.getCurrentNodeId());
    }

    @Test
    void recordsFailedExecutionWithoutWorkflowAdvance() {
        String toolCallId = "call-1";
        when(delegate.executeToolCalls(any(), any()))
                .thenThrow(new IllegalStateException("Tool execution failed."));

        assertThrows(
                IllegalStateException.class,
                () -> guardian.executeToolCalls(prompt, chatResponse(toolCallId))
        );

        verify(delegate, times(1)).executeToolCalls(eq(prompt), any(ChatResponse.class));
        verify(auditService).record(argThat(event ->
                event.eventType() == AuditEventType.EXECUTION_OUTCOME
                        && event.executionStatus() == AuditEvent.ExecutionStatus.FAILED
                        && "Tool execution failed.".equals(event.errorMessage())
        ));
        assertEquals("START", workflowContext.getCurrentNodeId());
    }

        @Test
        void recordsTransformationOnSuccessfulReportExecution() {
                String toolCallId = "report-call-1";
                workflowContext.advanceAfterCapability(WorkflowCapability.READ_CUSTOMER_DATA);
                when(toolCapabilityRegistry.getCapability("generateReport"))
                                .thenReturn(WorkflowCapability.GENERATE_REPORT);
                when(delegate.executeToolCalls(any(), any())).thenAnswer(invocation -> {
                        workflowContext.recordTransformation(new DataTransformation(
                                        DataClassification.RAW_CUSTOMER_DATA,
                                        DataClassification.ANALYTICAL,
                                        "ANALYTICAL",
                                        java.time.Instant.now()
                        ));
                        return successfulResult(toolCallId);
                });

                guardian.executeToolCalls(prompt, chatResponse(toolCallId, "generateReport"));

                verify(auditService).record(argThat(event ->
                                event.eventType() == AuditEventType.EXECUTION_OUTCOME
                                                && event.executionStatus() == AuditEvent.ExecutionStatus.SUCCESS
                                                && event.classificationBefore() == DataClassification.RAW_CUSTOMER_DATA
                                                && event.classificationAfter() == DataClassification.ANALYTICAL
                                                && "ANALYTICAL".equals(event.transformationType())
                ));
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
                new GovernanceDecision(GovernanceDecision.DecisionType.BLOCK, "Blocked for test.", "TEST_POLICY")
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
