package com.agentops.guardian.governance.evaluation;

import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.audit.AuditEventType;
import com.agentops.guardian.governance.intervention.GovernanceInterventionStatus;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.risk.GovernanceIntervention;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class EvaluationOutcomeDeriver {
    private EvaluationOutcomeDeriver() {
    }

    public static EvaluationResult derive(EvaluationObservation observation) {
        Objects.requireNonNull(observation, "Evaluation observation is required.");
        List<EvaluationAuditEvidence> events = observation.auditEvidence().stream()
                .filter(event -> observation.toolCallId().equals(event.toolCallId()))
                .sorted(Comparator.comparing(EvaluationAuditEvidence::timestamp,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EvaluationAuditEvidence::auditEventId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        EvaluationOutcome outcome = deriveOutcome(observation, events);
        EvaluationTiming timing = deriveTiming(observation, events);
        EvaluationEvidenceReferences references = evidenceReferences(observation, events, timing);
        return new EvaluationResult(
                observation.scenario().scenarioId(),
                observation.governanceMode(),
                observation.workflowId(),
                observation.toolCallId(),
                outcome,
                timing,
                duration(timing.toolExecutionStartedAt(), timing.toolExecutionCompletedAt()),
                duration(timing.guardianEvaluationStartedAt(), timing.guardianEvaluationCompletedAt()),
                duration(timing.interventionCreatedAt(), timing.interventionResolvedAt()),
                references
        );
    }

    private static EvaluationOutcome deriveOutcome(
            EvaluationObservation observation,
            List<EvaluationAuditEvidence> events
    ) {
        GovernanceInterventionStatus status = latest(events, event -> event.interventionStatus() != null,
                EvaluationAuditEvidence::interventionStatus);
        AuditEvent.ExecutionStatus execution = latest(events, event -> event.executionStatus() != null,
                EvaluationAuditEvidence::executionStatus);
        GovernanceDecision.DecisionType policy = latest(events, event -> event.policyDecision() != null,
                EvaluationAuditEvidence::policyDecision);
        GovernanceIntervention intervention = latest(events, event -> event.intervention() != null,
                EvaluationAuditEvidence::intervention);

        if (execution == AuditEvent.ExecutionStatus.SUCCESS) {
            return status == GovernanceInterventionStatus.APPROVED
                    ? EvaluationOutcome.APPROVED_AND_EXECUTED
                    : EvaluationOutcome.ACTION_EXECUTED;
        }
        if (execution == AuditEvent.ExecutionStatus.FAILED) return EvaluationOutcome.FAILED;
        if (execution == AuditEvent.ExecutionStatus.BLOCKED
                && (policy == GovernanceDecision.DecisionType.BLOCK
                || intervention == GovernanceIntervention.BLOCK)) {
            return EvaluationOutcome.ACTION_BLOCKED;
        }
        if (status == GovernanceInterventionStatus.REJECTED
                || status == GovernanceInterventionStatus.EXPIRED) {
            return EvaluationOutcome.REJECTED_AND_PREVENTED;
        }
        if (status == GovernanceInterventionStatus.PENDING
                || (intervention == GovernanceIntervention.REQUIRE_APPROVAL && status == null)) {
            return EvaluationOutcome.APPROVAL_REQUIRED;
        }
        return observation.observedOutcome() == null
                ? EvaluationOutcome.UNKNOWN
                : observation.observedOutcome();
    }

    private static EvaluationTiming deriveTiming(
            EvaluationObservation observation,
            List<EvaluationAuditEvidence> events
    ) {
        EvaluationTiming recorded = observation.recordedTiming();
        Instant actionStarted = firstTimestamp(events,
                event -> event.eventType() == AuditEventType.PROPOSED_ACTION);
        Instant evaluationStarted = actionStarted;
        Instant evaluationCompleted = firstTimestamp(events,
                event -> event.eventType() == AuditEventType.POLICY_DECISION);
        EvaluationAuditEvidence interventionCreation = events.stream()
                .filter(event -> event.eventType() == AuditEventType.INTERVENTION_CREATED
                        && event.interventionId() != null
                        && event.timestamp() != null)
                .findFirst()
                .orElse(null);
        Instant interventionCreated = interventionCreation == null
                ? null
                : interventionCreation.timestamp();
        Instant interventionResolved = interventionCreation == null
                ? null
                : firstTimestamp(events,
                        event -> interventionCreation.interventionId().equals(event.interventionId())
                                && (event.eventType() == AuditEventType.INTERVENTION_APPROVED
                                || event.eventType() == AuditEventType.INTERVENTION_REJECTED
                                || event.eventType() == AuditEventType.INTERVENTION_EXPIRED));
        Instant executionCompleted = lastTimestamp(events,
                event -> event.eventType() == AuditEventType.EXECUTION_OUTCOME
                        && event.executionStatus() != null);

        return new EvaluationTiming(
                prefer(actionStarted, recorded.actionStartedAt()),
                prefer(evaluationStarted, recorded.guardianEvaluationStartedAt()),
                prefer(evaluationCompleted, recorded.guardianEvaluationCompletedAt()),
                prefer(interventionCreated, recorded.interventionCreatedAt()),
                prefer(interventionResolved, recorded.interventionResolvedAt()),
                recorded.toolExecutionStartedAt(),
                prefer(executionCompleted, recorded.toolExecutionCompletedAt())
        );
    }

    private static EvaluationEvidenceReferences evidenceReferences(
            EvaluationObservation observation,
            List<EvaluationAuditEvidence> events,
            EvaluationTiming timing
    ) {
        List<Instant> timestamps = java.util.stream.Stream.of(
                        timing.actionStartedAt(),
                        timing.guardianEvaluationStartedAt(),
                        timing.guardianEvaluationCompletedAt(),
                        timing.interventionCreatedAt(),
                        timing.interventionResolvedAt(),
                        timing.toolExecutionStartedAt(),
                        timing.toolExecutionCompletedAt()
                )
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return new EvaluationEvidenceReferences(
                observation.workflowId(),
                observation.toolCallId(),
                events.stream().map(EvaluationAuditEvidence::auditEventId)
                        .filter(Objects::nonNull).distinct().toList(),
                events.stream().map(EvaluationAuditEvidence::interventionId)
                        .filter(Objects::nonNull).distinct().toList(),
                events.stream().map(EvaluationAuditEvidence::policyId)
                        .filter(Objects::nonNull).distinct().toList(),
                timestamps
        );
    }

    private static Instant firstTimestamp(
            List<EvaluationAuditEvidence> events,
            Predicate<EvaluationAuditEvidence> predicate
    ) {
        return events.stream()
                .filter(predicate)
                .map(EvaluationAuditEvidence::timestamp)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static Instant lastTimestamp(
            List<EvaluationAuditEvidence> events,
            Predicate<EvaluationAuditEvidence> predicate
    ) {
        for (int index = events.size() - 1; index >= 0; index--) {
            EvaluationAuditEvidence event = events.get(index);
            if (predicate.test(event) && event.timestamp() != null) return event.timestamp();
        }
        return null;
    }

    private static <T> T latest(
            List<EvaluationAuditEvidence> events,
            Predicate<EvaluationAuditEvidence> predicate,
            java.util.function.Function<EvaluationAuditEvidence, T> getter
    ) {
        for (int index = events.size() - 1; index >= 0; index--) {
            EvaluationAuditEvidence event = events.get(index);
            if (predicate.test(event)) return getter.apply(event);
        }
        return null;
    }

    private static Instant prefer(Instant persisted, Instant recorded) {
        return persisted != null ? persisted : recorded;
    }

    private static Long duration(Instant start, Instant end) {
        if (start == null || end == null || end.isBefore(start)) return null;
        try {
            return Duration.between(start, end).toMillis();
        } catch (ArithmeticException exception) {
            return null;
        }
    }
}
