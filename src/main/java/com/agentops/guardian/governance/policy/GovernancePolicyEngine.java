package com.agentops.guardian.governance.policy;

import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.config.GovernanceSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GovernancePolicyEngine {

    private final ConfigurablePolicyEvaluator policyEvaluator;
    private final GovernanceSettings governanceSettings;

    @Autowired
    public GovernancePolicyEngine(
            ConfigurablePolicyEvaluator policyEvaluator,
            GovernanceSettings governanceSettings
    ) {
        this.policyEvaluator = policyEvaluator;
        this.governanceSettings = governanceSettings;
    }

    public GovernancePolicyEngine(ConfigurablePolicyEvaluator policyEvaluator) {
        this(policyEvaluator, enabledSettings());
    }

    public GovernanceDecision evaluate(WorkflowContext workflow, ToolCallEvent proposedCall) {
        if (!governanceSettings.isEnabled()) {
            return new GovernanceDecision(
                    GovernanceDecision.DecisionType.ALLOW,
                    "Governance is disabled.",
                    "GOVERNANCE_DISABLED"
            );
        }
        return policyEvaluator.evaluate(workflow, proposedCall);
    }

    private static GovernanceSettings enabledSettings() {
        return new GovernanceSettings();
    }
}