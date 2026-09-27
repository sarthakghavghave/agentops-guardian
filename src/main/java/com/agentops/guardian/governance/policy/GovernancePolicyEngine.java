package com.agentops.guardian.governance.policy;

import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.context.WorkflowContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GovernancePolicyEngine {

    private static final String DEFAULT_POLICY_ID = "GOVERNANCE_POLICY_ENGINE";

    private final List<GovernancePolicy> policies;

    public GovernanceDecision evaluate(WorkflowContext workflow, ToolCallEvent proposedCall) {
        for (GovernancePolicy policy : policies) {

            GovernanceDecision decision = policy.evaluate(workflow, proposedCall);

            if (decision.decision() == GovernanceDecision.DecisionType.BLOCK) {
                return decision;
            }
        }

        return new GovernanceDecision(
                GovernanceDecision.DecisionType.ALLOW,
                "All governance policies passed.",
                DEFAULT_POLICY_ID
        );
    }
}