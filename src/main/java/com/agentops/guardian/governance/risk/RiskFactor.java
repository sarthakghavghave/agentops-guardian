package com.agentops.guardian.governance.risk;

public record RiskFactor(RiskFactorType type, String reason) {

    public RiskFactor {
        if (type == null) {
            throw new IllegalArgumentException("Risk factor type is required.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Risk factor reason is required.");
        }
    }
}