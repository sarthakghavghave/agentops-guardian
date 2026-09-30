package com.agentops.guardian.governance.policy;

import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.context.WorkflowContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GovernancePolicyEngine {

    private final ConfigurablePolicyEvaluator policyEvaluator;

    public GovernanceDecision evaluate(WorkflowContext workflow, ToolCallEvent proposedCall) {
        return policyEvaluator.evaluate(workflow, proposedCall);
    }
}