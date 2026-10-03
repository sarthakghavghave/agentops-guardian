package com.agentops.guardian.governance.exception;

import com.agentops.guardian.governance.risk.RiskDecision;

public class ApprovalRequiredException extends RuntimeException {

    private final String toolCallId;
    private final RiskDecision riskDecision;

    public ApprovalRequiredException(String toolCallId, RiskDecision riskDecision) {
        super("Approval required for tool call " + toolCallId + ": " + riskDecision.reason());
        this.toolCallId = toolCallId;
        this.riskDecision = riskDecision;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public RiskDecision getRiskDecision() {
        return riskDecision;
    }
}