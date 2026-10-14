package com.agentops.guardian.coordinator;

import com.agentops.guardian.coordinator.context.Artifact;
import com.agentops.guardian.coordinator.context.ArtifactType;
import com.agentops.guardian.coordinator.context.ExecutionRecord;
import com.agentops.guardian.coordinator.context.SharedContextService;
import com.agentops.guardian.coordinator.context.VerifiedCustomerReadEvidence;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ConversationMemory implements SharedContextService {

    private static final int DEFAULT_MAX_MESSAGES_PER_CONVERSATION = 12;
    private static final int MAX_STORED_TURN_CHARACTERS = 4000;
    private static final int MAX_CONVERSATIONS = 500;
    private static final int MAX_EXPIRED_IDS = 1000;
    private static final Duration CONVERSATION_TTL = Duration.ofMinutes(30);
    private static final Duration EXPIRED_ID_TTL = Duration.ofHours(1);
    private static final String REDACTED = "[redacted]";
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\w)(?:\\+?\\d[\\d ().-]{7,}\\d)(?!\\w)");

    private final ChatMemory chatMemory;
    private final Clock clock;
    private final Duration conversationTtl;
    private final int maxConversations;
    private final LinkedHashMap<String, Instant> activeConversations = new LinkedHashMap<>(16, 0.75f, true);
    private final LinkedHashMap<String, Instant> expiredConversations = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<String, Artifact>> conversationArtifacts = new LinkedHashMap<>();
    private final Map<String, List<ExecutionRecord>> conversationExecutions = new LinkedHashMap<>();
    private final Map<String, List<VerifiedCustomerReadEvidence>> verifiedCustomerReads = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> sessionAttributes = new LinkedHashMap<>();

    public ConversationMemory() {
        this(DEFAULT_MAX_MESSAGES_PER_CONVERSATION);
    }

    @Autowired
    public ConversationMemory(
            @Value("${agentops.conversation.max-messages:12}") int maxMessagesPerConversation
    ) {
        this(
                createChatMemory(maxMessagesPerConversation),
                Clock.systemUTC(),
                CONVERSATION_TTL,
                MAX_CONVERSATIONS
        );
    }

    ConversationMemory(ChatMemory chatMemory, Clock clock, Duration conversationTtl, int maxConversations) {
        if (chatMemory == null || clock == null || conversationTtl == null || conversationTtl.isNegative()
                || conversationTtl.isZero() || maxConversations < 1) {
            throw new IllegalArgumentException("Valid conversation memory configuration is required.");
        }
        this.chatMemory = chatMemory;
        this.clock = clock;
        this.conversationTtl = conversationTtl;
        this.maxConversations = maxConversations;
    }

    private static ChatMemory createChatMemory(int maxMessagesPerConversation) {
        if (maxMessagesPerConversation < 1) {
            throw new IllegalArgumentException("Conversation message limit must be positive.");
        }
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(maxMessagesPerConversation)
                .build();
    }

    @Override
    public synchronized UUID open(String requestedConversationId) {
        Instant now = clock.instant();
        purgeExpired(now);
        if (requestedConversationId == null) {
            return create(now);
        }

        UUID conversationId = parseId(requestedConversationId);
        String key = conversationId.toString();
        Instant lastAccess = activeConversations.get(key);
        if (lastAccess == null) {
            if (expiredConversations.containsKey(key)) {
                throw new ConversationException(
                        ConversationException.Kind.EXPIRED,
                        "Conversation has expired. Start a new conversation."
                );
            }
            throw new ConversationException(
                    ConversationException.Kind.UNKNOWN,
                    "Conversation was not found. Start a new conversation."
            );
        }
        if (isExpired(lastAccess, now)) {
            expire(key, now);
            throw new ConversationException(
                    ConversationException.Kind.EXPIRED,
                    "Conversation has expired. Start a new conversation."
            );
        }
        activeConversations.put(key, now);
        return conversationId;
    }

    @Override
    public synchronized List<Message> history(UUID conversationId) {
        String key = requireConversation(conversationId);
        return List.copyOf(chatMemory.get(key));
    }

    @Override
    public synchronized void recordTurn(
            UUID conversationId,
            String userMessage,
            String assistantContext
    ) {
        String key = requireConversation(conversationId);
        activeConversations.put(key, clock.instant());
        List<Message> turn = new ArrayList<>(2);
        turn.add(new UserMessage(sanitize(userMessage)));
        if (assistantContext != null && !assistantContext.isBlank()) {
            turn.add(new AssistantMessage(bound(assistantContext)));
        }
        chatMemory.add(key, turn);
    }

    // --- Artifact References ---

    @Override
    public synchronized Artifact registerArtifact(UUID conversationId, Artifact artifact) {
        if (artifact == null) {
            throw new IllegalArgumentException("A valid artifact is required.");
        }
        String key = requireConversation(conversationId);
        LinkedHashMap<String, Artifact> artifacts = conversationArtifacts.computeIfAbsent(
                key, k -> new LinkedHashMap<>()
        );
        artifacts.put(artifact.id(), artifact);
        return artifact;
    }

    @Override
    public synchronized Optional<Artifact> getArtifact(UUID conversationId, String artifactId) {
        if (artifactId == null || artifactId.isBlank()) {
            return Optional.empty();
        }
        String key = requireConversation(conversationId);
        Map<String, Artifact> artifacts = conversationArtifacts.get(key);
        if (artifacts == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(artifacts.get(artifactId));
    }

    @Override
    public synchronized Optional<Artifact> getLatestArtifact(UUID conversationId, ArtifactType type) {
        if (type == null) {
            return Optional.empty();
        }
        String key = requireConversation(conversationId);
        Map<String, Artifact> artifacts = conversationArtifacts.get(key);
        if (artifacts == null || artifacts.isEmpty()) {
            return Optional.empty();
        }
        return artifacts.values().stream()
                .filter(a -> a.type() == type)
                .max(Comparator.comparing(Artifact::createdAt));
    }

    @Override
    public synchronized List<Artifact> getArtifacts(UUID conversationId, ArtifactType type) {
        String key = requireConversation(conversationId);
        Map<String, Artifact> artifacts = conversationArtifacts.get(key);
        if (artifacts == null || artifacts.isEmpty()) {
            return List.of();
        }
        return artifacts.values().stream()
                .filter(a -> type == null || a.type() == type)
                .sorted(Comparator.comparing(Artifact::createdAt))
                .toList();
    }

    @Override
    public synchronized void clearArtifacts(UUID conversationId, ArtifactType type) {
        String key = requireConversation(conversationId);
        Map<String, Artifact> artifacts = conversationArtifacts.get(key);
        if (artifacts != null) {
            if (type == null) {
                artifacts.clear();
            } else {
                artifacts.entrySet().removeIf(entry -> entry.getValue().type() == type);
            }
        }
    }

    // --- Execution Records ---

    @Override
    public synchronized void recordExecution(UUID conversationId, ExecutionRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Execution record is required.");
        }
        String key = requireConversation(conversationId);
        conversationExecutions.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
    }

    @Override
    public synchronized List<ExecutionRecord> getExecutionRecords(UUID conversationId) {
        String key = requireConversation(conversationId);
        List<ExecutionRecord> records = conversationExecutions.get(key);
        return records != null ? Collections.unmodifiableList(records) : List.of();
    }

    @Override
    public synchronized Optional<ExecutionRecord> getLatestExecution(UUID conversationId) {
        String key = requireConversation(conversationId);
        List<ExecutionRecord> records = conversationExecutions.get(key);
        if (records == null || records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.getLast());
    }

    @Override
    public synchronized void recordVerifiedCustomerRead(VerifiedCustomerReadEvidence evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("Verified customer-read evidence is required.");
        }
        String key = requireConversation(evidence.conversationId());
        List<VerifiedCustomerReadEvidence> evidenceForConversation =
                verifiedCustomerReads.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (!evidenceForConversation.contains(evidence)) {
            evidenceForConversation.add(evidence);
        }
    }

    @Override
    public synchronized List<VerifiedCustomerReadEvidence> getVerifiedCustomerReads(UUID conversationId) {
        String key = requireConversation(conversationId);
        List<VerifiedCustomerReadEvidence> evidence = verifiedCustomerReads.get(key);
        return evidence == null ? List.of() : List.copyOf(evidence);
    }

    // --- Session Metadata ---

    @Override
    public synchronized void setSessionAttribute(UUID conversationId, String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Session attribute key is required.");
        }
        String convKey = requireConversation(conversationId);
        Map<String, Object> attrs = sessionAttributes.computeIfAbsent(convKey, k -> new LinkedHashMap<>());
        if (value == null) {
            attrs.remove(key);
        } else {
            attrs.put(key, value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <T> Optional<T> getSessionAttribute(UUID conversationId, String key, Class<T> type) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String convKey = requireConversation(conversationId);
        Map<String, Object> attrs = sessionAttributes.get(convKey);
        if (attrs == null) {
            return Optional.empty();
        }
        Object value = attrs.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    @Override
    public synchronized void removeSessionAttribute(UUID conversationId, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        String convKey = requireConversation(conversationId);
        Map<String, Object> attrs = sessionAttributes.get(convKey);
        if (attrs != null) {
            attrs.remove(key);
        }
    }

    @Override
    public synchronized void clearSessionAttributes(UUID conversationId) {
        String convKey = requireConversation(conversationId);
        Map<String, Object> attrs = sessionAttributes.get(convKey);
        if (attrs != null) {
            attrs.clear();
        }
    }

    private String requireConversation(UUID conversationId) {
        if (conversationId == null) {
            throw new IllegalArgumentException("Conversation id is required.");
        }
        String key = conversationId.toString();
        Instant now = clock.instant();
        purgeExpired(now);
        Instant lastAccess = activeConversations.get(key);
        if (lastAccess == null) {
            ConversationException.Kind kind = expiredConversations.containsKey(key)
                    ? ConversationException.Kind.EXPIRED
                    : ConversationException.Kind.UNKNOWN;
            throw new ConversationException(kind, "Conversation is no longer available.");
        }
        if (isExpired(lastAccess, now)) {
            expire(key, now);
            throw new ConversationException(
                    ConversationException.Kind.EXPIRED,
                    "Conversation is no longer available."
            );
        }
        return key;
    }

    private UUID create(Instant now) {
        while (activeConversations.size() >= maxConversations) {
            Map.Entry<String, Instant> oldest = activeConversations.entrySet().iterator().next();
            expire(oldest.getKey(), now);
        }
        UUID id = UUID.randomUUID();
        activeConversations.put(id.toString(), now);
        return id;
    }

    private UUID parseId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (IllegalArgumentException exception) {
            throw new ConversationException(
                    ConversationException.Kind.INVALID,
                    "Conversation id is invalid."
            );
        }
    }

    private void purgeExpired(Instant now) {
        List<String> expired = activeConversations.entrySet().stream()
                .filter(entry -> isExpired(entry.getValue(), now))
                .map(Map.Entry::getKey)
                .toList();
        expired.forEach(key -> expire(key, now));

        expiredConversations.entrySet().removeIf(entry ->
                entry.getValue().plus(EXPIRED_ID_TTL).isBefore(now)
        );
    }

    private boolean isExpired(Instant lastAccess, Instant now) {
        return !lastAccess.plus(conversationTtl).isAfter(now);
    }

    private void expire(String key, Instant now) {
        activeConversations.remove(key);
        chatMemory.clear(key);
        conversationArtifacts.remove(key);
        conversationExecutions.remove(key);
        verifiedCustomerReads.remove(key);
        sessionAttributes.remove(key);
        expiredConversations.put(key, now);
        while (expiredConversations.size() > MAX_EXPIRED_IDS) {
            expiredConversations.remove(expiredConversations.keySet().iterator().next());
        }
    }

    private String sanitize(String value) {
        String bounded = bound(value);
        return PHONE_PATTERN.matcher(EMAIL_PATTERN.matcher(bounded).replaceAll(REDACTED))
                .replaceAll(REDACTED);
    }

    private String bound(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_STORED_TURN_CHARACTERS
                ? value.substring(0, MAX_STORED_TURN_CHARACTERS)
                : value;
    }

    public static class ConversationException extends RuntimeException {

        private final Kind kind;

        public ConversationException(Kind kind, String message) {
            super(message);
            this.kind = kind;
        }

        public Kind kind() {
            return kind;
        }

        public enum Kind {
            INVALID,
            UNKNOWN,
            EXPIRED
        }
    }
}
