package com.agentops.guardian.governance.policy;

import java.util.Objects;

public record PolicyDefinition(
        String policyId,
        String name,
        String description,
        boolean enabled,
        int priority,
        PolicyTarget target,
        PolicyConditionField conditionField,
        PolicyConditionOperator conditionOperator,
        String conditionValue,
        PolicyAction action
) {

    public PolicyDefinition {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("Policy id is required.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Policy name is required.");
        }
        Objects.requireNonNull(target, "Policy target is required.");
        Objects.requireNonNull(conditionField, "Policy condition field is required.");
        Objects.requireNonNull(conditionOperator, "Policy condition operator is required.");
        Objects.requireNonNull(conditionValue, "Policy condition value is required.");
        Objects.requireNonNull(action, "Policy action is required.");
    }
}