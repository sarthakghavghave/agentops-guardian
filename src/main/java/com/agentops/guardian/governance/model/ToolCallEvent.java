package com.agentops.guardian.governance.model;

import java.time.Instant;

public record ToolCallEvent(
        String toolCallId,
        String toolName,
        String arguments,
        String toolType,
        Instant timestamp
) {}