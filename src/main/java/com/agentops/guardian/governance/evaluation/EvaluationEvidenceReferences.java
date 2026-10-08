package com.agentops.guardian.governance.evaluation;

import java.time.Instant;
import java.util.List;

public record EvaluationEvidenceReferences(
        String workflowId,
        String toolCallId,
        List<Long> auditEventIds,
        List<String> interventionIds,
        List<String> policyIds,
        List<Instant> relevantTimestamps
) {
    public EvaluationEvidenceReferences {
        auditEventIds = List.copyOf(auditEventIds);
        interventionIds = List.copyOf(interventionIds);
        policyIds = List.copyOf(policyIds);
        relevantTimestamps = List.copyOf(relevantTimestamps);
    }
}
