package com.agentops.guardian.governance.model;

import com.agentops.guardian.governance.workflow.WorkflowCapability;

import java.time.Instant;
import java.util.UUID;

public record WorkflowAction(
        String actionId,
        String providerToolCallId,
        int sequenceNumber,
        String toolName,
        WorkflowCapability capability,
        String arguments,
        Instant proposedAt
) {

    public WorkflowAction {
        if (actionId == null || actionId.isBlank()) {
            throw new IllegalArgumentException("Action id is required.");
        }

        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("Tool name is required.");
        }

        if (capability == null) {
            throw new IllegalArgumentException("Capability is required.");
        }

        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("Sequence number must be greater than zero.");
        }

        if (proposedAt == null) {
            throw new IllegalArgumentException("Proposal timestamp is required.");
        }
    }

    public static WorkflowAction create(
            String providerToolCallId,
            int sequenceNumber,
            String toolName,
            WorkflowCapability capability,
            String arguments
    ) {
        return new WorkflowAction(
                UUID.randomUUID().toString(),
                providerToolCallId,
                sequenceNumber,
                toolName,
                capability,
                arguments,
                Instant.now()
        );
    }
}