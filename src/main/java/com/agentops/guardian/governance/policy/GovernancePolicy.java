package com.agentops.guardian.governance.policy;

import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.model.ToolCallEvent;

public interface GovernancePolicy {

    String policyId();
    GovernanceDecision evaluate(WorkflowContext workflow, ToolCallEvent proposedCall);
}