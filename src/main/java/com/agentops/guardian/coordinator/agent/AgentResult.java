package com.agentops.guardian.coordinator.agent;

public record AgentResult(
        AgentResultStatus status,
        String response,
        String artifactReference,
        String dependencyAgentId,
        String clarificationQuestion,
        String errorMessage
) {
    public static AgentResult success(String response) {
        return new AgentResult(AgentResultStatus.SUCCESS, response, null, null, null, null);
    }

    public static AgentResult success(String response, String artifactReference) {
        return new AgentResult(AgentResultStatus.SUCCESS, response, artifactReference, null, null, null);
    }

    public static AgentResult clarification(String question) {
        return new AgentResult(AgentResultStatus.CLARIFICATION_REQUIRED, question, null, null, question, null);
    }

    public static AgentResult needsDependency(String dependencyAgentId, String reason) {
        return new AgentResult(AgentResultStatus.NEEDS_DEPENDENCY, reason, null, dependencyAgentId, null, null);
    }

    public static AgentResult failure(String errorMessage) {
        return new AgentResult(AgentResultStatus.FAILED, null, null, null, null, errorMessage);
    }

    public boolean isSuccess() {
        return status == AgentResultStatus.SUCCESS;
    }

    public boolean isClarification() {
        return status == AgentResultStatus.CLARIFICATION_REQUIRED;
    }

    public boolean isNeedsDependency() {
        return status == AgentResultStatus.NEEDS_DEPENDENCY;
    }
}

