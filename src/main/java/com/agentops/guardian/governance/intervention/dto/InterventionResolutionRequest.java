package com.agentops.guardian.governance.intervention.dto;

import jakarta.validation.constraints.NotBlank;

public record InterventionResolutionRequest(
        @NotBlank String resolvedBy,
        @NotBlank String resolutionReason
) {
}