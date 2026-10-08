package com.agentops.guardian.governance.evaluation;

import java.util.Objects;

public record EvaluationResult(
        String scenarioId,
        GovernanceMode governanceMode,
        String workflowId,
        String toolCallId,
        EvaluationOutcome outcome,
        EvaluationTiming timing,
        Long totalExecutionDurationMs,
        Long guardianEvaluationDurationMs,
        Long humanInterventionDurationMs,
        EvaluationEvidenceReferences evidence
) {
    public EvaluationResult {
        Objects.requireNonNull(governanceMode, "Governance mode is required.");
        Objects.requireNonNull(workflowId, "Workflow id is required.");
        Objects.requireNonNull(toolCallId, "Tool call id is required.");
        Objects.requireNonNull(outcome, "Evaluation outcome is required.");
        Objects.requireNonNull(timing, "Evaluation timing is required.");
        Objects.requireNonNull(evidence, "Evidence references are required.");
    }
}
