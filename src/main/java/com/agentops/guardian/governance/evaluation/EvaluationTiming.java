package com.agentops.guardian.governance.evaluation;

import java.time.Instant;

public record EvaluationTiming(
        Instant actionStartedAt,
        Instant guardianEvaluationStartedAt,
        Instant guardianEvaluationCompletedAt,
        Instant interventionCreatedAt,
        Instant interventionResolvedAt,
        Instant toolExecutionStartedAt,
        Instant toolExecutionCompletedAt
) {
    public static EvaluationTiming unavailable() {
        return new EvaluationTiming(null, null, null, null, null, null, null);
    }
}
