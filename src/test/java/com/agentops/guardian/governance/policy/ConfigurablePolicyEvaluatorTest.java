package com.agentops.guardian.governance.policy;

import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.workflow.ToolCapabilityRegistry;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowDefinition;
import com.agentops.guardian.governance.workflow.WorkflowGraph;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigurablePolicyEvaluatorTest {

    private PolicyDefinitionRepository repository;
    private ConfigurablePolicyEvaluator evaluator;
    private WorkflowContext workflow;
    private ToolCallEvent proposedCall;

    @BeforeEach
    void setUp() {
        repository = mock(PolicyDefinitionRepository.class);
        evaluator = new ConfigurablePolicyEvaluator(repository, new ToolCapabilityRegistry());
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.CUSTOMER_REPORTING,
                Set.of(
                        WorkflowCapability.READ_CUSTOMER_DATA,
                        WorkflowCapability.GENERATE_REPORT,
                        WorkflowCapability.SEND_EMAIL
                ),
                WorkflowGraph.customerReportingGraph()
        );
        workflow = new WorkflowContext("PolicyTestAgent", WorkflowType.CUSTOMER_REPORTING, "Test policy", definition);
        proposedCall = new ToolCallEvent(
                "call-1",
                "getCustomerData",
                "{}",
                "function",
                Instant.parse("2026-09-29T10:00:00Z")
        );
    }

    @Test
    void blocksMatchingPolicyAndReturnsItsIdAndDescription() {
        workflow.markDataAcquired(DataClassification.RAW_CUSTOMER_DATA);
        PolicyDefinition policy = policy(
                "block-raw-data",
                true,
                1,
                new PolicyTarget("getCustomerData", WorkflowCapability.READ_CUSTOMER_DATA),
                PolicyConditionField.DATA_CLASSIFICATION,
                PolicyConditionOperator.IN,
                "ANALYTICAL, RAW_CUSTOMER_DATA",
                PolicyAction.BLOCK,
                "Raw customer data access is blocked."
        );
        when(repository.findAllByEnabledTrueOrderByPriorityAscPolicyIdAsc())
                .thenReturn(List.of(PolicyDefinitionEntity.from(policy)));

        GovernanceDecision decision = evaluator.evaluate(workflow, proposedCall);

        assertEquals(GovernanceDecision.DecisionType.BLOCK, decision.decision());
        assertEquals("block-raw-data", decision.policyId());
        assertEquals("Raw customer data access is blocked.", decision.reason());
    }

    @Test
    void matchingAllowCannotBypassTheHighestPriorityMatchingBlock() {
        PolicyDefinition allow = policy(
                "allow-call", true, 1, new PolicyTarget("getCustomerData", null),
                PolicyConditionField.TOOL_NAME, PolicyConditionOperator.EQUALS, "getCustomerData",
                PolicyAction.ALLOW, ""
        );
        PolicyDefinition laterBlock = policy(
                "block-later", true, 5, new PolicyTarget("getCustomerData", null),
                PolicyConditionField.TOOL_NAME, PolicyConditionOperator.EQUALS, "getCustomerData",
                PolicyAction.BLOCK, "Later block."
        );
        PolicyDefinition firstBlock = policy(
                "block-first", true, 3, new PolicyTarget("getCustomerData", null),
                PolicyConditionField.TOOL_NAME, PolicyConditionOperator.EQUALS, "getCustomerData",
                PolicyAction.BLOCK, "First block."
        );
        when(repository.findAllByEnabledTrueOrderByPriorityAscPolicyIdAsc())
                .thenReturn(List.of(laterBlock, allow, firstBlock).stream()
                        .map(PolicyDefinitionEntity::from)
                        .toList());

        GovernanceDecision decision = evaluator.evaluate(workflow, proposedCall);

        assertEquals(GovernanceDecision.DecisionType.BLOCK, decision.decision());
        assertEquals("block-first", decision.policyId());
        assertEquals("First block.", decision.reason());
    }

    @Test
    void ignoresDisabledAndNonmatchingPolicies() {
        PolicyDefinition disabledBlock = policy(
                "disabled-block", false, 1, new PolicyTarget("getCustomerData", null),
                PolicyConditionField.TOOL_NAME, PolicyConditionOperator.EQUALS, "getCustomerData",
                PolicyAction.BLOCK, "Disabled."
        );
        PolicyDefinition wrongTarget = policy(
                "wrong-target", true, 2, new PolicyTarget("sendEmail", null),
                PolicyConditionField.TOOL_NAME, PolicyConditionOperator.EQUALS, "getCustomerData",
                PolicyAction.BLOCK, "Wrong target."
        );
        PolicyDefinition wrongCondition = policy(
                "wrong-condition", true, 3, new PolicyTarget("getCustomerData", null),
                PolicyConditionField.WORKFLOW_STATE, PolicyConditionOperator.EQUALS,
                WorkflowState.COMPLETED.name(), PolicyAction.BLOCK, "Wrong state."
        );
        when(repository.findAllByEnabledTrueOrderByPriorityAscPolicyIdAsc())
                .thenReturn(List.of(disabledBlock, wrongTarget, wrongCondition).stream()
                        .map(PolicyDefinitionEntity::from)
                        .toList());

        GovernanceDecision decision = evaluator.evaluate(workflow, proposedCall);

        assertEquals(GovernanceDecision.DecisionType.ALLOW, decision.decision());
        assertEquals("GOVERNANCE_POLICY_ENGINE", decision.policyId());
    }

    @Test
    void initializedSensitiveDataRulePreservesDecisionAndPolicyId() throws Exception {
        PolicyDefinitionEntity seededPolicy = PolicyDefinitionEntity.from(
                SensitiveDataOutboundPolicyInitializer.initialPolicy()
        );
        when(repository.findByPolicyId(SensitiveDataOutboundPolicyInitializer.POLICY_ID))
                .thenReturn(Optional.empty(), Optional.of(seededPolicy));
        when(repository.save(any(PolicyDefinitionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SensitiveDataOutboundPolicyInitializer initializer =
                new SensitiveDataOutboundPolicyInitializer(repository);
        initializer.run();
        initializer.run();
        verify(repository, times(1)).save(any(PolicyDefinitionEntity.class));

        when(repository.findAllByEnabledTrueOrderByPriorityAscPolicyIdAsc())
                .thenReturn(List.of(seededPolicy));
        GovernancePolicyEngine engine = new GovernancePolicyEngine(evaluator);
        ToolCallEvent emailCall = new ToolCallEvent(
                "email-1",
                "sendEmail",
                "{}",
                "function",
                Instant.parse("2026-09-30T10:00:00Z")
        );

        workflow.markDataAcquired(DataClassification.RAW_CUSTOMER_DATA);
        GovernanceDecision blocked = engine.evaluate(workflow, emailCall);
        assertEquals(GovernanceDecision.DecisionType.BLOCK, blocked.decision());
        assertEquals(SensitiveDataOutboundPolicyInitializer.POLICY_ID, blocked.policyId());
        assertEquals(SensitiveDataOutboundPolicyInitializer.BLOCK_REASON, blocked.reason());

        workflow.markDataAcquired(DataClassification.ANALYTICAL);
        GovernanceDecision analyticalAllowed = engine.evaluate(workflow, emailCall);
        assertEquals(GovernanceDecision.DecisionType.ALLOW, analyticalAllowed.decision());
        assertEquals("GOVERNANCE_POLICY_ENGINE", analyticalAllowed.policyId());
        assertEquals("All governance policies passed.", analyticalAllowed.reason());

        workflow.markDataAcquired(DataClassification.REDACTED_DETAIL);
        GovernanceDecision redactedAllowed = engine.evaluate(workflow, emailCall);
        assertEquals(GovernanceDecision.DecisionType.ALLOW, redactedAllowed.decision());
        assertEquals("GOVERNANCE_POLICY_ENGINE", redactedAllowed.policyId());
        assertEquals("All governance policies passed.", redactedAllowed.reason());
    }

    private PolicyDefinition policy(
            String id,
            boolean enabled,
            int priority,
            PolicyTarget target,
            PolicyConditionField field,
            PolicyConditionOperator operator,
            String value,
            PolicyAction action,
            String description
    ) {
        return new PolicyDefinition(
                id, id, description, enabled, priority, target, field, operator, value, action
        );
    }
}