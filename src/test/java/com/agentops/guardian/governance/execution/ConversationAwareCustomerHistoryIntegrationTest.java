package com.agentops.guardian.governance.execution;

import com.agentops.guardian.agent.EmailAgent;
import com.agentops.guardian.coordinator.ConversationMemory;
import com.agentops.guardian.coordinator.context.VerifiedCustomerReadEvidence;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.audit.AuditEventType;
import com.agentops.guardian.governance.audit.AuditService;
import com.agentops.guardian.governance.config.GovernanceSettings;
import com.agentops.guardian.governance.context.ConversationHistoryAccessService;
import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.context.WorkflowContextHolder;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.execution.ApprovedActionGrant;
import com.agentops.guardian.governance.intervention.GovernanceIntervention;
import com.agentops.guardian.governance.intervention.GovernanceInterventionStatus;
import com.agentops.guardian.governance.intervention.InterventionService;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.ToolInvocationOutcome;
import com.agentops.guardian.governance.policy.ConfigurablePolicyEvaluator;
import com.agentops.guardian.governance.policy.GovernancePolicyEngine;
import com.agentops.guardian.governance.policy.PolicyDefinition;
import com.agentops.guardian.governance.policy.PolicyDefinitionEntity;
import com.agentops.guardian.governance.policy.PolicyDefinitionRepository;
import com.agentops.guardian.governance.policy.PolicyAction;
import com.agentops.guardian.governance.policy.PolicyConditionField;
import com.agentops.guardian.governance.policy.PolicyConditionOperator;
import com.agentops.guardian.governance.policy.PolicyTarget;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.workflow.ToolCapabilityRegistry;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowDefinitionRegistry;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.service.CustomerService;
import com.agentops.guardian.tool.CommunicationTools;
import com.agentops.guardian.tool.CustomerTools;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationAwareCustomerHistoryIntegrationTest {

    @Test
    void customerNameLookupBlocksSameConversationHistoryBeforeTranscriptFetchAndApprovedSend() throws Exception {
        Fixture fixture = new Fixture();
        UUID conversationId = fixture.memory.open(null);
        fixture.memory.recordTurn(
                conversationId,
                "Give me the email ID of Michael Fletcher.",
                "Customer lookup result: michael.fletcher@example.test TRANSCRIPT_SECRET_MARKER"
        );

        WorkflowContext lookupWorkflow = fixture.workflowContextManager.start(
                "CustomerSupportAgent",
                WorkflowType.CUSTOMER_SUPPORT,
                "Give me the email ID of Michael Fletcher.",
                conversationId
        );
        fixture.invokeNameLookup("lookup-call-1", "Michael Fletcher");
        fixture.workflowContextManager.complete();
        fixture.workflowContextManager.clear();

        VerifiedCustomerReadEvidence evidence =
                fixture.memory.getVerifiedCustomerReads(conversationId).getFirst();
        assertEquals(conversationId, evidence.conversationId());
        assertEquals(lookupWorkflow.getWorkflowId(), evidence.sourceWorkflowId());
        assertEquals("lookup-call-1", evidence.sourceToolCallId());
        assertEquals(WorkflowCapability.READ_CUSTOMER_DATA, evidence.capability());
        assertEquals(ToolInvocationOutcome.Status.SUCCEEDED, evidence.outcome());
        assertEquals(List.of("michael.fletcher@example.test"), evidence.customerEmails());

        ConversationHistoryAccessService.HistoryAccessResult denied =
                fixture.historyAccessService.readHistory(conversationId);
        assertFalse(denied.allowed());
        assertTrue(denied.messages().isEmpty());
        assertEquals("BLOCK_EMAIL_HISTORY_AFTER_CUSTOMER_READ", denied.decision().policyId());
        verify(fixture.interventionService, never())
                .createPending(any(), anyString(), anyString(), anyString(), any(), any());
        verify(fixture.auditService).record(org.mockito.ArgumentMatchers.argThat(
                event -> event.eventType() == AuditEventType.POLICY_DECISION
                        && event.policyDecision() == GovernanceDecision.DecisionType.BLOCK
                        && "BLOCK_EMAIL_HISTORY_AFTER_CUSTOMER_READ".equals(event.policyId())
        ));
        verify(fixture.auditService).record(org.mockito.ArgumentMatchers.argThat(
                event -> event.eventType() == AuditEventType.EXECUTION_OUTCOME
                        && event.executionStatus() == AuditEvent.ExecutionStatus.BLOCKED
        ));

        fixture.emailAgent.execute(
                conversationId,
                new AgentCoordinatorRequest(
                        "Email Michael Fletcher at michael.fletcher@example.test.",
                        null,
                        null
                )
        );
        List<Message> decisionMessages = fixture.decisionMessages();
        assertTrue(decisionMessages.stream().map(Message::getText)
                .noneMatch(text -> text.contains("TRANSCRIPT_SECRET_MARKER")));
        assertTrue(decisionMessages.stream().map(Message::getText)
                .anyMatch(text -> text.contains("Email Michael Fletcher at michael.fletcher@example.test.")));
        verify(fixture.memory, never()).history(conversationId);

        GovernanceIntervention approved = fixture.pendingEmailIntervention(conversationId)
                .approve("reviewer", "Approved.", Instant.now());
        assertThrows(GovernanceViolationException.class, () -> fixture.grant.resume(
                approved,
                () -> fixture.emailAgent.resumeApprovedAction(
                        conversationId,
                        approved.interventionId(),
                        approved.toolCallId(),
                        approved.toolName(),
                        "{\"recipient\":\"michael.fletcher@example.test\","
                                + "\"subject\":\"Follow-up\",\"body\":\"Hello Michael.\"}",
                        null
                )
        ));
        verify(fixture.communicationTools, never()).sendEmail(anyString(), anyString(), anyString());
        verify(fixture.memory, never()).history(conversationId);

        UUID separateConversation = fixture.memory.open(null);
        ConversationHistoryAccessService.HistoryAccessResult allowed =
                fixture.historyAccessService.readHistory(separateConversation);
        assertTrue(allowed.allowed());
        verify(fixture.memory).history(separateConversation);
    }

    @Test
    void missingAndFailedCustomerNameLookupsDoNotCreateVerifiedEvidence() throws Exception {
        Fixture fixture = new Fixture();
        UUID conversationId = fixture.memory.open(null);

        fixture.workflowContextManager.start(
                "CustomerSupportAgent",
                WorkflowType.CUSTOMER_SUPPORT,
                "Look up an unknown customer.",
                conversationId
        );
        fixture.invokeNameLookup("lookup-empty", "Nobody");
        WorkflowContext emptyLookupWorkflow = fixture.workflowContextManager.current();
        assertEquals(
                ToolInvocationOutcome.Status.UNCONFIRMED,
                emptyLookupWorkflow.getToolInvocationOutcomes().getFirst().status()
        );
        fixture.workflowContextManager.complete();
        fixture.workflowContextManager.clear();
        assertTrue(fixture.memory.getVerifiedCustomerReads(conversationId).isEmpty());

        when(fixture.customerService.searchByName("Broken")).thenThrow(new IllegalStateException("Lookup failed."));
        fixture.workflowContextManager.start(
                "CustomerSupportAgent",
                WorkflowType.CUSTOMER_SUPPORT,
                "Look up a customer.",
                conversationId
        );
        fixture.invokeNameLookup("lookup-failed", "Broken");
        WorkflowContext failedLookupWorkflow = fixture.workflowContextManager.current();
        assertEquals(
                ToolInvocationOutcome.Status.FAILED,
                failedLookupWorkflow.getToolInvocationOutcomes().getFirst().status()
        );
        fixture.workflowContextManager.clear();
        assertTrue(fixture.memory.getVerifiedCustomerReads(conversationId).isEmpty());
    }

    @Test
    void successfulCustomerIdLookupRecordsVerifiedEvidence() throws Exception {
        Fixture fixture = new Fixture();
        UUID conversationId = fixture.memory.open(null);
        when(fixture.customerService.getCustomer("customer-michael"))
                .thenReturn(com.agentops.guardian.domain.customer.Customer.builder()
                        .id("customer-michael")
                        .name("Michael Fletcher")
                        .email("michael.fletcher@example.test")
                        .gender("OTHER")
                        .city("North")
                        .signupDate(LocalDate.of(2024, 1, 1))
                        .build());
        WorkflowContext lookupWorkflow = fixture.workflowContextManager.start(
                "CustomerSupportAgent",
                WorkflowType.CUSTOMER_SUPPORT,
                "Look up customer by ID.",
                conversationId
        );

        fixture.invokeCustomerIdLookup("lookup-by-id", "customer-michael");
        fixture.workflowContextManager.complete();
        fixture.workflowContextManager.clear();

        VerifiedCustomerReadEvidence evidence =
                fixture.memory.getVerifiedCustomerReads(conversationId).getFirst();
        assertEquals(conversationId, evidence.conversationId());
        assertEquals(lookupWorkflow.getWorkflowId(), evidence.sourceWorkflowId());
        assertEquals("lookup-by-id", evidence.sourceToolCallId());
        assertEquals(WorkflowCapability.READ_CUSTOMER_DATA, evidence.capability());
        assertEquals(ToolInvocationOutcome.Status.SUCCEEDED, evidence.outcome());
        assertEquals(List.of("michael.fletcher@example.test"), evidence.customerEmails());
    }

    private static final class Fixture {
        private final ConversationMemory memory = spy(new ConversationMemory());
        private final WorkflowContextManager workflowContextManager = new WorkflowContextManager(
                new WorkflowContextHolder(), new WorkflowDefinitionRegistry()
        );
        private final ObservationRegistry observationRegistry = ObservationRegistry.create();
        private final ToolInvocationCorrelation correlation = new ToolInvocationCorrelation(observationRegistry);
        private final ToolCapabilityRegistry capabilities = new ToolCapabilityRegistry();
        private final CustomerService customerService = mock(CustomerService.class);
        private final InterventionService interventionService = mock(InterventionService.class);
        private final AuditService auditService = mock(AuditService.class);
        private final ApprovedActionGrant grant = new ApprovedActionGrant();
        private final GovernanceSettings settings = new GovernanceSettings();
        private final ConfigurablePolicyEvaluator policyEngine;
        private final GovernancePolicyEngine governancePolicyEngine;
        private final GuardianToolCallingManager guardianManager;
        private final ConversationHistoryAccessService historyAccessService;
        private final CustomerTools customerTools;
        private final CommunicationTools communicationTools = mock(CommunicationTools.class);
        private final EmailAgent emailAgent;
        private ChatClient decisionClient;

        private Fixture() throws Exception {
            settings.setEnabled(true);
            PolicyDefinitionRepository policyRepository = mock(PolicyDefinitionRepository.class);
            PolicyDefinition historyPolicy = new PolicyDefinition(
                    "BLOCK_EMAIL_HISTORY_AFTER_CUSTOMER_READ",
                    "Block email access to history after customer read",
                    "EmailAgent cannot access shared conversation history because customer data was "
                            + "previously retrieved in this conversation.",
                    true,
                    1,
                    new PolicyTarget(
                            "readConversationHistory",
                            WorkflowCapability.READ_CONVERSATION_HISTORY
                    ),
                    PolicyConditionField.VERIFIED_CUSTOMER_READ_IN_CONVERSATION,
                    PolicyConditionOperator.EQUALS,
                    "true",
                    PolicyAction.BLOCK
            );
            when(policyRepository.findAllByEnabledTrueOrderByPriorityAscPolicyIdAsc())
                    .thenReturn(List.of(PolicyDefinitionEntity.from(historyPolicy)));
            policyEngine = new ConfigurablePolicyEvaluator(policyRepository, capabilities, memory);
            governancePolicyEngine = new GovernancePolicyEngine(policyEngine, settings);
            guardianManager = new GuardianToolCallingManager(
                    workflowContextManager,
                    governancePolicyEngine,
                    capabilities,
                    auditService,
                    new com.agentops.guardian.governance.risk.RiskEvaluator(),
                    interventionService,
                    correlation,
                    memory,
                    settings,
                    grant
            );
            historyAccessService = new ConversationHistoryAccessService(
                    workflowContextManager,
                    governancePolicyEngine,
                    auditService,
                    memory,
                    settings,
                    grant
            );
            customerTools = new CustomerTools(customerService, correlation);
            when(customerService.searchByName("Michael Fletcher")).thenReturn(List.of(
                    com.agentops.guardian.domain.customer.Customer.builder()
                            .id("customer-michael")
                            .name("Michael Fletcher")
                            .email("michael.fletcher@example.test")
                            .gender("OTHER")
                            .city("North")
                            .signupDate(LocalDate.of(2024, 1, 1))
                            .build()
            ));
            emailAgent = createEmailAgent();
            clearInvocations(memory);
        }

        private void invokeNameLookup(String toolCallId, String name) {
            invokeLookup(toolCallId, CustomerTools.SEARCH_CUSTOMERS_BY_NAME_TOOL_NAME,
                    "{\"name\":\"" + name + "\"}");
        }

        private void invokeCustomerIdLookup(String toolCallId, String customerId) {
            invokeLookup(toolCallId, CustomerTools.GET_CUSTOMER_TOOL_NAME,
                    "{\"customerId\":\"" + customerId + "\"}");
        }

        private void invokeLookup(String toolCallId, String toolName, String arguments) {
            AssistantMessage toolCallResponse = AssistantMessage.builder()
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                            toolCallId,
                            "function",
                            toolName,
                            arguments
                    )))
                    .build();
            Prompt prompt = new Prompt(
                    new UserMessage("Look up customer by name."),
                    ToolCallingChatOptions.builder().toolCallbacks(ToolCallbacks.from(customerTools)).build()
            );
            guardianManager.executeToolCalls(prompt, new ChatResponse(List.of(new Generation(toolCallResponse))));
        }

        private EmailAgent createEmailAgent() throws java.io.IOException {
            ChatClient.Builder builder = mock(ChatClient.Builder.class);
            when(builder.defaultSystem(anyString())).thenReturn(builder);
            when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
            decisionClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
            ChatClient sendClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
            when(builder.build()).thenReturn(decisionClient, sendClient);
            when(decisionClient.prompt().messages(anyList()).call().entity(EmailAgent.EmailDecision.class))
                    .thenReturn(new EmailAgent.EmailDecision(
                            EmailAgent.EmailDecisionOutcome.CLARIFICATION_REQUIRED,
                            "Please confirm the message.",
                            null,
                            null,
                            null,
                            false,
                            null
                    ));
            return new EmailAgent(
                    builder,
                    communicationTools,
                    workflowContextManager,
                    memory,
                    null,
                    historyAccessService,
                    guardianManager
            );
        }

        private List<Message> decisionMessages() {
            ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
            verify(decisionClient.prompt(), org.mockito.Mockito.atLeastOnce()).messages(messages.capture());
            return messages.getAllValues().getLast();
        }

        private GovernanceIntervention pendingEmailIntervention(UUID conversationId) {
            String arguments = "{\"recipient\":\"michael.fletcher@example.test\","
                    + "\"subject\":\"Follow-up\",\"body\":\"Hello Michael.\"}";
            return new GovernanceIntervention(
                    "approved-email-history-test",
                    "email-source-workflow",
                    "email-tool-call",
                    "EmailAgent",
                    WorkflowType.EMAIL_COMMUNICATION,
                    WorkflowCapability.SEND_EMAIL,
                    RiskLevel.HIGH,
                    com.agentops.guardian.governance.risk.GovernanceIntervention.REQUIRE_APPROVAL,
                    GovernanceInterventionStatus.PENDING,
                    "External email requires approval.",
                    Instant.now(),
                    null,
                    null,
                    null,
                    "START",
                    com.agentops.guardian.governance.context.WorkflowState.STARTED,
                    null,
                    conversationId,
                    "sendEmail",
                    ApprovedActionGrant.argumentsDigest(arguments)
            );
        }
    }
}
