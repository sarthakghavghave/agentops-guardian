package com.agentops.guardian.governance.risk;

import java.util.List;

public record RiskAssessment(RiskLevel level, List<RiskFactor> factors, String reason) {

    public RiskAssessment {
        if (level == null) {
            throw new IllegalArgumentException("Risk level is required.");
        }
        if (factors == null) {
            throw new IllegalArgumentException("Risk factors are required.");
        }
        factors = List.copyOf(factors);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Risk assessment reason is required.");
        }
    }
}