package com.agentops.guardian.coordinator.dto;

import com.agentops.guardian.coordinator.AgentCoordinator.IntentAction;

public record AgentCoordinatorResponse(
        IntentAction action,
        String response,
        boolean clarificationRequired,
        String conversationId
) {
    public static AgentCoordinatorResponse completed(IntentAction action, String response, String conversationId) {
        return new AgentCoordinatorResponse(action, response, false, conversationId);
    }

    public static AgentCoordinatorResponse clarification(String question, String conversationId) {
        return new AgentCoordinatorResponse(IntentAction.CLARIFICATION_REQUIRED, question, true, conversationId);
    }
}
