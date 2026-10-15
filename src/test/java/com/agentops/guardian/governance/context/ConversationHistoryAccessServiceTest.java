package com.agentops.guardian.governance.context;

import com.agentops.guardian.coordinator.ConversationMemory;
import com.agentops.guardian.coordinator.context.VerifiedCustomerReadEvidence;
import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.audit.AuditService;
import com.agentops.guardian.governance.execution.ApprovedActionGrant;
import com.agentops.guardian.governance.intervention.GovernanceIntervention;
import com.agentops.guardian.governance.intervention.GovernanceInterventionStatus;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.ToolInvocationOutcome;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.policy.ConfigurablePolicyEvaluator;
import com.agentops.guardian.governance.policy.GovernancePolicyEngine;
import com.agentops.guardian.governance.policy.PolicyAction;
import com.agentops.guardian.governance.policy.PolicyConditionField;
import com.agentops.guardian.governance.policy.PolicyConditionOperator;
import com.agentops.guardian.governance.policy.PolicyDefinition;
import com.agentops.guardian.governance.policy.PolicyDefinitionEntity;
import com.agentops.guardian.governance.policy.PolicyDefinitionRepository;
import com.agentops.guardian.governance.policy.PolicyTarget;
import com.agentops.guardian.governance.workflow.ToolCapabilityRegistry;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowDefinitionRegistry;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationHistoryAccessServiceTest {

    @Test
    void deniesAndAuditsHistoryAccessForConversationWithVerifiedCustomerRead() {
        PolicyDefinitionRepository repository = mock(PolicyDefinitionRepository.class);
        when(repository.findAllByEnabledTrueOrderByPriorityAscPolicyIdAsc())
                .thenReturn(List.of(PolicyDefinitionEntity.from(historyAccessPolicy())));
        ConversationMemory memory = org.mockito.Mockito.spy(new ConversationMemory());
        UUID conversationId = memory.open(null);
        memory.recordVerifiedCustomerRead(new VerifiedCustomerReadEvidence(
                conversationId,
                "prior-workflow",
                "prior-customer-read",
                WorkflowCapability.READ_CUSTOMER_DATA,
                ToolInvocationOutcome.Status.SUCCEEDED,
                Instant.now(),
                List.of("customer@example.test")
        ));
        WorkflowContextManager manager = new WorkflowContextManager(
                new WorkflowContextHolder(), new WorkflowDefinitionRegistry()
        );
        AuditService auditService = mock(AuditService.class);
        ConversationHistoryAccessService service = new ConversationHistoryAccessService(
                manager,
                new GovernancePolicyEngine(new ConfigurablePolicyEvaluator(
                        repository, new ToolCapabilityRegistry(), memory
                )),
                auditService,
                memory,
                true
        );

        ConversationHistoryAccessService.HistoryAccessResult access = service.readHistory(conversationId);
        GovernanceDecision decision = access.decision();

        assertEquals(GovernanceDecision.DecisionType.BLOCK, decision.decision());
        assertFalse(access.allowed());
        assertTrue(access.messages().isEmpty());
        assertEquals("BLOCK_EMAIL_HISTORY_AFTER_CUSTOMER_READ", decision.policyId());
        assertEquals("EmailAgent cannot access shared conversation history because customer data was "
                + "previously retrieved in this conversation.", decision.reason());
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, org.mockito.Mockito.times(3)).record(events.capture());
        assertEquals(List.of("PROPOSED_ACTION", "POLICY_DECISION", "EXECUTION_OUTCOME"),
                events.getAllValues().stream().map(event -> event.eventType().name()).toList());
        assertTrue(events.getAllValues().stream()
                .allMatch(event -> conversationId.equals(event.conversationId())));
        assertTrue(events.getAllValues().stream()
                .allMatch(event -> events.getAllValues().getFirst().workflowId().equals(event.workflowId())));
        assertNotEquals("prior-workflow", events.getAllValues().getLast().workflowId());
        assertEquals(decision.reason(), events.getAllValues().get(1).policyReason());
        assertEquals(com.agentops.guardian.governance.audit.AuditEvent.ExecutionStatus.BLOCKED,
                events.getAllValues().getLast().executionStatus());
        verify(memory, org.mockito.Mockito.never()).history(conversationId);
        verify(repository).findAllByEnabledTrueOrderByPriorityAscPolicyIdAsc();
    }

    @Test
    void usesASeparateEmailWorkflowForEachHistoryDecision() {
        PolicyDefinitionRepository repository = mock(PolicyDefinitionRepository.class);
        when(repository.findAllByEnabledTrueOrderByPriorityAscPolicyIdAsc()).thenReturn(List.of());
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        WorkflowContextManager manager = new WorkflowContextManager(
                new WorkflowContextHolder(), new WorkflowDefinitionRegistry()
        );
        AuditService auditService = mock(AuditService.class);
        ConversationHistoryAccessService service = new ConversationHistoryAccessService(
                manager,
                new GovernancePolicyEngine(new ConfigurablePolicyEvaluator(
                        repository, new ToolCapabilityRegistry(), memory
                )),
                auditService,
                memory,
                true
        );

        service.readHistory(conversationId);
        service.readHistory(conversationId);

        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, org.mockito.Mockito.times(6)).record(events.capture());
        assertTrue(events.getAllValues().stream().allMatch(event ->
                conversationId.equals(event.conversationId())));
        assertEquals(List.of(AuditEvent.ExecutionStatus.SUCCESS, AuditEvent.ExecutionStatus.SUCCESS),
                List.of(events.getAllValues().get(2).executionStatus(),
                        events.getAllValues().get(5).executionStatus()));
        assertNotEquals(
                events.getAllValues().get(0).workflowId(),
                events.getAllValues().get(3).workflowId()
        );
    }

    @Test
    void approvedEmailContinuationStillBlocksHistoryAndCorrelatesTheDenial() {
        PolicyDefinitionRepository repository = mock(PolicyDefinitionRepository.class);
        when(repository.findAllByEnabledTrueOrderByPriorityAscPolicyIdAsc())
                .thenReturn(List.of(PolicyDefinitionEntity.from(historyAccessPolicy())));
        ConversationMemory memory = org.mockito.Mockito.spy(new ConversationMemory());
        UUID conversationId = memory.open(null);
        memory.recordTurn(conversationId, "Earlier request", "Earlier assistant response.");
        memory.recordVerifiedCustomerRead(new VerifiedCustomerReadEvidence(
                conversationId,
                "prior-workflow",
                "prior-customer-read",
                WorkflowCapability.READ_CUSTOMER_DATA,
                ToolInvocationOutcome.Status.SUCCEEDED,
                Instant.now(),
                List.of("customer@example.test")
        ));
        WorkflowContextManager manager = new WorkflowContextManager(
                new WorkflowContextHolder(), new WorkflowDefinitionRegistry()
        );
        AuditService auditService = mock(AuditService.class);
        ApprovedActionGrant grant = new ApprovedActionGrant();
        ConversationHistoryAccessService service = new ConversationHistoryAccessService(
                manager,
                new GovernancePolicyEngine(new ConfigurablePolicyEvaluator(
                        repository, new ToolCapabilityRegistry(), memory
                )),
                auditService,
                memory,
                new com.agentops.guardian.governance.config.GovernanceSettings(),
                grant
        );
        GovernanceIntervention approved = new GovernanceIntervention(
                "approved-send-email-1",
                "send-workflow",
                "send-call",
                "EmailAgent",
                WorkflowType.EMAIL_COMMUNICATION,
                WorkflowCapability.SEND_EMAIL,
                RiskLevel.HIGH,
                com.agentops.guardian.governance.risk.GovernanceIntervention.REQUIRE_APPROVAL,
                GovernanceInterventionStatus.APPROVED,
                "Approval required.",
                Instant.now(),
                Instant.now(),
                "reviewer",
                "Approved.",
                "EMAIL",
                com.agentops.guardian.governance.context.WorkflowState.REPORT_GENERATED,
                DataClassification.ANALYTICAL,
                conversationId,
                "sendEmail",
                ApprovedActionGrant.argumentsDigest("{}")
        );
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);

        ConversationHistoryAccessService.HistoryAccessResult access = grant.resume(
                approved, () -> service.readHistory(conversationId)
        );

        assertFalse(access.allowed());
        assertTrue(access.messages().isEmpty());
        verify(memory, org.mockito.Mockito.never()).history(conversationId);
        verify(auditService, org.mockito.Mockito.times(3)).record(events.capture());
        assertTrue(events.getAllValues().stream().allMatch(event ->
                "approved-send-email-1".equals(event.interventionId())
                        && conversationId.equals(event.conversationId())
        ));
        assertEquals(GovernanceDecision.DecisionType.BLOCK,
                events.getAllValues().get(1).policyDecision());
    }

    @Test
    void governanceDisabledAllowsHistoryWithoutStartingOrAuditingGovernanceWorkflow() {
        AuditService auditService = mock(AuditService.class);
        ConversationMemory memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        memory.recordTurn(conversationId, "Earlier request", "Earlier assistant response.");
        ConversationHistoryAccessService service =
                new ConversationHistoryAccessService(null, null, auditService, memory, false);

        ConversationHistoryAccessService.HistoryAccessResult access = service.readHistory(conversationId);

        assertEquals(GovernanceDecision.DecisionType.ALLOW, access.decision().decision());
        assertEquals("GOVERNANCE_DISABLED", access.decision().policyId());
        assertEquals(2, access.messages().size());
        org.mockito.Mockito.verifyNoInteractions(auditService);
    }

    private PolicyDefinition historyAccessPolicy() {
        return new PolicyDefinition(
                "BLOCK_EMAIL_HISTORY_AFTER_CUSTOMER_READ",
                "Block email access to history after customer read",
                "EmailAgent cannot access shared conversation history because customer data was "
                        + "previously retrieved in this conversation.",
                true,
                1,
                new PolicyTarget("readConversationHistory", WorkflowCapability.READ_CONVERSATION_HISTORY),
                PolicyConditionField.VERIFIED_CUSTOMER_READ_IN_CONVERSATION,
                PolicyConditionOperator.EQUALS,
                "true",
                PolicyAction.BLOCK
        );
    }
}
