package com.agentops.guardian.governance.audit;

import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostgreSQLAuditServiceTest {

    @Test
    void mapsAuditEventFieldsAndSavesEntity() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        PostgreSQLAuditService service = new PostgreSQLAuditService(repository);
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
        assertEquals(event.policyReason(), entity.getPolicyReason());
        assertEquals(event.executionStatus(), entity.getExecutionStatus());
        assertEquals("START", entity.getNodeBeforeId());
        assertEquals(WorkflowState.STARTED, entity.getNodeBeforeState());
        assertEquals("CUSTOMER_DATA", entity.getNodeAfterId());
        assertEquals(WorkflowState.DATA_ACQUIRED, entity.getNodeAfterState());
        assertEquals(event.stateBefore(), entity.getStateBefore());
        assertEquals(event.stateAfter(), entity.getStateAfter());
    }
}