package com.agentops.guardian.governance.model;

public record GovernanceDecision(DecisionType decision, String reason, String policyId) {

    public GovernanceDecision {
        if (decision == null) {
            throw new IllegalArgumentException("Decision is required.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Decision reason is required.");
        }
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("Policy id is required.");
        }
    }

    public enum DecisionType {
        ALLOW,
        BLOCK
    }
}