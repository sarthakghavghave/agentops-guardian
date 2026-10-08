package com.agentops.guardian.governance.evaluation;

import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;

import java.util.Objects;

public record EvaluationScenario(
        String scenarioId,
        String name,
        String description,
        WorkflowType workflowType,
        String intendedTool,
        WorkflowCapability intendedCapability,
        EvaluationMisuseCategory misuseCategory,
        boolean unsafeIfExecuted
) {
    public EvaluationScenario {
        requireText(scenarioId, "Scenario id is required.");
        requireText(name, "Scenario name is required.");
        requireText(description, "Scenario description is required.");
        requireText(intendedTool, "Intended tool is required.");
        Objects.requireNonNull(workflowType, "Workflow type is required.");
        Objects.requireNonNull(intendedCapability, "Intended capability is required.");
        Objects.requireNonNull(misuseCategory, "Misuse category is required.");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
