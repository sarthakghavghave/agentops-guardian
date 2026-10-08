package com.agentops.guardian.governance.evaluation;

import java.util.Objects;

public final class EvaluationComparisonDeriver {
    private EvaluationComparisonDeriver() {
    }

    public static EvaluationComparison compare(EvaluationResult governanceOn, EvaluationResult governanceOff) {
        if (governanceOn == null || governanceOff == null) {
            String scenarioId = governanceOn != null
                    ? governanceOn.scenarioId()
                    : governanceOff != null ? governanceOff.scenarioId() : "unavailable";
            return EvaluationComparison.unavailable(
                    scenarioId,
                    "A manually observed Governance ON and Governance OFF run pair is required."
            );
        }
        if (governanceOn.governanceMode() != GovernanceMode.GOVERNANCE_ON
                || governanceOff.governanceMode() != GovernanceMode.GOVERNANCE_OFF
                || !governanceOn.scenarioId().equals(governanceOff.scenarioId())) {
            return EvaluationComparison.unavailable(
                    governanceOn.scenarioId(),
                    "Observations must identify the same scenario and explicitly label opposite governance modes."
            );
        }
        if (governanceOn.outcome() == EvaluationOutcome.UNKNOWN
                || governanceOff.outcome() == EvaluationOutcome.UNKNOWN) {
            return EvaluationComparison.unavailable(
                    governanceOn.scenarioId(),
                    "Both runs need an observed or evidence-derived action outcome."
            );
        }

        Long offDuration = governanceOff.totalExecutionDurationMs();
        Long onDuration = governanceOn.totalExecutionDurationMs();
        Long overhead = difference(onDuration, offDuration);
        Double overheadPercentage = offDuration != null && offDuration > 0 && overhead != null
                ? overhead * 100.0 / offDuration
                : null;
        boolean changed = !Objects.equals(governanceOn.outcome(), governanceOff.outcome());
        String explanation = changed
                ? "The observed outcomes differ between the explicitly paired runs."
                : "The observed outcomes are the same in both explicitly paired runs.";
        if (offDuration == null || onDuration == null) {
            explanation += " Duration comparison is unavailable because one or both execution durations are missing.";
        } else if (offDuration == 0) {
            explanation += " Overhead percentage is unavailable because the Governance OFF duration is zero.";
        }
        if (governanceOn.evidence().auditEventIds().isEmpty()
                || governanceOff.evidence().auditEventIds().isEmpty()) {
            explanation += " At least one run has no persisted audit-event references; its result relies on the explicit manual observation.";
        }
        return new EvaluationComparison(
                governanceOn.scenarioId(),
                true,
                governanceOff.outcome(),
                governanceOn.outcome(),
                changed,
                offDuration,
                onDuration,
                overhead,
                overheadPercentage,
                explanation
        );
    }

    private static Long difference(Long onDuration, Long offDuration) {
        if (onDuration == null || offDuration == null) return null;
        try {
            return Math.subtractExact(onDuration, offDuration);
        } catch (ArithmeticException exception) {
            return null;
        }
    }
}
