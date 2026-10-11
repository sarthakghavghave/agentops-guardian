package com.agentops.guardian.coordinator.context;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public record Artifact(
        String id,
        ArtifactType type,
        String title,
        String content,
        Instant createdAt,
        Map<String, String> metadata
) {
    public static final int MAX_ARTIFACT_CHARACTERS = 30000;
    public static final String PROVENANCE_KEY = "provenance";
    public static final String GENERATED_PROVENANCE = "CUSTOMER_REPORT_AGENT";
    public static final String SUPPLIED_PROVENANCE = "USER_SUPPLIED";
    public static final String FILE_REFERENCE_KEY = "fileReference";
    public static final String FORMAT_KEY = "format";
    public static final String REPORT_TYPE_KEY = "reportType";

    public Artifact {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (type == null) {
            throw new IllegalArgumentException("Artifact type is required.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Artifact content is required.");
        }
        if (content.length() > MAX_ARTIFACT_CHARACTERS) {
            throw new IllegalArgumentException("Artifact content exceeds the maximum allowed length of "
                    + MAX_ARTIFACT_CHARACTERS + " characters.");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
    }

    public static Artifact report(String title, String content) {
        return suppliedReport(title, content);
    }

    public static Artifact report(String id, String title, String content) {
        return suppliedReport(id, title, content);
    }

    public static Artifact generatedReport(String title, String content) {
        return new Artifact(
                UUID.randomUUID().toString(),
                ArtifactType.REPORT,
                title,
                content,
                Instant.now(),
                Map.of(PROVENANCE_KEY, GENERATED_PROVENANCE)
        );
    }

    public static Artifact generatedPdfReport(
            String title,
            String fileReference,
            long fileSizeBytes,
            int customerCount
    ) {
        if (fileReference == null || fileReference.isBlank()) {
            throw new IllegalArgumentException("A stored report file reference is required.");
        }
        if (fileSizeBytes <= 0 || customerCount < 0) {
            throw new IllegalArgumentException("Stored report metadata is invalid.");
        }
        return new Artifact(
                UUID.randomUUID().toString(),
                ArtifactType.REPORT,
                title,
                fileReference,
                Instant.now(),
                Map.of(
                        PROVENANCE_KEY, GENERATED_PROVENANCE,
                        FILE_REFERENCE_KEY, fileReference,
                        FORMAT_KEY, "PDF",
                        REPORT_TYPE_KEY, "ANALYTICAL",
                        "customerCount", Integer.toString(customerCount),
                        "fileSizeBytes", Long.toString(fileSizeBytes)
                )
        );
    }

    public static Artifact suppliedReport(String title, String content) {
        return suppliedReport(UUID.randomUUID().toString(), title, content);
    }

    public static Artifact suppliedReport(String id, String title, String content) {
        return new Artifact(
                id,
                ArtifactType.REPORT,
                title,
                content,
                Instant.now(),
                Map.of(PROVENANCE_KEY, SUPPLIED_PROVENANCE)
        );
    }
}
