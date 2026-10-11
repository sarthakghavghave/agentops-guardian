package com.agentops.guardian.coordinator.dto;

import com.agentops.guardian.tool.ReportTools.ReportType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AgentCoordinatorRequest(
        @NotBlank
        @Size(max = 4000)
        String message,
        @Email
        @Size(max = 320)
        String recipient,
        @Valid
        ReportHandoff report,
        @Size(max = 36) String conversationId,
        @Size(max = 500) String subjectInstructions,
        @Size(max = 4000) String bodyInstructions
) {
    public AgentCoordinatorRequest(String message, String recipient, ReportHandoff report) {
        this(message, recipient, report, null, null, null);
    }

    public AgentCoordinatorRequest(
            String message,
            String recipient,
            ReportHandoff report,
            String conversationId
    ) {
        this(message, recipient, report, conversationId, null, null);
    }

    public record ReportHandoff(
            @NotNull ReportType type,
            boolean redacted,
            @PositiveOrZero int customerCount,
            @NotBlank @Size(max = 30000) String content
    ) {
    }
}
