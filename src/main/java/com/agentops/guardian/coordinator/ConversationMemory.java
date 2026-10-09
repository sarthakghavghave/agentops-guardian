package com.agentops.guardian.coordinator;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import com.agentops.guardian.tool.ReportTools;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConversationMemory {

    private static final int MAX_MESSAGES_PER_CONVERSATION = 12;
    private static final int MAX_STORED_TURN_CHARACTERS = 4000;
    private static final int MAX_PENDING_REPORT_CHARACTERS = 30000;
    private static final int MAX_CONVERSATIONS = 500;
    private static final int MAX_EXPIRED_IDS = 1000;
    private static final Duration CONVERSATION_TTL = Duration.ofMinutes(30);
    private static final Duration EXPIRED_ID_TTL = Duration.ofHours(1);
    private static final String REDACTED = "[redacted]";
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\w)(?:\\+?\\d[\\d ().-]{7,}\\d)(?!\\w)");
    private static final Pattern EMAIL_EXTRACT_PATTERN =
            Pattern.compile("(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![A-Z0-9._%+-])",
                    Pattern.CASE_INSENSITIVE);

    private final ChatMemory chatMemory;
    private final Clock clock;
    private final Duration conversationTtl;
    private final int maxConversations;
    private final LinkedHashMap<String, Instant> activeConversations = new LinkedHashMap<>(16, 0.75f, true);
    private final LinkedHashMap<String, Instant> expiredConversations = new LinkedHashMap<>();
    private final Map<String, ReportTools.Report> pendingEmailReports = new LinkedHashMap<>();

    public ConversationMemory() {
        this(
                MessageWindowChatMemory.builder()
                        .chatMemoryRepository(new InMemoryChatMemoryRepository())
                        .maxMessages(MAX_MESSAGES_PER_CONVERSATION)
                        .build(),
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

    public synchronized List<Message> history(UUID conversationId) {
        String key = requireConversation(conversationId);
        return List.copyOf(chatMemory.get(key));
    }

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
            turn.add(new AssistantMessage(sanitize(assistantContext)));
        }
        chatMemory.add(key, turn);
    }

    public synchronized void rememberPendingEmailReport(UUID conversationId, ReportTools.Report report) {
        if (report == null || report.content() == null || report.content().isBlank()) {
            throw new IllegalArgumentException("A complete report is required for a pending email.");
        }
        String key = requireConversation(conversationId);
        if (report.content().length() > MAX_PENDING_REPORT_CHARACTERS) {
            throw new IllegalArgumentException("Report content exceeds the pending conversation limit.");
        }
        pendingEmailReports.put(key, report);
    }

    public synchronized ReportTools.Report pendingEmailReport(UUID conversationId) {
        String key = requireConversation(conversationId);
        return pendingEmailReports.get(key);
    }

    public synchronized void clearPendingEmailReport(UUID conversationId) {
        String key = requireConversation(conversationId);
        pendingEmailReports.remove(key);
    }

    public synchronized String extractSingleEmailAddress(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = EMAIL_EXTRACT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String address = matcher.group();
        return matcher.find() ? null : address;
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
        pendingEmailReports.remove(key);
        expiredConversations.put(key, now);
        while (expiredConversations.size() > MAX_EXPIRED_IDS) {
            expiredConversations.remove(expiredConversations.keySet().iterator().next());
        }
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String bounded = value.length() > MAX_STORED_TURN_CHARACTERS
                ? value.substring(0, MAX_STORED_TURN_CHARACTERS)
                : value;
        return PHONE_PATTERN.matcher(EMAIL_PATTERN.matcher(bounded).replaceAll(REDACTED))
                .replaceAll(REDACTED);
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
