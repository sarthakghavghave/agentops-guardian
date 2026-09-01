package com.agentops.guardian.governance.model;

public record GovernanceDecision(DecisionType decision, String reason) {
    public enum DecisionType {
        ALLOW,
        BLOCK
    }
}