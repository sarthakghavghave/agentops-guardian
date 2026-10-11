package com.agentops.guardian.coordinator.context;

import java.time.Instant;
import java.util.UUID;

public record ExecutionRecord(
        String id,
        String agentId,
        String operation,
        ExecutionStatus status,
        String summary,
        String artifactId,
        Instant timestamp,
        String errorMessage
) {
    public ExecutionRecord {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("Agent ID is required.");
        }
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("Operation is required.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Execution status is required.");
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public static ExecutionRecord success(String agentId, String operation, String summary, String artifactId) {
        return new ExecutionRecord(UUID.randomUUID().toString(), agentId, operation, ExecutionStatus.SUCCESS, summary, artifactId, Instant.now(), null);
    }

    public static ExecutionRecord success(String agentId, String operation, String summary) {
        return success(agentId, operation, summary, null);
    }

    public static ExecutionRecord clarification(String agentId, String operation, String question) {
        return new ExecutionRecord(UUID.randomUUID().toString(), agentId, operation, ExecutionStatus.CLARIFICATION_REQUIRED, question, null, Instant.now(), null);
    }

    public static ExecutionRecord blocked(String agentId, String operation, String reason) {
        return new ExecutionRecord(UUID.randomUUID().toString(), agentId, operation, ExecutionStatus.BLOCKED_BY_GOVERNANCE, null, null, Instant.now(), reason);
    }

    public static ExecutionRecord failed(String agentId, String operation, String reason) {
        return new ExecutionRecord(UUID.randomUUID().toString(), agentId, operation, ExecutionStatus.FAILED, null, null, Instant.now(), reason);
    }
}

