package com.agentops.guardian.governance.audit;

import com.agentops.guardian.governance.audit.dto.WorkflowSummaryResponse;
import com.agentops.guardian.governance.audit.dto.WorkflowTrajectoryResponse;
import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.service.WorkflowTrajectoryService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowTrajectoryServiceTest {

    @Test
    void reconstructsChronologicalTrajectoryAndSummaryFallbacks() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        WorkflowTrajectoryService service = new WorkflowTrajectoryService(repository);
        List<AuditEventEntity> workflowEvents = List.of(
                event("workflow-1", "2026-10-01T10:00:00Z", AuditEventType.PROPOSED_ACTION,
                        null, null, null, null, null, null),
                event("workflow-1", "2026-10-01T10:01:00Z", AuditEventType.WORKFLOW_TRANSITION,
                        new WorkflowNodeSnapshot("CUSTOMER_DATA", WorkflowState.DATA_ACQUIRED,
                                WorkflowCapability.READ_CUSTOMER_DATA),
                        new WorkflowNodeSnapshot("REPORT", WorkflowState.REPORT_GENERATED,
                                WorkflowCapability.GENERATE_REPORT),
                        WorkflowState.DATA_ACQUIRED, WorkflowState.REPORT_GENERATED,
                        DataClassification.RAW_CUSTOMER_DATA, DataClassification.ANALYTICAL),
                event("workflow-1", "2026-10-01T10:02:00Z", AuditEventType.EXECUTION_OUTCOME,
                        new WorkflowNodeSnapshot("REPORT", WorkflowState.REPORT_GENERATED,
                                WorkflowCapability.GENERATE_REPORT),
                        null,
                        WorkflowState.REPORT_GENERATED, null,
                        null, null)
        );
        when(repository.findByWorkflowIdOrderByTimestampAscIdAsc("workflow-1"))
                .thenReturn(workflowEvents);

        WorkflowTrajectoryResponse trajectory = service.getTrajectory("workflow-1");

        assertEquals("workflow-1", trajectory.workflowId());
        assertEquals(Instant.parse("2026-10-01T10:00:00Z"), trajectory.startedAt());
        assertEquals(Instant.parse("2026-10-01T10:02:00Z"), trajectory.lastEventAt());
        assertEquals("REPORT", trajectory.currentNodeId());
        assertEquals(WorkflowState.REPORT_GENERATED, trajectory.currentState());
        assertEquals(3, trajectory.eventCount());
        assertEquals(AuditEventType.PROPOSED_ACTION, trajectory.events().getFirst().eventType());
        assertEquals(AuditEventType.EXECUTION_OUTCOME, trajectory.events().getLast().eventType());
        assertEquals(DataClassification.RAW_CUSTOMER_DATA,
                trajectory.events().get(1).classificationBefore());
        assertEquals(DataClassification.ANALYTICAL,
                trajectory.events().get(1).classificationAfter());

        AuditEventEntity fallbackEvent = event(
                "workflow-before-only", "2026-10-01T11:00:00Z", AuditEventType.EXECUTION_OUTCOME,
                new WorkflowNodeSnapshot("CUSTOMER_DATA", WorkflowState.DATA_ACQUIRED,
                        WorkflowCapability.READ_CUSTOMER_DATA),
                null,
                WorkflowState.DATA_ACQUIRED, null,
                null, null
        );
        when(repository.findAllByOrderByWorkflowIdAscTimestampAscIdAsc()).thenReturn(
                List.of(workflowEvents.get(0), workflowEvents.get(1), workflowEvents.get(2), fallbackEvent)
        );
        List<WorkflowSummaryResponse> summaries = service.getSummaries();
        WorkflowSummaryResponse fallbackSummary = summaries.stream()
                .filter(summary -> summary.workflowId().equals("workflow-before-only"))
                .findFirst()
                .orElseThrow();
        assertEquals("CUSTOMER_DATA", fallbackSummary.currentNodeId());
        assertEquals(WorkflowState.DATA_ACQUIRED, fallbackSummary.currentState());
    }

    @Test
    void returnsNotFoundWhenWorkflowHasNoEvents() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.findByWorkflowIdOrderByTimestampAscIdAsc("missing"))
                .thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new WorkflowTrajectoryService(repository).getTrajectory("missing")
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    private AuditEventEntity event(
            String workflowId,
            String timestamp,
            AuditEventType eventType,
            WorkflowNodeSnapshot nodeBefore,
            WorkflowNodeSnapshot nodeAfter,
            WorkflowState stateBefore,
            WorkflowState stateAfter,
            DataClassification classificationBefore,
            DataClassification classificationAfter
    ) {
        AuditEvent event = new AuditEvent(
                workflowId,
                "ReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                Instant.parse(timestamp),
                eventType,
                null,
                null,
                null,
                null,
                GovernanceDecision.DecisionType.ALLOW,
                "TEST_POLICY",
                "Allowed.",
                AuditEvent.ExecutionStatus.SUCCESS,
                null,
                null,
                nodeBefore,
                nodeAfter,
                stateBefore,
                stateAfter,
                classificationBefore,
                classificationAfter,
                classificationAfter == null ? null : "ANALYTICAL"
        );
        return AuditEventEntity.from(event);
    }
}