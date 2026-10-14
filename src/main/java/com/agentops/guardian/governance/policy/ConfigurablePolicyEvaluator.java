package com.agentops.guardian.governance.policy;

import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.coordinator.context.SharedContextService;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.workflow.ToolCapabilityRegistry;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Objects;

@Component
public class ConfigurablePolicyEvaluator {

    private static final String DEFAULT_POLICY_ID = "GOVERNANCE_POLICY_ENGINE";
    private static final String DEFAULT_ALLOW_REASON = "All governance policies passed.";

    private final PolicyDefinitionRepository policyRepository;
    private final ToolCapabilityRegistry toolCapabilityRegistry;
    private final SharedContextService sharedContextService;

    @Autowired
    public ConfigurablePolicyEvaluator(
            PolicyDefinitionRepository policyRepository,
            ToolCapabilityRegistry toolCapabilityRegistry,
            SharedContextService sharedContextService
    ) {
        this.policyRepository = policyRepository;
        this.toolCapabilityRegistry = toolCapabilityRegistry;
        this.sharedContextService = sharedContextService;
    }

    public ConfigurablePolicyEvaluator(
            PolicyDefinitionRepository policyRepository,
            ToolCapabilityRegistry toolCapabilityRegistry
    ) {
        this(policyRepository, toolCapabilityRegistry, null);
    }

    public GovernanceDecision evaluate(WorkflowContext workflow, ToolCallEvent proposedCall) {
        Objects.requireNonNull(workflow, "Workflow context is required.");
        Objects.requireNonNull(proposedCall, "Proposed tool call is required.");

        PolicyDefinition blockingPolicy = policyRepository
                .findAllByEnabledTrueOrderByPriorityAscPolicyIdAsc()
                .stream()
                .map(PolicyDefinitionEntity::toDomain)
                .filter(PolicyDefinition::enabled)
                .sorted(Comparator.comparingInt(PolicyDefinition::priority)
                        .thenComparing(PolicyDefinition::policyId))
                .filter(policy -> targetApplies(policy.target(), proposedCall))
                .filter(policy -> conditionMatches(policy, workflow, proposedCall))
                .filter(policy -> policy.action() == PolicyAction.BLOCK)
                .findFirst()
                .orElse(null);

        if (blockingPolicy == null) {
            return new GovernanceDecision(
                    GovernanceDecision.DecisionType.ALLOW,
                    DEFAULT_ALLOW_REASON,
                    DEFAULT_POLICY_ID
            );
        }

        String reason = blockingPolicy.description();
        if (reason == null || reason.isBlank()) {
            reason = "Blocked by policy: " + blockingPolicy.name();
        }

        return new GovernanceDecision(
                GovernanceDecision.DecisionType.BLOCK,
                reason,
                blockingPolicy.policyId()
        );
    }

    private boolean targetApplies(PolicyTarget target, ToolCallEvent proposedCall) {
        WorkflowCapability proposedCapability = toolCapabilityRegistry.getCapability(proposedCall.toolName());
        return (target.toolName() == null || target.toolName().equals(proposedCall.toolName()))
                && (target.capability() == null || target.capability() == proposedCapability);
    }

    private boolean conditionMatches(
            PolicyDefinition policy,
            WorkflowContext workflow,
            ToolCallEvent proposedCall
    ) {
        String actualValue = conditionValue(policy.conditionField(), workflow, proposedCall);
        String expectedValue = policy.conditionValue();

        return switch (policy.conditionOperator()) {
            case EQUALS -> Objects.equals(actualValue, expectedValue);
            case NOT_EQUALS -> !Objects.equals(actualValue, expectedValue);
            case IN -> actualValue != null
                    && java.util.Arrays.stream(expectedValue.split(",", -1))
                    .map(String::trim)
                    .anyMatch(actualValue::equals);
        };
    }

    private String conditionValue(
            PolicyConditionField field,
            WorkflowContext workflow,
            ToolCallEvent proposedCall
    ) {
        return switch (field) {
            case DATA_CLASSIFICATION -> enumName(workflow.getCurrentDataClassification());
            case TOOL_NAME -> proposedCall.toolName();
            case WORKFLOW_TYPE -> enumName(workflow.getWorkflowType());
            case WORKFLOW_STATE -> enumName(workflow.getCurrentState());
            case CAPABILITY -> enumName(toolCapabilityRegistry.getCapability(proposedCall.toolName()));
            case VERIFIED_CUSTOMER_READ_IN_CONVERSATION -> hasVerifiedCustomerRead(workflow, proposedCall)
                    ? "true"
                    : "false";
        };
    }

    private boolean hasVerifiedCustomerRead(WorkflowContext workflow, ToolCallEvent proposedCall) {
        if (sharedContextService == null
                || workflow.getWorkflowType() != WorkflowType.EMAIL_COMMUNICATION
                || toolCapabilityRegistry.getCapability(proposedCall.toolName())
                != WorkflowCapability.READ_CONVERSATION_HISTORY
                || workflow.getConversationId() == null) {
            return false;
        }
        return !sharedContextService.getVerifiedCustomerReads(workflow.getConversationId()).isEmpty();
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}