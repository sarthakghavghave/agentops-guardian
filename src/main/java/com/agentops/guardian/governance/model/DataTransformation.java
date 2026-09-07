package com.agentops.guardian.governance.model;

import java.time.Instant;

public record DataTransformation(
        DataClassification sourceClassification,
        DataClassification resultClassification,
        String transformationType,
        Instant timestamp) {
}