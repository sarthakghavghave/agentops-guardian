package com.agentops.guardian.governance.evaluation;

import java.util.List;
import java.util.Objects;

public record EvaluationObservation(
        EvaluationScenario scenario,
        GovernanceMode governanceMode,
        String workflowId,
        String toolCallId,
        EvaluationOutcome observedOutcome,
        EvaluationTiming recordedTiming,
        List<EvaluationAuditEvidence> auditEvidence
) {
    public EvaluationObservation {
        Objects.requireNonNull(scenario, "Evaluation scenario is required.");
        Objects.requireNonNull(governanceMode, "Governance mode must be explicitly supplied.");
        requireText(workflowId, "Workflow id is required.");
        requireText(toolCallId, "Tool call id is required.");
        recordedTiming = recordedTiming == null ? EvaluationTiming.unavailable() : recordedTiming;
        auditEvidence = List.copyOf(Objects.requireNonNull(auditEvidence, "Audit evidence is required."));
        if (auditEvidence.stream().anyMatch(event -> !workflowId.equals(event.workflowId()))) {
            throw new IllegalArgumentException("Evaluation evidence must belong to the observed workflow.");
        }
        if (auditEvidence.stream().anyMatch(event -> event.workflowType() != scenario.workflowType())) {
            throw new IllegalArgumentException("Evaluation evidence must match the scenario workflow type.");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
