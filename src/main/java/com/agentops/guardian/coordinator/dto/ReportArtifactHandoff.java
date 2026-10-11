package com.agentops.guardian.coordinator.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public record ReportArtifactHandoff(
        String artifactId,
        String title,
        String reportType,
        String format,
        String storageReference,
        Instant createdAt,
        Map<String, String> metadata
) {
    public ReportArtifactHandoff {
        if (artifactId == null || artifactId.isBlank()
                || title == null || title.isBlank()
                || reportType == null || reportType.isBlank()
                || format == null || format.isBlank()
                || storageReference == null || storageReference.isBlank()
                || createdAt == null) {
            throw new IllegalArgumentException("Required report artifact handoff fields are missing.");
        }
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(Map.copyOf(metadata));
    }
}
