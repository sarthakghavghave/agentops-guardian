package com.agentops.guardian.coordinator.dto;

import com.agentops.guardian.coordinator.AgentCoordinator.IntentAction;

public record AgentCoordinatorResponse(
        IntentAction action,
        String response,
        boolean clarificationRequired,
        String conversationId,
        boolean approvalRequired,
        String interventionId
) {
    public AgentCoordinatorResponse(
            IntentAction action,
            String response,
            boolean clarificationRequired,
            String conversationId
    ) {
        this(action, response, clarificationRequired, conversationId, false, null);
    }

    public static AgentCoordinatorResponse completed(IntentAction action, String response, String conversationId) {
        return new AgentCoordinatorResponse(action, response, false, conversationId);
    }

    public static AgentCoordinatorResponse clarification(String question, String conversationId) {
        return new AgentCoordinatorResponse(IntentAction.CLARIFICATION_REQUIRED, question, true, conversationId);
    }

    public static AgentCoordinatorResponse approvalRequired(
            IntentAction action,
            String interventionId,
            String conversationId
    ) {
        return approvalRequired(action, null, interventionId, conversationId);
    }

    public static AgentCoordinatorResponse approvalRequired(
            IntentAction action,
            String toolName,
            String interventionId,
            String conversationId
    ) {
        String actionDescription = toolName == null || toolName.isBlank()
                ? "this operation"
                : "the new action '" + toolName + "'";
        return new AgentCoordinatorResponse(
                action,
                "Approval is required for " + actionDescription + ". Intervention: " + interventionId,
                false,
                conversationId,
                true,
                interventionId
        );
    }
}
