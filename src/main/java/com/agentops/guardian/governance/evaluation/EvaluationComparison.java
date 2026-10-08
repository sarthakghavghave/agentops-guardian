package com.agentops.guardian.governance.evaluation;

public record EvaluationComparison(
        String scenarioId,
        boolean available,
        EvaluationOutcome governanceOffOutcome,
        EvaluationOutcome governanceOnOutcome,
        Boolean outcomeChanged,
        Long governanceOffDurationMs,
        Long governanceOnDurationMs,
        Long governanceOverheadMs,
        Double governanceOverheadPercentage,
        String explanation
) {
    public static EvaluationComparison unavailable(String scenarioId, String explanation) {
        return new EvaluationComparison(
                scenarioId, false, null, null, null, null, null, null, null, explanation
        );
    }
}
