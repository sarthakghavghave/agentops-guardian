package com.agentops.guardian.coordinator.context;

import com.agentops.guardian.governance.model.ToolInvocationOutcome;
import com.agentops.guardian.governance.workflow.WorkflowCapability;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VerifiedCustomerReadEvidence(
        UUID conversationId,
        String sourceWorkflowId,
        String sourceToolCallId,
        WorkflowCapability capability,
        ToolInvocationOutcome.Status outcome,
        Instant verifiedAt,
        List<String> customerEmails
) {

    public VerifiedCustomerReadEvidence {
        if (conversationId == null || sourceWorkflowId == null || sourceWorkflowId.isBlank()
                || sourceToolCallId == null || sourceToolCallId.isBlank()
                || capability != WorkflowCapability.READ_CUSTOMER_DATA
                || outcome != ToolInvocationOutcome.Status.SUCCEEDED
                || verifiedAt == null) {
            throw new IllegalArgumentException("Verified customer-read evidence is incomplete.");
        }
        customerEmails = customerEmails == null ? List.of() : List.copyOf(customerEmails);
    }
}
