package com.agentops.guardian.governance.exception;

import com.agentops.guardian.governance.risk.RiskDecision;
import com.agentops.guardian.governance.intervention.GovernanceIntervention;

public class ApprovalRequiredException extends RuntimeException {

    private final String toolCallId;
    private final RiskDecision riskDecision;
    private final String interventionId;
    private final GovernanceIntervention intervention;
    private final String proposedToolArguments;

    public ApprovalRequiredException(GovernanceIntervention intervention) {
        this(intervention, null);
    }

    public ApprovalRequiredException(GovernanceIntervention intervention, String proposedToolArguments) {
        super("Approval required for tool call " + intervention.toolCallId() + ": " + intervention.reason());
        this.toolCallId = intervention.toolCallId();
        this.riskDecision = new RiskDecision(
                intervention.riskLevel(),
                intervention.intervention(),
                intervention.reason()
        );
        this.interventionId = intervention.interventionId();
        this.intervention = intervention;
        this.proposedToolArguments = proposedToolArguments;
    }

    public ApprovalRequiredException(String toolCallId, RiskDecision riskDecision) {
        super("Approval required for tool call " + toolCallId + ": " + riskDecision.reason());
        this.toolCallId = toolCallId;
        this.riskDecision = riskDecision;
        this.interventionId = null;
        this.intervention = null;
        this.proposedToolArguments = null;
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

    public GovernanceIntervention getIntervention() {
        return intervention;
    }

    public String getProposedToolArguments() {
        return proposedToolArguments;
    }
}