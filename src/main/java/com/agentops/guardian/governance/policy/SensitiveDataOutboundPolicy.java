package com.agentops.guardian.governance.policy;

import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.model.WorkflowContext;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataOutboundPolicy implements GovernancePolicy {
    private static final String EMAIL_TOOL = "sendEmail";

    @Override
    public GovernanceDecision evaluate(WorkflowContext workflow, ToolCallEvent proposedCall) {
        if (!EMAIL_TOOL.equals(proposedCall.toolName())) {
            return allow("Tool call does not involve outbound email.");
        }

        DataClassification classification = workflow.getCurrentDataClassification();

        if (classification == null) {
            return allow("No classified customer data is present in the workflow.");
        }

        if (classification == DataClassification.ANALYTICAL
                || classification == DataClassification.REDACTED_DETAIL) {
            return allow(
                    "Outbound email is allowed because customer data was transformed into an approved external-sharing classification.");
        }

        return new GovernanceDecision(
                GovernanceDecision.DecisionType.BLOCK,
                "Outbound email blocked because the workflow still contains raw customer data.");
    }

    private GovernanceDecision allow(String reason) {
        return new GovernanceDecision(
                GovernanceDecision.DecisionType.ALLOW,
                reason);
    }
}