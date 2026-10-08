package com.agentops.guardian.governance.audit;

import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.risk.GovernanceIntervention;
import com.agentops.guardian.governance.risk.RiskAssessment;
import com.agentops.guardian.governance.risk.RiskDecision;
import com.agentops.guardian.governance.risk.RiskFactor;
import com.agentops.guardian.governance.risk.RiskFactorType;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.governance.audit.live.GovernanceAuditEventPersisted;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

class PostgreSQLAuditServiceTest {

    @Test
    void mapsAuditEventFieldsAndSavesEntity() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(repository.save(any(AuditEventEntity.class))).thenAnswer(invocation -> {
            AuditEventEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 101L);
            return entity;
        });
        PostgreSQLAuditService service = new PostgreSQLAuditService(repository, eventPublisher);
        Instant timestamp = Instant.parse("2026-09-25T10:15:30Z");
        AuditEvent event = new AuditEvent(
                "workflow-1",
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                timestamp,
                AuditEventType.WORKFLOW_TRANSITION,
                "call-1",
                "getCustomerData",
                WorkflowCapability.READ_CUSTOMER_DATA,
                2,
                GovernanceDecision.DecisionType.ALLOW,
                "TEST_POLICY",
                "Allowed by policy.",

                AuditEvent.ExecutionStatus.SUCCESS,
                null,
                null,
                new WorkflowNodeSnapshot("START", WorkflowState.STARTED, WorkflowCapability.READ_CUSTOMER_DATA),
                new WorkflowNodeSnapshot("CUSTOMER_DATA", WorkflowState.DATA_ACQUIRED, WorkflowCapability.READ_CUSTOMER_DATA),
                WorkflowState.STARTED,
                WorkflowState.DATA_ACQUIRED
        );

        service.record(event);

        ArgumentCaptor<AuditEventEntity> entityCaptor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(repository).save(entityCaptor.capture());
        AuditEventEntity entity = entityCaptor.getValue();
        assertEquals(event.workflowId(), entity.getWorkflowId());
        assertEquals(event.agentName(), entity.getAgentName());
        assertEquals(event.workflowType(), entity.getWorkflowType());
        assertEquals(event.timestamp(), entity.getTimestamp());
        assertEquals(event.eventType(), entity.getEventType());
        assertEquals(event.toolCallId(), entity.getToolCallId());
        assertEquals(event.toolName(), entity.getToolName());
        assertEquals(event.capability(), entity.getCapability());
        assertEquals(event.policyDecision(), entity.getPolicyDecision());
        assertEquals("TEST_POLICY", entity.getPolicyId());        assertEquals(event.policyReason(), entity.getPolicyReason());
        assertEquals(event.executionStatus(), entity.getExecutionStatus());
        assertEquals("START", entity.getNodeBeforeId());
        assertEquals(WorkflowState.STARTED, entity.getNodeBeforeState());
        assertEquals("CUSTOMER_DATA", entity.getNodeAfterId());
        assertEquals(WorkflowState.DATA_ACQUIRED, entity.getNodeAfterState());
        assertEquals(event.stateBefore(), entity.getStateBefore());
        assertEquals(event.stateAfter(), entity.getStateAfter());
        assertNull(entity.getClassificationBefore());
        assertNull(entity.getClassificationAfter());
        assertNull(entity.getTransformationType());
        verify(eventPublisher).publishEvent(any(GovernanceAuditEventPersisted.class));
    }

    @Test
    void mapsRiskInterventionMetadataToEntity() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(repository.save(any(AuditEventEntity.class))).thenAnswer(invocation -> {
            AuditEventEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 102L);
            return entity;
        });
        PostgreSQLAuditService service = new PostgreSQLAuditService(repository, eventPublisher);
        RiskAssessment assessment = new RiskAssessment(
                RiskLevel.HIGH,
                List.of(new RiskFactor(RiskFactorType.EXTERNAL_SIDE_EFFECT, "External communication.")),
                "External communication requires approval."
        );
        RiskDecision decision = RiskDecision.from(assessment);
        AuditEvent event = AuditEvent.riskAssessment(
                "workflow-risk",
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "email-1",
                "sendEmail",
                WorkflowCapability.SEND_EMAIL,
                3,
                decision
        );

        service.record(event);

        ArgumentCaptor<AuditEventEntity> entityCaptor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(repository).save(entityCaptor.capture());
        AuditEventEntity entity = entityCaptor.getValue();
        assertEquals(RiskLevel.HIGH, entity.getRiskLevel());
        assertEquals(GovernanceIntervention.REQUIRE_APPROVAL, entity.getIntervention());
        assertEquals("External communication requires approval.", entity.getRiskReason());
        assertEquals(null, entity.getPolicyDecision());
    }

    @Test
    void doesNotPublishWhenPersistenceFails() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(repository.save(any(AuditEventEntity.class)))
                .thenThrow(new IllegalStateException("Persistence failed."));
        PostgreSQLAuditService service = new PostgreSQLAuditService(repository, eventPublisher);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.record(AuditEvent.proposedAction(
                        "workflow-failed", "Agent", WorkflowType.CUSTOMER_REPORTING,
                        "call-1", "sendEmail", WorkflowCapability.SEND_EMAIL, 1
                ))
        );

        org.mockito.Mockito.verifyNoInteractions(eventPublisher);
    }
}