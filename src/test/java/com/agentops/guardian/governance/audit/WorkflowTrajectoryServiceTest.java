package com.agentops.guardian.governance.audit;

import com.agentops.guardian.governance.audit.dto.WorkflowSummaryResponse;
import com.agentops.guardian.governance.audit.dto.WorkflowTrajectoryResponse;
import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.risk.GovernanceIntervention;
import com.agentops.guardian.governance.risk.RiskDecision;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.service.WorkflowTrajectoryService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowTrajectoryServiceTest {

    @Test
    void summarizesPersistedAllowedAndAutonomouslyBlockedActionsByToolCall() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        String workflowId = "workflow-counts";
        List<AuditEventEntity> events = List.of(
                entity(AuditEvent.policyDecision(
                        workflowId, "EmailAgent", WorkflowType.EMAIL_COMMUNICATION,
                        "call-allowed", "sendEmail", WorkflowCapability.SEND_EMAIL,
                        new GovernanceDecision(GovernanceDecision.DecisionType.ALLOW, "Allowed.", "EMAIL")
                )),
                entity(AuditEvent.riskAssessment(
                        workflowId, "EmailAgent", WorkflowType.EMAIL_COMMUNICATION,
                        "call-allowed", "sendEmail", WorkflowCapability.SEND_EMAIL, 1,
                        new RiskDecision(RiskLevel.LOW, GovernanceIntervention.NONE, "Low risk.")
                )),
                entity(AuditEvent.executionOutcome(
                        workflowId, "EmailAgent", WorkflowType.EMAIL_COMMUNICATION,
                        "call-allowed", "sendEmail", WorkflowCapability.SEND_EMAIL,
                        AuditEvent.ExecutionStatus.SUCCESS, null, null, null, null, null, null
                )),
                entity(AuditEvent.policyDecision(
                        workflowId, "EmailAgent", WorkflowType.EMAIL_COMMUNICATION,
                        "call-audit", "sendEmail", WorkflowCapability.SEND_EMAIL,
                        new GovernanceDecision(GovernanceDecision.DecisionType.ALLOW, "Allowed.", "EMAIL")
                )),
                entity(AuditEvent.riskAssessment(
                        workflowId, "EmailAgent", WorkflowType.EMAIL_COMMUNICATION,
                        "call-audit", "sendEmail", WorkflowCapability.SEND_EMAIL, 2,
                        new RiskDecision(RiskLevel.MODERATE, GovernanceIntervention.AUDIT, "Audit.")
                )),
                entity(AuditEvent.policyDecision(
                        workflowId, "EmailAgent", WorkflowType.EMAIL_COMMUNICATION,
                        "call-policy-block", "sendEmail", WorkflowCapability.SEND_EMAIL,
                        new GovernanceDecision(GovernanceDecision.DecisionType.BLOCK, "Blocked.", "EMAIL")
                )),
                entity(AuditEvent.policyDecision(
                        workflowId, "EmailAgent", WorkflowType.EMAIL_COMMUNICATION,
                        "call-risk-block", "sendEmail", WorkflowCapability.SEND_EMAIL,
                        new GovernanceDecision(GovernanceDecision.DecisionType.ALLOW, "Allowed by policy.", "EMAIL")
                )),
                entity(AuditEvent.riskAssessment(
                        workflowId, "EmailAgent", WorkflowType.EMAIL_COMMUNICATION,
                        "call-risk-block", "sendEmail", WorkflowCapability.SEND_EMAIL, 3,
                        new RiskDecision(RiskLevel.CRITICAL, GovernanceIntervention.BLOCK, "Critical.")
                )),
                entity(AuditEvent.policyDecision(
                        workflowId, "EmailAgent", WorkflowType.EMAIL_COMMUNICATION,
                        "call-approval", "sendEmail", WorkflowCapability.SEND_EMAIL,
                        new GovernanceDecision(GovernanceDecision.DecisionType.ALLOW, "Allowed by policy.", "EMAIL")
                )),
                entity(AuditEvent.riskAssessment(
                        workflowId, "EmailAgent", WorkflowType.EMAIL_COMMUNICATION,
                        "call-approval", "sendEmail", WorkflowCapability.SEND_EMAIL, 4,
                        new RiskDecision(
                                RiskLevel.HIGH,
                                GovernanceIntervention.REQUIRE_APPROVAL,
                                "Approval required."
                        )
                ))
        );
        when(repository.findAllByOrderByWorkflowIdAscTimestampAscIdAsc()).thenReturn(events);

        WorkflowSummaryResponse summary = new WorkflowTrajectoryService(repository)
                .getSummaries()
                .getFirst();

        assertEquals(2, summary.allowedActionCount());
        assertEquals(2, summary.blockedActionCount());
    }

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

    @Test
    void includesPersistedConversationIdInSummariesAndLeavesStandaloneWorkflowUnlinked() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        UUID conversationId = UUID.randomUUID();
        AuditEventEntity unlinkedFirstEvent = event(
                "workflow-in-conversation", "2026-10-01T10:00:00Z", AuditEventType.PROPOSED_ACTION,
                null, null, null, null, null, null
        );
        AuditEventEntity linkedEvent = event(
                "workflow-in-conversation", "2026-10-01T10:01:00Z", AuditEventType.EXECUTION_OUTCOME,
                null, null, null, null, null, null, conversationId
        );
        AuditEventEntity standaloneEvent = event(
                "standalone-workflow", "2026-10-01T11:00:00Z", AuditEventType.PROPOSED_ACTION,
                null, null, null, null, null, null
        );
        when(repository.findAllByOrderByWorkflowIdAscTimestampAscIdAsc())
                .thenReturn(List.of(unlinkedFirstEvent, linkedEvent, standaloneEvent));

        List<WorkflowSummaryResponse> summaries = new WorkflowTrajectoryService(repository).getSummaries();

        assertEquals(
                conversationId,
                summaries.stream()
                        .filter(summary -> summary.workflowId().equals("workflow-in-conversation"))
                        .findFirst()
                        .orElseThrow()
                        .conversationId()
        );
        assertNull(
                summaries.stream()
                        .filter(summary -> summary.workflowId().equals("standalone-workflow"))
                        .findFirst()
                        .orElseThrow()
                        .conversationId()
        );
    }

    @Test
    void approvalAndResumedExecutionRemainOneWorkflowTrajectory() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        String workflowId = "workflow-approved-email";
        UUID conversationId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-18T10:00:00Z");
        com.agentops.guardian.governance.intervention.GovernanceIntervention pending =
                new com.agentops.guardian.governance.intervention.GovernanceIntervention(
                        "intervention-email",
                        workflowId,
                        "send-call-email",
                        "EmailAgent",
                        WorkflowType.EMAIL_COMMUNICATION,
                        WorkflowCapability.SEND_EMAIL,
                        RiskLevel.HIGH,
                        GovernanceIntervention.REQUIRE_APPROVAL,
                        com.agentops.guardian.governance.intervention.GovernanceInterventionStatus.PENDING,
                        "External communication requires approval.",
                        createdAt,
                        null,
                        null,
                        null,
                        "EMAIL",
                        WorkflowState.STARTED,
                        null,
                        conversationId,
                        "sendEmail",
                        "arguments-digest"
                );
        var approved = pending.approve("reviewer", "Approved.", createdAt.plusSeconds(30));
        List<AuditEventEntity> events = List.of(
                entity(AuditEvent.interventionLifecycle(pending, AuditEventType.INTERVENTION_CREATED)),
                entity(AuditEvent.interventionLifecycle(approved, AuditEventType.INTERVENTION_APPROVED)),
                entity(AuditEvent.executionOutcome(
                        workflowId,
                        "EmailAgent",
                        WorkflowType.EMAIL_COMMUNICATION,
                        "send-call-email",
                        "sendEmail",
                        WorkflowCapability.SEND_EMAIL,
                        AuditEvent.ExecutionStatus.SUCCESS,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ).withConversationId(conversationId))
        );
        when(repository.findAllByOrderByWorkflowIdAscTimestampAscIdAsc()).thenReturn(events);
        when(repository.findByWorkflowIdOrderByTimestampAscIdAsc(workflowId)).thenReturn(events);
        WorkflowTrajectoryService service = new WorkflowTrajectoryService(repository);

        List<WorkflowSummaryResponse> summaries = service.getSummaries();
        WorkflowTrajectoryResponse trajectory = service.getTrajectory(workflowId);

        assertEquals(1, summaries.size());
        assertEquals(workflowId, summaries.getFirst().workflowId());
        assertEquals(3, summaries.getFirst().eventCount());
        assertEquals(workflowId, trajectory.workflowId());
        assertEquals(3, trajectory.eventCount());
        assertEquals(AuditEventType.INTERVENTION_CREATED, trajectory.events().getFirst().eventType());
        assertEquals(AuditEventType.INTERVENTION_APPROVED, trajectory.events().get(1).eventType());
        assertEquals(AuditEventType.EXECUTION_OUTCOME, trajectory.events().getLast().eventType());
        assertEquals(AuditEvent.ExecutionStatus.SUCCESS, trajectory.events().getLast().executionStatus());
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
        return event(
                workflowId, timestamp, eventType, nodeBefore, nodeAfter, stateBefore, stateAfter,
                classificationBefore, classificationAfter, null
        );
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
            DataClassification classificationAfter,
            UUID conversationId
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
        return AuditEventEntity.from(
                conversationId == null ? event : event.withConversationId(conversationId)
        );
    }

    private AuditEventEntity entity(AuditEvent event) {
        return AuditEventEntity.from(event);
    }
}