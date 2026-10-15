package com.agentops.guardian.governance.intervention;

import com.agentops.guardian.governance.audit.AuditEventEntity;
import com.agentops.guardian.governance.audit.AuditEventRepository;
import com.agentops.guardian.governance.audit.AuditEventType;
import com.agentops.guardian.governance.audit.AuditService;
import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.execution.ApprovedActionGrant;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.risk.RiskDecision;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowDefinition;
import com.agentops.guardian.governance.workflow.WorkflowGraph;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterventionServiceTest {

    private GovernanceInterventionRepository interventionRepository;
    private AuditEventRepository auditEventRepository;
    private AuditService auditService;
    private InterventionService service;

    @BeforeEach
    void setUp() {
        interventionRepository = mock(GovernanceInterventionRepository.class);
        auditEventRepository = mock(AuditEventRepository.class);
        auditService = mock(AuditService.class);
        service = new InterventionService(interventionRepository, auditEventRepository, auditService);
        when(interventionRepository.save(any(GovernanceInterventionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsRetrievesAndListsCallSpecificPendingIntervention() {
        GovernanceIntervention created = service.createPending(
                reportWorkflow(), "call-specific-1", WorkflowCapability.SEND_EMAIL, highRiskDecision()
        );
        GovernanceInterventionEntity entity = GovernanceInterventionEntity.from(created);
        when(interventionRepository.findByInterventionId(created.interventionId())).thenReturn(java.util.Optional.of(entity));
        when(interventionRepository.findAllByOrderByCreatedAtDescInterventionIdAsc()).thenReturn(List.of(entity));

        assertEquals(GovernanceInterventionStatus.PENDING, created.status());
        assertEquals("call-specific-1", created.toolCallId());
        assertEquals(created, service.get(created.interventionId()));
        assertEquals(List.of(created), service.list());
        verify(auditService).record(org.mockito.ArgumentMatchers.argThat(event ->
                event.eventType() == AuditEventType.INTERVENTION_CREATED
                        && created.interventionId().equals(event.interventionId())
                        && "call-specific-1".equals(event.toolCallId())
        ));
    }

    @Test
    void persistsOnlyAHashOfTheProposedActionForSafeRevalidation() {
        String digest = ApprovedActionGrant.argumentsDigest("{\"recipient\":\"customer@example.test\"}");
        GovernanceIntervention created = service.createPending(
                reportWorkflow(),
                "call-specific-2",
                "sendEmail",
                digest,
                WorkflowCapability.SEND_EMAIL,
                highRiskDecision()
        );

        GovernanceInterventionEntity entity = GovernanceInterventionEntity.from(created);
        GovernanceIntervention restored = entity.toDomain();
        assertEquals("sendEmail", restored.toolName());
        assertEquals(digest, restored.argumentsDigest());
        assertFalse(restored.argumentsDigest().contains("customer@example.test"));
        verify(auditService).record(org.mockito.ArgumentMatchers.argThat(event ->
                event.eventType() == AuditEventType.INTERVENTION_CREATED
                        && "sendEmail".equals(event.toolName())
                        && "call-specific-2".equals(event.toolCallId())
        ));
    }

    @Test
    void approvesPendingInterventionAndAuditsResolution() {
        GovernanceIntervention pending = pending();
        GovernanceInterventionEntity entity = GovernanceInterventionEntity.from(pending);
        when(interventionRepository.findByInterventionId(pending.interventionId())).thenReturn(java.util.Optional.of(entity));
        AuditEventEntity currentEvent = currentTrajectoryEvent(
                "REPORT", WorkflowState.REPORT_GENERATED, DataClassification.ANALYTICAL
        );
        when(auditEventRepository.findByWorkflowIdOrderByTimestampAscIdAsc(pending.workflowId()))
                .thenReturn(List.of(currentEvent));

        GovernanceIntervention approved = service.approve(pending.interventionId(), "reviewer", "Approved once.");

        assertEquals(GovernanceInterventionStatus.APPROVED, approved.status());
        assertEquals("reviewer", approved.resolvedBy());
        assertEquals("Approved once.", approved.resolutionReason());
        verify(interventionRepository).saveAndFlush(entity);
        verify(auditService).record(org.mockito.ArgumentMatchers.argThat(event ->
                event.eventType() == AuditEventType.INTERVENTION_APPROVED
                        && pending.interventionId().equals(event.interventionId())
                        && event.interventionStatus() == GovernanceInterventionStatus.APPROVED
        ));
    }

    @Test
    void approvesPendingEmailWhenAuditContainsItsApprovalTimeSnapshot() {
        WorkflowContext context = emailWorkflow();
        GovernanceIntervention pending = service.createPending(
                context,
                "email-call",
                "sendEmail",
                ApprovedActionGrant.argumentsDigest("{\"recipient\":\"customer@example.test\"}"),
                WorkflowCapability.SEND_EMAIL,
                highRiskDecision()
        );
        GovernanceInterventionEntity entity = GovernanceInterventionEntity.from(pending);
        when(interventionRepository.findByInterventionId(pending.interventionId()))
                .thenReturn(java.util.Optional.of(entity));
        AuditEventEntity unchanged = currentTrajectoryEvent("START", WorkflowState.STARTED, null);
        when(auditEventRepository.findByWorkflowIdOrderByTimestampAscIdAsc(pending.workflowId()))
                .thenReturn(List.of(unchanged));

        GovernanceIntervention approved = service.approve(
                pending.interventionId(),
                "reviewer",
                "Approved email."
        );

        assertEquals(GovernanceInterventionStatus.APPROVED, approved.status());
        assertEquals("sendEmail", approved.toolName());
        assertEquals(WorkflowState.STARTED, approved.workflowState());
        assertEquals("START", approved.workflowNodeId());
        verify(interventionRepository).saveAndFlush(entity);
    }

    @Test
    void approvesLegacyPendingEmailWhenAuditHasNoTrajectorySnapshot() {
        GovernanceIntervention pending = service.createPending(
                emailWorkflow(),
                "email-call",
                "sendEmail",
                ApprovedActionGrant.argumentsDigest("{}"),
                WorkflowCapability.SEND_EMAIL,
                highRiskDecision()
        );
        GovernanceInterventionEntity entity = GovernanceInterventionEntity.from(pending);
        when(interventionRepository.findByInterventionId(pending.interventionId()))
                .thenReturn(java.util.Optional.of(entity));
        AuditEventEntity sparseAuditEvent = currentTrajectoryEvent(null, null, null);
        when(auditEventRepository.findByWorkflowIdOrderByTimestampAscIdAsc(pending.workflowId()))
                .thenReturn(List.of(sparseAuditEvent));

        GovernanceIntervention approved = service.approve(
                pending.interventionId(),
                "reviewer",
                "Approve pending email."
        );

        assertEquals(GovernanceInterventionStatus.APPROVED, approved.status());
        verify(interventionRepository).saveAndFlush(entity);
    }

    @Test
    void rejectsPendingInterventionAndAuditsResolution() {
        GovernanceIntervention pending = pending();
        GovernanceInterventionEntity entity = GovernanceInterventionEntity.from(pending);
        when(interventionRepository.findByInterventionId(pending.interventionId())).thenReturn(java.util.Optional.of(entity));

        GovernanceIntervention rejected = service.reject(pending.interventionId(), "reviewer", "Not approved.");

        assertEquals(GovernanceInterventionStatus.REJECTED, rejected.status());
        verify(auditService).record(org.mockito.ArgumentMatchers.argThat(event ->
                event.eventType() == AuditEventType.INTERVENTION_REJECTED
                        && event.interventionStatus() == GovernanceInterventionStatus.REJECTED
        ));
    }

    @Test
    void refusesASecondResolution() {
        GovernanceIntervention approved = pending().approve("reviewer", "Approved.", Instant.now());
        when(interventionRepository.findByInterventionId(approved.interventionId()))
                .thenReturn(java.util.Optional.of(GovernanceInterventionEntity.from(approved)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.reject(approved.interventionId(), "another-reviewer", "Changed decision.")
        );

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void refusesApprovalOfAnAlreadyResolvedIntervention() {
        GovernanceIntervention rejected = pending().reject("reviewer", "Rejected.", Instant.now());
        when(interventionRepository.findByInterventionId(rejected.interventionId()))
                .thenReturn(java.util.Optional.of(GovernanceInterventionEntity.from(rejected)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.approve(rejected.interventionId(), "reviewer", "Approve after rejection.")
        );

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void returnsNotFoundForUnknownIntervention() {
        when(interventionRepository.findByInterventionId("missing"))
                .thenReturn(java.util.Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.get("missing")
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void expiresStaleApprovalAndDoesNotAuthorizeExecution() {
        GovernanceIntervention pending = pending();
        GovernanceInterventionEntity entity = GovernanceInterventionEntity.from(pending);
        when(interventionRepository.findByInterventionId(pending.interventionId()))
                .thenReturn(java.util.Optional.of(entity));
        AuditEventEntity staleEvent = currentTrajectoryEvent(
                "DELIVERY", WorkflowState.DELIVERY_REQUESTED, DataClassification.ANALYTICAL
        );
        when(auditEventRepository.findByWorkflowIdOrderByTimestampAscIdAsc(pending.workflowId()))
                .thenReturn(List.of(staleEvent));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.approve(pending.interventionId(), "reviewer", "Approved stale call.")
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(GovernanceInterventionStatus.EXPIRED, entity.toDomain().status());
        verify(auditService).record(org.mockito.ArgumentMatchers.argThat(event ->
                event.eventType() == AuditEventType.INTERVENTION_EXPIRED
                        && event.interventionStatus() == GovernanceInterventionStatus.EXPIRED
        ));
    }

    @Test
    void expiresPendingEmailWhenItsClassificationChangesToRawCustomerData() {
        GovernanceIntervention pending = service.createPending(
                emailWorkflow(),
                "email-call",
                "sendEmail",
                ApprovedActionGrant.argumentsDigest("{}"),
                WorkflowCapability.SEND_EMAIL,
                highRiskDecision()
        );
        GovernanceInterventionEntity entity = GovernanceInterventionEntity.from(pending);
        when(interventionRepository.findByInterventionId(pending.interventionId()))
                .thenReturn(java.util.Optional.of(entity));
        AuditEventEntity changedClassification =
                currentTrajectoryEvent("START", WorkflowState.STARTED, DataClassification.RAW_CUSTOMER_DATA);
        when(auditEventRepository.findByWorkflowIdOrderByTimestampAscIdAsc(pending.workflowId()))
                .thenReturn(List.of(changedClassification));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.approve(pending.interventionId(), "reviewer", "Approve stale email.")
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(GovernanceInterventionStatus.EXPIRED, entity.toDomain().status());
    }

    @Test
    void entityContainsNoRuntimePayloadFields() {
        Set<String> fields = java.util.Arrays.stream(GovernanceInterventionEntity.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertFalse(fields.contains("arguments"));
        assertFalse(fields.contains("prompt"));
        assertFalse(fields.contains("customerData"));
        assertFalse(fields.contains("emailBody"));
        assertFalse(fields.contains("toolResponse"));
    }

    private GovernanceIntervention pending() {
        return service.createPending(
                reportWorkflow(), "call-specific-1", WorkflowCapability.SEND_EMAIL, highRiskDecision()
        );
    }

    private WorkflowContext reportWorkflow() {
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.CUSTOMER_REPORTING,
                Set.of(WorkflowCapability.READ_CUSTOMER_DATA, WorkflowCapability.GENERATE_REPORT,
                        WorkflowCapability.SEND_EMAIL),
                WorkflowGraph.customerReportingGraph()
        );
        WorkflowContext context = new WorkflowContext(
                "CustomerReportAgent", WorkflowType.CUSTOMER_REPORTING, "Report", definition
        );
        context.advanceAfterCapability(WorkflowCapability.READ_CUSTOMER_DATA);
        context.advanceAfterCapability(WorkflowCapability.GENERATE_REPORT);
        context.recordTransformation(new com.agentops.guardian.governance.model.DataTransformation(
                DataClassification.RAW_CUSTOMER_DATA,
                DataClassification.ANALYTICAL,
                "ANALYTICAL",
                Instant.now()
        ));
        return context;
    }

    private WorkflowContext emailWorkflow() {
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.EMAIL_COMMUNICATION,
                Set.of(WorkflowCapability.SEND_EMAIL),
                WorkflowGraph.emailCommunicationGraph()
        );
        return new WorkflowContext(
                "EmailAgent",
                WorkflowType.EMAIL_COMMUNICATION,
                "Send approved email",
                definition
        );
    }

    private RiskDecision highRiskDecision() {
        return new RiskDecision(
                RiskLevel.HIGH,
                com.agentops.guardian.governance.risk.GovernanceIntervention.REQUIRE_APPROVAL,
                "External email requires approval after report generation."
        );
    }

    private AuditEventEntity currentTrajectoryEvent(
            String nodeId,
            WorkflowState state,
            DataClassification classification
    ) {
        AuditEventEntity event = mock(AuditEventEntity.class);
        when(event.getNodeAfterId()).thenReturn(nodeId);
        when(event.getStateAfter()).thenReturn(state);
        when(event.getClassificationAfter()).thenReturn(classification);
        return event;
    }
}