package com.agentops.guardian.governance.exception;

import com.agentops.guardian.governance.risk.RiskDecision;
import com.agentops.guardian.governance.intervention.GovernanceIntervention;

public class ApprovalRequiredException extends RuntimeException {

    private final String toolCallId;
    private final RiskDecision riskDecision;
    private final String interventionId;

    public ApprovalRequiredException(GovernanceIntervention intervention) {
        super("Approval required for tool call " + intervention.toolCallId() + ": " + intervention.reason());
        this.toolCallId = intervention.toolCallId();
        this.riskDecision = new RiskDecision(
                intervention.riskLevel(),
                intervention.intervention(),
                intervention.reason()
        );
        this.interventionId = intervention.interventionId();
    }

    public ApprovalRequiredException(String toolCallId, RiskDecision riskDecision) {
        super("Approval required for tool call " + toolCallId + ": " + riskDecision.reason());
        this.toolCallId = toolCallId;
        this.riskDecision = riskDecision;
        this.interventionId = null;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public RiskDecision getRiskDecision() {
        return riskDecision;
    }

    public String getInterventionId() {
        return interventionId;
    }
}