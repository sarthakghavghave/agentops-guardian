package com.agentops.guardian.governance.model;

import java.time.Instant;

public record ToolInvocationOutcome<T>(
        String toolCallId,
        String toolName,
        Status status,
        T typedResult,
        String failureType,
        Instant completedAt
) {

    public ToolInvocationOutcome {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("Tool call id is required.");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("Tool name is required.");
        }
        if (status == null || completedAt == null) {
            throw new IllegalArgumentException("Tool outcome status and completion time are required.");
        }
        if (status != Status.SUCCEEDED && typedResult != null) {
            throw new IllegalArgumentException("Only successful tool outcomes may contain a typed result.");
        }
        if (status == Status.FAILED && (failureType == null || failureType.isBlank())) {
            throw new IllegalArgumentException("Failed tool outcomes require a failure type.");
        }
        if (status != Status.FAILED && failureType != null) {
            throw new IllegalArgumentException("Only failed tool outcomes may contain a failure type.");
        }
    }

    public enum Status {
        SUCCEEDED,
        FAILED,
        UNCONFIRMED
    }
}
