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
        @Size(max = 36)
        String conversationId
) {
    public AgentCoordinatorRequest(String message, String recipient, ReportHandoff report) {
        this(message, recipient, report, null);
    }

    public record ReportHandoff(
            @NotNull ReportType type,
            boolean redacted,
            @PositiveOrZero int customerCount,
            @NotBlank @Size(max = 30000) String content
    ) {
    }
}
