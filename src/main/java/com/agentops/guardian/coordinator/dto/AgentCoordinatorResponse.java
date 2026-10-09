package com.agentops.guardian.coordinator.dto;

import com.agentops.guardian.coordinator.AgentCoordinator.IntentAction;

public record AgentCoordinatorResponse(
        IntentAction action,
        String response,
        boolean clarificationRequired
) {
    public static AgentCoordinatorResponse completed(IntentAction action, String response) {
        return new AgentCoordinatorResponse(action, response, false);
    }

    public static AgentCoordinatorResponse clarification(String question) {
        return new AgentCoordinatorResponse(IntentAction.CLARIFICATION_REQUIRED, question, true);
    }
}
