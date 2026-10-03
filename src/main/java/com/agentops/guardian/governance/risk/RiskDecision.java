package com.agentops.guardian.governance.risk;

public record RiskDecision(RiskLevel riskLevel, GovernanceIntervention intervention, String reason) {

    public RiskDecision {
        if (riskLevel == null) {
            throw new IllegalArgumentException("Risk level is required.");
        }
        if (intervention == null) {
            throw new IllegalArgumentException("Governance intervention is required.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Risk decision reason is required.");
        }
    }

    public static RiskDecision from(RiskAssessment assessment) {
        if (assessment == null) {
            throw new IllegalArgumentException("Risk assessment is required.");
        }
        GovernanceIntervention intervention = switch (assessment.level()) {
            case LOW -> GovernanceIntervention.NONE;
            case MODERATE -> GovernanceIntervention.AUDIT;
            case HIGH -> GovernanceIntervention.REQUIRE_APPROVAL;
            case CRITICAL -> GovernanceIntervention.BLOCK;
        };
        return new RiskDecision(assessment.level(), intervention, assessment.reason());
    }
}