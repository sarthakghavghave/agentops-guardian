package com.agentops.guardian.governance.policy;

import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.WorkflowContext;
import com.agentops.guardian.governance.model.ToolCallEvent;

public interface GovernancePolicy {
    GovernanceDecision evaluate(WorkflowContext workflow, ToolCallEvent proposedCall);
}