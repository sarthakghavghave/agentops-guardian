package com.agentops.guardian.governance.policy.dto;

import com.agentops.guardian.governance.policy.PolicyAction;
import com.agentops.guardian.governance.policy.PolicyConditionField;
import com.agentops.guardian.governance.policy.PolicyConditionOperator;
import com.agentops.guardian.governance.policy.PolicyDefinition;
import com.agentops.guardian.governance.policy.PolicyTarget;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PolicyDefinitionRequest(
        @NotBlank String policyId,
        @NotBlank String name,
        String description,
        boolean enabled,
        int priority,
        String targetToolName,
        WorkflowCapability targetCapability,
        @NotNull PolicyConditionField conditionField,
        @NotNull PolicyConditionOperator conditionOperator,
        @NotNull String conditionValue,
        @NotNull PolicyAction action
) {

    @AssertTrue(message = "A non-blank target tool name or target capability is required.")
    public boolean isTargetValid() {
        boolean toolNameValid = targetToolName == null || !targetToolName.isBlank();
        boolean targetPresent = targetToolName != null && !targetToolName.isBlank()
                || targetCapability != null;
        return toolNameValid && targetPresent;
    }

    public PolicyDefinition toDomain() {
        return new PolicyDefinition(
                policyId,
                name,
                description,
                enabled,
                priority,
                new PolicyTarget(targetToolName, targetCapability),
                conditionField,
                conditionOperator,
                conditionValue,
                action
        );
    }
}