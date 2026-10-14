package com.agentops.guardian.coordinator.context;

import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharedContextService {

    // 1. Conversation history
    UUID open(String requestedConversationId);

    List<Message> history(UUID conversationId);

    void recordTurn(UUID conversationId, String userMessage, String assistantContext);

    // 2. Artifact references
    Artifact registerArtifact(UUID conversationId, Artifact artifact);

    Optional<Artifact> getArtifact(UUID conversationId, String artifactId);

    Optional<Artifact> getLatestArtifact(UUID conversationId, ArtifactType type);

    List<Artifact> getArtifacts(UUID conversationId, ArtifactType type);

    void clearArtifacts(UUID conversationId, ArtifactType type);

    // 3. Execution records
    void recordExecution(UUID conversationId, ExecutionRecord record);

    List<ExecutionRecord> getExecutionRecords(UUID conversationId);

    Optional<ExecutionRecord> getLatestExecution(UUID conversationId);

    void recordVerifiedCustomerRead(VerifiedCustomerReadEvidence evidence);

    List<VerifiedCustomerReadEvidence> getVerifiedCustomerReads(UUID conversationId);

    // 4. Session metadata
    void setSessionAttribute(UUID conversationId, String key, Object value);

    <T> Optional<T> getSessionAttribute(UUID conversationId, String key, Class<T> type);

    void removeSessionAttribute(UUID conversationId, String key);

    void clearSessionAttributes(UUID conversationId);
}
