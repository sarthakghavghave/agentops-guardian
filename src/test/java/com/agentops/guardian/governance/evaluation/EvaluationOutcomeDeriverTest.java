package com.agentops.guardian.governance.evaluation;

import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.audit.AuditEventType;
import com.agentops.guardian.governance.intervention.GovernanceInterventionStatus;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.risk.GovernanceIntervention;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationOutcomeDeriverTest {

    private static final Instant START = Instant.parse("2026-10-08T03:00:00Z");

    @Test
    void comparesExplicitOnAndOffRunsAndDerivesDurationAndOverhead() {
        EvaluationResult off = derive(GovernanceMode.GOVERNANCE_OFF, EvaluationOutcome.ACTION_EXECUTED,
                timing(START, null, null, null, null, START, START.plusMillis(1_000)), List.of());
        EvaluationResult on = derive(GovernanceMode.GOVERNANCE_ON, null,
                timing(START, START.plusMillis(500), START.plusMillis(1_500),
                        null, null, START.plusMillis(500), null), List.of(
                        event(1L, 0, AuditEventType.PROPOSED_ACTION, null, null, null, null, null),
                        event(2L, 1, AuditEventType.POLICY_DECISION,
                                GovernanceDecision.DecisionType.ALLOW, null, null, null, null),
                        event(3L, 2, AuditEventType.EXECUTION_OUTCOME, null,
                                AuditEvent.ExecutionStatus.SUCCESS, null, null, null)
                ));

        EvaluationComparison comparison = EvaluationComparisonDeriver.compare(on, off);

        assertTrue(comparison.available());
        assertEquals(EvaluationOutcome.ACTION_EXECUTED, comparison.governanceOffOutcome());
        assertEquals(EvaluationOutcome.ACTION_EXECUTED, comparison.governanceOnOutcome());
        assertEquals(Boolean.FALSE, comparison.outcomeChanged());
        assertEquals(1_000L, comparison.governanceOffDurationMs());
        assertEquals(1_500L, comparison.governanceOnDurationMs());
        assertEquals(500L, comparison.governanceOverheadMs());
        assertEquals(50.0, comparison.governanceOverheadPercentage());
    }

    @Test
    void computesPositiveGovernanceOverhead() {
        EvaluationResult off = derive(GovernanceMode.GOVERNANCE_OFF, EvaluationOutcome.ACTION_EXECUTED,
                timing(START, null, null, START, null, START, START.plusMillis(800)), List.of());
        EvaluationResult on = derive(GovernanceMode.GOVERNANCE_ON, EvaluationOutcome.ACTION_EXECUTED,
                timing(START, null, null, START, null, START, START.plusMillis(1_000)), List.of());

        EvaluationComparison comparison = EvaluationComparisonDeriver.compare(on, off);

        assertEquals(200L, comparison.governanceOverheadMs());
        assertEquals(25.0, comparison.governanceOverheadPercentage());
    }

    @Test
    void overheadPercentageNeedsNonzeroOffDuration() {
        EvaluationResult off = derive(GovernanceMode.GOVERNANCE_OFF, EvaluationOutcome.ACTION_EXECUTED,
                timing(START, null, null, START, null, START, START), List.of());
        EvaluationResult on = derive(GovernanceMode.GOVERNANCE_ON, EvaluationOutcome.ACTION_EXECUTED,
                timing(START, null, null, START, null, START, START.plusMillis(10)), List.of());

        EvaluationComparison comparison = EvaluationComparisonDeriver.compare(on, off);

        assertTrue(comparison.available());
        assertEquals(0L, comparison.governanceOffDurationMs());
        assertEquals(10L, comparison.governanceOverheadMs());
        assertNull(comparison.governanceOverheadPercentage());
        assertTrue(comparison.explanation().contains("zero"));
    }

    @Test
    void missingTimestampsLeaveDurationsUnavailable() {
        EvaluationResult result = derive(GovernanceMode.GOVERNANCE_ON, EvaluationOutcome.ACTION_EXECUTED,
                EvaluationTiming.unavailable(), List.of());

        assertNull(result.totalExecutionDurationMs());
        assertNull(result.guardianEvaluationDurationMs());
        assertNull(result.humanInterventionDurationMs());
    }

    @Test
    void detectsOutcomeDifferenceBetweenRuns() {
        EvaluationResult off = derive(GovernanceMode.GOVERNANCE_OFF, EvaluationOutcome.ACTION_EXECUTED,
                EvaluationTiming.unavailable(), List.of());
        EvaluationResult on = derive(GovernanceMode.GOVERNANCE_ON, EvaluationOutcome.ACTION_BLOCKED,
                EvaluationTiming.unavailable(), List.of());

        EvaluationComparison comparison = EvaluationComparisonDeriver.compare(on, off);

        assertTrue(comparison.available());
        assertEquals(EvaluationOutcome.ACTION_EXECUTED, comparison.governanceOffOutcome());
        assertEquals(EvaluationOutcome.ACTION_BLOCKED, comparison.governanceOnOutcome());
        assertEquals(Boolean.TRUE, comparison.outcomeChanged());
    }

    @Test
    void sameOutcomeIsExplicitlyRepresentedAsUnchanged() {
        EvaluationResult off = derive(GovernanceMode.GOVERNANCE_OFF, EvaluationOutcome.ACTION_EXECUTED,
                EvaluationTiming.unavailable(), List.of());
        EvaluationResult on = derive(GovernanceMode.GOVERNANCE_ON, EvaluationOutcome.ACTION_EXECUTED,
                EvaluationTiming.unavailable(), List.of());

        EvaluationComparison comparison = EvaluationComparisonDeriver.compare(on, off);

        assertTrue(comparison.available());
        assertEquals(Boolean.FALSE, comparison.outcomeChanged());
    }

    @Test
    void interventionTimingComesFromPersistedLifecycleEvents() {
        EvaluationResult result = derive(GovernanceMode.GOVERNANCE_ON, null,
                timing(null, null, null, null, null, START.plusSeconds(150), null), List.of(
                        event(1L, 0, AuditEventType.PROPOSED_ACTION, null, null, null, null, null),
                        event(2L, 10, AuditEventType.POLICY_DECISION,
                                GovernanceDecision.DecisionType.ALLOW, null, null, null, null),
                        event(3L, 20, AuditEventType.INTERVENTION_CREATED, null, null,
                                GovernanceIntervention.REQUIRE_APPROVAL, "int-1", GovernanceInterventionStatus.PENDING),
                        event(4L, 140, AuditEventType.INTERVENTION_APPROVED, null, null,
                                GovernanceIntervention.REQUIRE_APPROVAL, "int-1", GovernanceInterventionStatus.APPROVED),
                        event(5L, 160, AuditEventType.EXECUTION_OUTCOME, null,
                                AuditEvent.ExecutionStatus.SUCCESS, null, null, null)
                ));

        assertEquals(EvaluationOutcome.APPROVED_AND_EXECUTED, result.outcome());
        assertEquals(120_000L, result.humanInterventionDurationMs());
        assertEquals(10_000L, result.totalExecutionDurationMs());
        assertEquals(10_000L, result.guardianEvaluationDurationMs());
        assertTrue(result.evidence().interventionIds().contains("int-1"));
    }

    @Test
    void successWithoutGovernanceEvidenceMeansExecutedNotApprovedOrAllowed() {
        EvaluationResult result = derive(GovernanceMode.GOVERNANCE_ON, null,
                EvaluationTiming.unavailable(), List.of(
                        event(1L, 0, AuditEventType.PROPOSED_ACTION, null, null, null, null, null),
                        event(2L, 100, AuditEventType.EXECUTION_OUTCOME, null,
                                AuditEvent.ExecutionStatus.SUCCESS, null, null, null)
                ));

        assertEquals(EvaluationOutcome.ACTION_EXECUTED, result.outcome());
        assertNull(result.timing().guardianEvaluationCompletedAt());
    }

    @Test
    void missingGovernanceEvidenceDoesNotCreateAnOffBaseline() {
        EvaluationResult observationWithoutGovernanceEvidence = derive(
                GovernanceMode.GOVERNANCE_ON, null, EvaluationTiming.unavailable(), List.of()
        );

        EvaluationComparison comparison = EvaluationComparisonDeriver.compare(
                observationWithoutGovernanceEvidence, null
        );

        assertEquals(GovernanceMode.GOVERNANCE_ON, observationWithoutGovernanceEvidence.governanceMode());
        assertEquals(EvaluationOutcome.UNKNOWN, observationWithoutGovernanceEvidence.outcome());
        assertFalse(comparison.available());
        assertNull(comparison.governanceOffOutcome());
    }

    @Test
    void evidenceReferencesStayAttachedToDerivedResult() {
        EvaluationResult result = derive(GovernanceMode.GOVERNANCE_ON, null,
                EvaluationTiming.unavailable(), List.of(
                        event(1L, 0, AuditEventType.PROPOSED_ACTION, null, null, null, null, null),
                        event(2L, 10, AuditEventType.POLICY_DECISION,
                                GovernanceDecision.DecisionType.BLOCK, null, null, null, null),
                        event(3L, 20, AuditEventType.EXECUTION_OUTCOME, null,
                                AuditEvent.ExecutionStatus.BLOCKED, null, null, null)
                ));

        assertEquals("workflow-1", result.evidence().workflowId());
        assertEquals("tool-call-1", result.evidence().toolCallId());
        assertEquals(List.of(1L, 2L, 3L), result.evidence().auditEventIds());
        assertEquals(List.of("policy-1"), result.evidence().policyIds());
        assertEquals(EvaluationOutcome.ACTION_BLOCKED, result.outcome());
    }

    private EvaluationResult derive(
            GovernanceMode mode,
            EvaluationOutcome observedOutcome,
            EvaluationTiming timing,
            List<EvaluationAuditEvidence> events
    ) {
        return EvaluationOutcomeDeriver.derive(new EvaluationObservation(
                scenario(),
                mode,
                "workflow-1",
                "tool-call-1",
                observedOutcome,
                timing,
                events
        ));
    }

    private EvaluationScenario scenario() {
        return new EvaluationScenario(
                "send-email-demo",
                "Send customer report",
                "Demonstrate a customer report email action.",
                WorkflowType.CUSTOMER_REPORTING,
                "sendEmail",
                WorkflowCapability.SEND_EMAIL,
                EvaluationMisuseCategory.HUMAN_APPROVAL_REQUIRED,
                true
        );
    }

    private EvaluationTiming timing(Instant start, Instant evaluationStart, Instant complete) {
        return timing(start, evaluationStart, complete, null, null, start, complete);
    }

    private EvaluationTiming timing(
            Instant actionStart,
            Instant evaluationStart,
            Instant evaluationComplete,
            Instant interventionCreated,
            Instant interventionResolved,
            Instant executionStart,
            Instant executionComplete
    ) {
        return new EvaluationTiming(
                actionStart,
                evaluationStart,
                evaluationComplete,
                interventionCreated,
                interventionResolved,
                executionStart,
                executionComplete
        );
    }

    private EvaluationAuditEvidence event(
            Long id,
            Integer secondsFromStart,
            AuditEventType eventType,
            GovernanceDecision.DecisionType policy,
            AuditEvent.ExecutionStatus execution,
            GovernanceIntervention intervention,
            String interventionId,
            GovernanceInterventionStatus status
    ) {
        Instant timestamp = secondsFromStart == null ? null : START.plusSeconds(secondsFromStart);
        return new EvaluationAuditEvidence(
                id,
                "workflow-1",
                WorkflowType.CUSTOMER_REPORTING,
                "tool-call-1",
                "sendEmail",
                WorkflowCapability.SEND_EMAIL,
                timestamp,
                eventType,
                policy,
                policy == null ? null : "policy-1",
                execution,
                intervention,
                interventionId,
                status
        );
    }
}
