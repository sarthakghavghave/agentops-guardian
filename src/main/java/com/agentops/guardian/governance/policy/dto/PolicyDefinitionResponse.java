package com.agentops.guardian.governance.policy.dto;

import com.agentops.guardian.governance.policy.PolicyAction;
import com.agentops.guardian.governance.policy.PolicyConditionField;
import com.agentops.guardian.governance.policy.PolicyConditionOperator;
import com.agentops.guardian.governance.policy.PolicyDefinition;
import com.agentops.guardian.governance.workflow.WorkflowCapability;

public record PolicyDefinitionResponse(
        String policyId,
        String name,
        String description,
        boolean enabled,
        int priority,
        String targetToolName,
        WorkflowCapability targetCapability,
        PolicyConditionField conditionField,
        PolicyConditionOperator conditionOperator,
        String conditionValue,
        PolicyAction action
) {

    public static PolicyDefinitionResponse from(PolicyDefinition definition) {
        return new PolicyDefinitionResponse(
                definition.policyId(),
                definition.name(),
                definition.description(),
                definition.enabled(),
                definition.priority(),
                definition.target().toolName(),
                definition.target().capability(),
                definition.conditionField(),
                definition.conditionOperator(),
                definition.conditionValue(),
                definition.action()
        );
    }
}