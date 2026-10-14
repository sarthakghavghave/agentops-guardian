package com.agentops.guardian.governance.execution;

import com.agentops.guardian.coordinator.ConversationMemory;
import com.agentops.guardian.coordinator.context.VerifiedCustomerReadEvidence;
import com.agentops.guardian.governance.audit.AuditService;
import com.agentops.guardian.governance.audit.AuditEventType;
import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.intervention.InterventionService;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.ToolInvocationOutcome;
import com.agentops.guardian.governance.policy.GovernancePolicyEngine;
import com.agentops.guardian.governance.risk.RiskEvaluator;
import com.agentops.guardian.governance.workflow.ToolCapabilityRegistry;
import com.agentops.guardian.governance.workflow.WorkflowDefinitionRegistry;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.service.CustomerService;
import com.agentops.guardian.domain.customer.Customer;
import com.agentops.guardian.tool.CustomerDataTools;
import com.agentops.guardian.tool.ToolDataStore;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.method.MethodToolCallback;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class ToolInvocationCorrelationIntegrationTest {

    private ObservationRegistry observationRegistry;
    private ToolInvocationCorrelation correlation;
    private WorkflowContext workflowContext;
    private WorkflowContextManager workflowContextManager;
    private GovernancePolicyEngine policyEngine;
    private AuditService auditService;
    private ToolCapabilityRegistry capabilities;
    private CustomerService customerService;
    private CustomerDataTools customerDataTools;
    private GuardianToolCallingManager guardian;
    private ConversationMemory sharedContextService;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        observationRegistry = ObservationRegistry.create();
        correlation = new ToolInvocationCorrelation(observationRegistry);
        sharedContextService = new ConversationMemory();
        conversationId = sharedContextService.open(null);
        workflowContext = new WorkflowContext(
                "CustomerSupportAgent",
                WorkflowType.CUSTOMER_SUPPORT,
                "Read customer data",
                new WorkflowDefinitionRegistry().get(WorkflowType.CUSTOMER_SUPPORT),
                conversationId
        );
        workflowContextManager = mock(WorkflowContextManager.class);
        when(workflowContextManager.current()).thenReturn(workflowContext);
        policyEngine = mock(GovernancePolicyEngine.class);
        when(policyEngine.evaluate(any(), any())).thenReturn(new GovernanceDecision(
                GovernanceDecision.DecisionType.ALLOW,
                "Allowed for integration test.",
                "TEST_POLICY"
        ));
        auditService = mock(AuditService.class);
        capabilities = mock(ToolCapabilityRegistry.class);
        customerService = mock(CustomerService.class);
        ToolDataStore toolDataStore = new ToolDataStore(workflowContextManager);
        customerDataTools = new CustomerDataTools(
                customerService,
                toolDataStore,
                workflowContextManager,
                correlation
        );
        guardian = createGuardian();
    }

    @Test
    void correlatesNormalTypedResultWithExactToolCallId() {
        when(capabilities.getCapability(CustomerDataTools.TOOL_NAME))
                .thenReturn(WorkflowCapability.READ_CUSTOMER_DATA);
        when(customerService.getCustomersByCity("North", 5)).thenReturn(List.of(
                Customer.builder()
                        .id("customer-north")
                        .name("Alex North")
                        .email("alex.north@example.test")
                        .gender("OTHER")
                        .city("North")
                        .signupDate(LocalDate.now())
                        .build()
        ));
        when(customerService.getCustomerOrders("customer-north")).thenReturn(List.of());

        execute(List.of(toolCall("call-north", "North")));

        ToolInvocationOutcome<?> outcome = workflowContext.getToolInvocationOutcomes().getFirst();
        assertEquals("call-north", outcome.toolCallId());
        assertEquals(CustomerDataTools.TOOL_NAME, outcome.toolName());
        assertEquals(ToolInvocationOutcome.Status.SUCCEEDED, outcome.status());
        CustomerDataTools.CustomerDataSet data =
                assertInstanceOf(CustomerDataTools.CustomerDataSet.class, outcome.typedResult());
        assertEquals("North", data.city());
        assertEquals(1, data.customerCount());
        assertEquals("CUSTOMER_DATA", workflowContext.getCurrentNodeId());
        assertEquals(List.of(new VerifiedCustomerReadEvidence(
                conversationId,
                workflowContext.getWorkflowId(),
                "call-north",
                WorkflowCapability.READ_CUSTOMER_DATA,
                ToolInvocationOutcome.Status.SUCCEEDED,
                outcome.completedAt(),
                List.of("alex.north@example.test")
        )), sharedContextService.getVerifiedCustomerReads(conversationId));
        WorkflowContext laterWorkflow = new WorkflowContext(
                "EmailAgent",
                WorkflowType.EMAIL_COMMUNICATION,
                "Send email",
                new WorkflowDefinitionRegistry().get(WorkflowType.EMAIL_COMMUNICATION),
                conversationId
        );
        assertNotEquals(workflowContext.getWorkflowId(), laterWorkflow.getWorkflowId());
        assertEquals("alex.north@example.test", sharedContextService.getVerifiedCustomerReads(
                laterWorkflow.getConversationId()).getFirst().customerEmails().getFirst());
        assertNull(laterWorkflow.getData("CUSTOMER_DATA"));
        verify(auditService).record(argThat(event ->
                event.eventType() == AuditEventType.EXECUTION_OUTCOME
                        && "call-north".equals(event.toolCallId())
                        && event.executionStatus() == AuditEvent.ExecutionStatus.SUCCESS
        ));
        assertEquals(0, correlation.activeInvocationCount());
    }

    @Test
    void handledToolExecutionExceptionIsRecordedAsFailureDespiteResponseText() {
        when(capabilities.getCapability(CustomerDataTools.TOOL_NAME))
                .thenReturn(WorkflowCapability.READ_CUSTOMER_DATA);
        when(customerService.getCustomersByCity("Failure", 5))
                .thenThrow(new IllegalStateException("Customer store is unavailable."));

        ToolExecutionResult result = execute(List.of(toolCall("call-failure", "Failure")));

        ToolInvocationOutcome<?> outcome = workflowContext.getToolInvocationOutcomes().getFirst();
        assertEquals("call-failure", outcome.toolCallId());
        assertEquals(ToolInvocationOutcome.Status.FAILED, outcome.status());
        assertTrue(sharedContextService.getVerifiedCustomerReads(conversationId).isEmpty());
        assertEquals(ToolExecutionException.class.getName(), outcome.failureType());
        assertNull(outcome.typedResult());
        String responseText = result.conversationHistory().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())
                .filter(response -> "call-failure".equals(response.id()))
                .map(ToolResponseMessage.ToolResponse::responseData)
                .findFirst()
                .orElseThrow();
        assertFalse(responseText.isBlank());
        assertTrue(responseText.contains("Customer store is unavailable."));
        assertEquals("START", workflowContext.getCurrentNodeId());
        verify(auditService).record(argThat(event ->
                event.eventType() == AuditEventType.EXECUTION_OUTCOME
                        && "call-failure".equals(event.toolCallId())
                        && event.executionStatus() == AuditEvent.ExecutionStatus.FAILED
        ));
        verify(auditService, never()).record(argThat(event ->
                event.eventType() == AuditEventType.EXECUTION_OUTCOME
                        && "call-failure".equals(event.toolCallId())
                        && event.executionStatus() == AuditEvent.ExecutionStatus.SUCCESS
        ));
        assertEquals(0, correlation.activeInvocationCount());
    }

    @Test
    void normalResponseTextWithoutTypedReturnIsUnconfirmed() {
        when(capabilities.getCapability(CustomerDataTools.TOOL_NAME))
                .thenReturn(WorkflowCapability.READ_CUSTOMER_DATA);
        ToolCallback generatedCallback = ToolCallbacks.from(customerDataTools)[0];
        ToolCallback responseOnlyCallback = new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return generatedCallback.getToolDefinition();
            }

            @Override
            public String call(String toolInput) {
                return "{\"city\":\"North\",\"customerCount\":0,\"customers\":[]}";
            }
        };

        execute(List.of(toolCall("call-response-only", "North")), List.of(responseOnlyCallback));

        ToolInvocationOutcome<?> outcome = workflowContext.getToolInvocationOutcomes().getFirst();
        assertEquals(ToolInvocationOutcome.Status.UNCONFIRMED, outcome.status());
        assertTrue(sharedContextService.getVerifiedCustomerReads(conversationId).isEmpty());
        assertNull(outcome.typedResult());
        assertEquals("START", workflowContext.getCurrentNodeId());
        verify(auditService, never()).record(argThat(event ->
                event.eventType() == AuditEventType.EXECUTION_OUTCOME
                        && event.executionStatus() == AuditEvent.ExecutionStatus.SUCCESS
        ));
        assertEquals(0, correlation.activeInvocationCount());
    }

    @Test
    void thrownCallbackExceptionIsRecordedAsFailureAndRethrown() {
        ToolCallback throwingCallback = new ToolCallback() {
            private final ToolCallback definition = ToolCallbacks.from(customerDataTools)[0];

            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return definition.getToolDefinition();
            }

            @Override
            public String call(String toolInput) {
                customerDataTools.getCustomerData("North", 5);
                throw new IllegalStateException("Callback failed after method return.");
            }
        };

        assertThrows(IllegalStateException.class, () -> execute(
                List.of(toolCall("call-thrown", "North")),
                List.of(throwingCallback)
        ));

        ToolInvocationOutcome<?> outcome = workflowContext.getToolInvocationOutcomes().getFirst();
        assertEquals("call-thrown", outcome.toolCallId());
        assertEquals(ToolInvocationOutcome.Status.FAILED, outcome.status());
        assertEquals(IllegalStateException.class.getName(), outcome.failureType());
        assertNull(outcome.typedResult());
        assertEquals(0, correlation.activeInvocationCount());
    }

    @Test
    void sameBatchCallsRetainTheirOwnIdsAndTypedResults() {
        when(customerService.getCustomersByCity(anyString(), anyInt())).thenReturn(List.of());
        stubCustomer("North", "customer-north", "north@example.test");
        stubCustomer("South", "customer-south", "south@example.test");

        execute(List.of(
                toolCall("call-one", "North"),
                toolCall("call-two", "South")
        ));

        assertEquals(2, workflowContext.getToolInvocationOutcomes().size());
        assertOutcome("call-one", "North", ToolInvocationOutcome.Status.SUCCEEDED);
        assertOutcome("call-two", "South", ToolInvocationOutcome.Status.SUCCEEDED);
        List<VerifiedCustomerReadEvidence> evidence =
                sharedContextService.getVerifiedCustomerReads(conversationId);
        assertEquals(2, evidence.size());
        assertTrue(evidence.stream().anyMatch(read ->
                read.sourceToolCallId().equals("call-one")
                        && read.customerEmails().equals(List.of("north@example.test"))));
        assertTrue(evidence.stream().anyMatch(read ->
                read.sourceToolCallId().equals("call-two")
                        && read.customerEmails().equals(List.of("south@example.test"))));
        assertEquals(0, correlation.activeInvocationCount());
    }

    @Test
    void blankProviderIdsReceiveDistinctExecutionIdsForSameBatchCalls() {
        when(customerService.getCustomersByCity(anyString(), anyInt())).thenReturn(List.of());

        execute(List.of(
                toolCall("", "North"),
                toolCall("", "South")
        ));

        List<String> executionIds = workflowContext.getToolInvocationOutcomes().stream()
                .map(ToolInvocationOutcome::toolCallId)
                .toList();
        assertEquals(2, executionIds.size());
        assertNotEquals(executionIds.get(0), executionIds.get(1));
        assertOutcome(executionIds.get(0), "North", ToolInvocationOutcome.Status.SUCCEEDED);
        assertOutcome(executionIds.get(1), "South", ToolInvocationOutcome.Status.SUCCEEDED);
    }

    @Test
    void duplicateProviderIdsAreNormalizedBeforeToolExecution() {
        when(customerService.getCustomersByCity(anyString(), anyInt())).thenReturn(List.of());

        execute(List.of(
                toolCall("reused-id", "North"),
                toolCall("reused-id", "South")
        ));

        List<String> executionIds = workflowContext.getToolInvocationOutcomes().stream()
                .map(ToolInvocationOutcome::toolCallId)
                .toList();
        assertEquals(2, executionIds.size());
        assertNotEquals(executionIds.get(0), executionIds.get(1));
        assertFalse(executionIds.contains("reused-id"));
        assertOutcome(executionIds.get(0), "North", ToolInvocationOutcome.Status.SUCCEEDED);
        assertOutcome(executionIds.get(1), "South", ToolInvocationOutcome.Status.SUCCEEDED);
        assertEquals(2, sharedContextService.getVerifiedCustomerReads(conversationId).size());
    }

    @Test
    void oneFailedCallDoesNotContaminateAnotherCallInTheSameBatch() {
        when(customerService.getCustomersByCity("North", 5)).thenReturn(List.of());
        when(customerService.getCustomersByCity("Failure", 5))
                .thenThrow(new IllegalStateException("Customer store is unavailable."));

        execute(List.of(
                toolCall("call-success", "North"),
                toolCall("call-failed", "Failure")
        ));

        assertEquals(2, workflowContext.getToolInvocationOutcomes().size());
        assertOutcome("call-success", "North", ToolInvocationOutcome.Status.SUCCEEDED);
        ToolInvocationOutcome<?> failure = outcome("call-failed");
        assertEquals(ToolInvocationOutcome.Status.FAILED, failure.status());
        assertNull(failure.typedResult());
        assertEquals(List.of("call-success"), sharedContextService.getVerifiedCustomerReads(conversationId)
                .stream()
                .map(VerifiedCustomerReadEvidence::sourceToolCallId)
                .toList());
        assertEquals(0, correlation.activeInvocationCount());
    }

    @Test
    void concurrentBatchesDoNotSelectOutcomesFromSharedWorkflowHistory() throws Exception {
        CountDownLatch failedInvocationEntered = new CountDownLatch(1);
        CountDownLatch allowFailedInvocationToFinish = new CountDownLatch(1);
        when(customerService.getCustomersByCity("Failure", 5)).thenAnswer(invocation -> {
            failedInvocationEntered.countDown();
            if (!allowFailedInvocationToFinish.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to finish the failed invocation.");
            }
            throw new IllegalStateException("Customer store is unavailable.");
        });
        when(customerService.getCustomersByCity("Success", 5)).thenReturn(List.of());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ToolExecutionResult> failedBatch = executor.submit(
                    () -> execute(List.of(toolCall("reused-call-id", "Failure")))
            );
            assertTrue(failedInvocationEntered.await(5, TimeUnit.SECONDS));

            Future<ToolExecutionResult> successfulBatch = executor.submit(
                    () -> execute(List.of(toolCall("reused-call-id", "Success")))
            );
            successfulBatch.get(5, TimeUnit.SECONDS);
            assertTrue(workflowContext.getToolInvocationOutcomes().stream()
                    .anyMatch(outcome -> outcome.toolCallId().equals("reused-call-id")
                            && outcome.status() == ToolInvocationOutcome.Status.SUCCEEDED));

            allowFailedInvocationToFinish.countDown();
            failedBatch.get(5, TimeUnit.SECONDS);
        } finally {
            allowFailedInvocationToFinish.countDown();
            executor.shutdownNow();
        }

        assertEquals(2, workflowContext.getToolInvocationOutcomes().size());
        assertEquals(1, workflowContext.getToolInvocationOutcomes().stream()
                .filter(outcome -> outcome.toolCallId().equals("reused-call-id")
                        && outcome.status() == ToolInvocationOutcome.Status.SUCCEEDED)
                .count());
        assertEquals(1, workflowContext.getToolInvocationOutcomes().stream()
                .filter(outcome -> outcome.toolCallId().equals("reused-call-id")
                        && outcome.status() == ToolInvocationOutcome.Status.FAILED)
                .count());
        assertEquals(1, sharedContextService.getVerifiedCustomerReads(conversationId).size());
        verify(auditService, times(1)).record(argThat(event ->
                event.eventType() == AuditEventType.EXECUTION_OUTCOME
                        && "reused-call-id".equals(event.toolCallId())
                        && event.executionStatus() == AuditEvent.ExecutionStatus.SUCCESS
        ));
        verify(auditService, times(1)).record(argThat(event ->
                event.eventType() == AuditEventType.EXECUTION_OUTCOME
                        && "reused-call-id".equals(event.toolCallId())
                        && event.executionStatus() == AuditEvent.ExecutionStatus.FAILED
        ));
        assertEquals(0, correlation.activeInvocationCount());
    }

    @Test
    void rejectedBatchOutcomeDoesNotLeaveWorkflowHistoryAheadOfBatch() {
        ToolInvocationCorrelation.InvocationBatch batch = correlation.newInvocationBatch();
        ToolInvocationOutcome<?> successfulOutcome = new ToolInvocationOutcome<>(
                "same-id",
                CustomerDataTools.TOOL_NAME,
                ToolInvocationOutcome.Status.SUCCEEDED,
                new CustomerDataTools.CustomerDataSet("North", 0, List.of()),
                null,
                Instant.now()
        );
        batch.recordOutcome(workflowContext, successfulOutcome);

        assertThrows(IllegalStateException.class, () -> batch.recordOutcome(
                workflowContext,
                new ToolInvocationOutcome<>(
                        "same-id",
                        CustomerDataTools.TOOL_NAME,
                        ToolInvocationOutcome.Status.FAILED,
                        null,
                        IllegalStateException.class.getName(),
                        Instant.now()
                )
        ));

        assertEquals(List.of(successfulOutcome), workflowContext.getToolInvocationOutcomes());
        assertEquals(successfulOutcome, batch.outcomesByToolCallId().get("same-id"));
    }

    @Test
    void failedWorkflowHistoryWriteRollsBackBatchOutcome() {
        ToolInvocationCorrelation.InvocationBatch batch = correlation.newInvocationBatch();
        workflowContext.complete();
        ToolInvocationOutcome<?> outcome = new ToolInvocationOutcome<>(
                "completed-workflow",
                CustomerDataTools.TOOL_NAME,
                ToolInvocationOutcome.Status.SUCCEEDED,
                new CustomerDataTools.CustomerDataSet("North", 0, List.of()),
                null,
                Instant.now()
        );

        assertThrows(IllegalStateException.class, () -> batch.recordOutcome(workflowContext, outcome));

        assertTrue(batch.outcomesByToolCallId().isEmpty());
        assertTrue(workflowContext.getToolInvocationOutcomes().isEmpty());
    }

    @Test
    void unhandledFailureDoesNotReplaceEarlierSuccessfulCallOutcome() {
        ToolCallback generatedCallback = ToolCallbacks.from(customerDataTools)[0];
        ToolCallback partiallyFailingCallback = new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return generatedCallback.getToolDefinition();
            }

            @Override
            public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
                if (toolInput.contains("\"Failure\"")) {
                    customerDataTools.getCustomerData("Failure", 5);
                    throw new IllegalStateException("Uncaught callback failure.");
                }
                return generatedCallback.call(toolInput, toolContext);
            }

            @Override
            public String call(String toolInput) {
                return call(toolInput, null);
            }
        };
        when(customerService.getCustomersByCity(anyString(), anyInt())).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> execute(List.of(
                toolCall("call-success", "North"),
                toolCall("call-failed", "Failure")
        ), List.of(partiallyFailingCallback)));

        assertEquals(2, workflowContext.getToolInvocationOutcomes().size());
        assertOutcome("call-success", "North", ToolInvocationOutcome.Status.SUCCEEDED);
        ToolInvocationOutcome<?> failure = outcome("call-failed");
        assertEquals(ToolInvocationOutcome.Status.FAILED, failure.status());
        assertEquals(IllegalStateException.class.getName(), failure.failureType());
        assertNull(failure.typedResult());
        assertEquals(0, correlation.activeInvocationCount());
        verify(auditService, times(1)).record(argThat(event ->
                event.eventType() == AuditEventType.EXECUTION_OUTCOME
                        && "call-success".equals(event.toolCallId())
                        && event.executionStatus() == AuditEvent.ExecutionStatus.SUCCESS
        ));
        verify(auditService, never()).record(argThat(event ->
                event.eventType() == AuditEventType.EXECUTION_OUTCOME
                        && "call-success".equals(event.toolCallId())
                        && event.executionStatus() == AuditEvent.ExecutionStatus.FAILED
        ));
        verify(auditService, times(1)).record(argThat(event ->
                event.eventType() == AuditEventType.EXECUTION_OUTCOME
                        && "call-failed".equals(event.toolCallId())
                        && event.executionStatus() == AuditEvent.ExecutionStatus.FAILED
        ));
    }

    @Test
    void resultConversionFailureDoesNotLeaveSuccessfulTypedResult() throws Exception {
        ToolCallback generatedCallback = ToolCallbacks.from(customerDataTools)[0];
        Method toolMethod = CustomerDataTools.class.getMethod(
                CustomerDataTools.TOOL_NAME,
                String.class,
                int.class
        );
        ToolCallResultConverter failingConverter = (result, returnType) -> {
            throw new ToolExecutionException(
                    generatedCallback.getToolDefinition(),
                    new IllegalStateException("Result conversion failed.")
            );
        };
        ToolCallback callback = MethodToolCallback.builder()
                .toolDefinition(generatedCallback.getToolDefinition())
                .toolMetadata(generatedCallback.getToolMetadata())
                .toolMethod(toolMethod)
                .toolObject(customerDataTools)
                .toolCallResultConverter(failingConverter)
                .build();
        when(customerService.getCustomersByCity("North", 5)).thenReturn(List.of());

        execute(List.of(toolCall("call-conversion", "North")), List.of(callback));

        ToolInvocationOutcome<?> outcome = workflowContext.getToolInvocationOutcomes().getFirst();
        assertEquals(ToolInvocationOutcome.Status.FAILED, outcome.status());
        assertEquals(ToolExecutionException.class.getName(), outcome.failureType());
        assertNull(outcome.typedResult());
        assertTrue(sharedContextService.getVerifiedCustomerReads(conversationId).isEmpty());
        assertEquals(0, correlation.activeInvocationCount());
    }

    @Test
    void blockedCallDoesNotCreateInvocationOutcome() {
        when(policyEngine.evaluate(any(), any())).thenReturn(new GovernanceDecision(
                GovernanceDecision.DecisionType.BLOCK,
                "Blocked by test policy.",
                "TEST_BLOCK"
        ));

        assertThrows(GovernanceViolationException.class, () -> execute(List.of(toolCall("call-blocked", "North"))));

        assertFalse(workflowContext.getToolInvocationOutcomes().stream()
                .anyMatch(outcome -> "call-blocked".equals(outcome.toolCallId())));
        assertTrue(sharedContextService.getVerifiedCustomerReads(conversationId).isEmpty());
        assertEquals(0, correlation.activeInvocationCount());
    }

    private GuardianToolCallingManager createGuardian() {
        ToolCallingManager delegate = ToolCallingManager.builder()
                .observationRegistry(observationRegistry)
                .build();
        return new GuardianToolCallingManager(
                workflowContextManager,
                policyEngine,
                capabilities,
                delegate,
                auditService,
                new RiskEvaluator(),
                mock(InterventionService.class),
                correlation,
                sharedContextService
        );
    }

    private ToolExecutionResult execute(List<AssistantMessage.ToolCall> calls) {
        return execute(calls, List.of(ToolCallbacks.from(customerDataTools)));
    }

    private ToolExecutionResult execute(List<AssistantMessage.ToolCall> calls, List<ToolCallback> callbacks) {
        Prompt prompt = new Prompt(
                new UserMessage("Read customer records."),
                ToolCallingChatOptions.builder().toolCallbacks(callbacks).build()
        );
        ChatResponse response = new ChatResponse(List.of(new Generation(
                AssistantMessage.builder().toolCalls(calls).build()
        )));

        return guardian.executeToolCalls(prompt, response);
    }

    private AssistantMessage.ToolCall toolCall(String id, String city) {
        return new AssistantMessage.ToolCall(
                id,
                "function",
                CustomerDataTools.TOOL_NAME,
                "{\"city\":\"" + city + "\",\"maxCustomers\":5}"
        );
    }

    private void assertOutcome(String callId, String city, ToolInvocationOutcome.Status status) {
        ToolInvocationOutcome<?> outcome = outcome(callId);
        assertEquals(status, outcome.status());
        CustomerDataTools.CustomerDataSet data =
                assertInstanceOf(CustomerDataTools.CustomerDataSet.class, outcome.typedResult());
        assertEquals(city, data.city());
    }

    private ToolInvocationOutcome<?> outcome(String callId) {
        return workflowContext.getToolInvocationOutcomes().stream()
                .filter(outcome -> outcome.toolCallId().equals(callId))
                .findFirst()
                .orElseThrow();
    }

    private void stubCustomer(String city, String customerId, String email) {
        when(customerService.getCustomersByCity(city, 5)).thenReturn(List.of(
                Customer.builder()
                        .id(customerId)
                        .name(customerId)
                        .email(email)
                        .gender("OTHER")
                        .city(city)
                        .signupDate(LocalDate.now())
                        .build()
        ));
        when(customerService.getCustomerOrders(customerId)).thenReturn(List.of());
    }
}
