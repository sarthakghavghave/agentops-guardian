package com.agentops.guardian.coordinator;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMemoryTest {

    @Test
    void opensNewConversationAndKeepsItsHistoryScopedToItsId() {
        ConversationMemory memory = newMemory(new MutableClock(Instant.parse("2026-10-09T12:00:00Z")), 10);
        UUID first = memory.open(null);
        UUID second = memory.open(null);

        memory.recordTurn(first, "Generate report for Boston", "Which date range?");

        assertNotEquals(first, second);
        assertEquals(2, memory.history(first).size());
        assertTrue(memory.history(second).isEmpty());
    }

    @Test
    void unknownAndMalformedConversationIdsAreRejectedExplicitly() {
        ConversationMemory memory = newMemory(new MutableClock(Instant.parse("2026-10-09T12:00:00Z")), 10);

        ConversationMemory.ConversationException unknown = assertThrows(
                ConversationMemory.ConversationException.class,
                () -> memory.open(UUID.randomUUID().toString())
        );
        ConversationMemory.ConversationException invalid = assertThrows(
                ConversationMemory.ConversationException.class,
                () -> memory.open("not-a-uuid")
        );

        assertEquals(ConversationMemory.ConversationException.Kind.UNKNOWN, unknown.kind());
        assertEquals(ConversationMemory.ConversationException.Kind.INVALID, invalid.kind());
    }

    @Test
    void expiredConversationIsDistinctFromUnknownConversation() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-09T12:00:00Z"));
        ConversationMemory memory = newMemory(clock, 10);
        UUID conversationId = memory.open(null);
        memory.recordTurn(conversationId, "Question", null);
        clock.advance(Duration.ofMinutes(11));

        ConversationMemory.ConversationException expired = assertThrows(
                ConversationMemory.ConversationException.class,
                () -> memory.open(conversationId.toString())
        );
        assertEquals(ConversationMemory.ConversationException.Kind.EXPIRED, expired.kind());

        clock.advance(Duration.ofHours(2));
        ConversationMemory.ConversationException noLongerKnown = assertThrows(
                ConversationMemory.ConversationException.class,
                () -> memory.open(conversationId.toString())
        );
        assertEquals(ConversationMemory.ConversationException.Kind.UNKNOWN, noLongerKnown.kind());
    }

    @Test
    void evictsOldestConversationWhenSessionCapacityIsReached() {
        ConversationMemory memory = newMemory(new MutableClock(Instant.parse("2026-10-09T12:00:00Z")), 1);
        UUID oldest = memory.open(null);
        memory.open(null);

        ConversationMemory.ConversationException expired = assertThrows(
                ConversationMemory.ConversationException.class,
                () -> memory.open(oldest.toString())
        );
        assertEquals(ConversationMemory.ConversationException.Kind.EXPIRED, expired.kind());
    }

    @Test
    void limitsHistoryAndRedactsEmailPhoneAndStructuredReportDataIsNotStored() {
        ConversationMemory memory = newMemory(new MutableClock(Instant.parse("2026-10-09T12:00:00Z")), 10);
        UUID conversationId = memory.open(null);
        for (int turn = 0; turn < 8; turn++) {
            memory.recordTurn(
                    conversationId,
                    "Turn " + turn + " contact person@example.com +1 555-123-4567",
                    "Clarification " + turn
            );
        }

        List<Message> history = memory.history(conversationId);
        assertEquals(12, history.size());
        assertFalse(history.getFirst().getText().contains("Turn 0"));
        assertTrue(history.stream().anyMatch(message -> message.getText().contains("Turn 7")));
        assertTrue(history.stream().noneMatch(message -> message.getText().contains("person@example.com")));
        assertTrue(history.stream().noneMatch(message -> message.getText().contains("+1 555-123-4567")));
    }

    @Test
    void boundsEachStoredMessageLength() {
        ConversationMemory memory = newMemory(new MutableClock(Instant.parse("2026-10-09T12:00:00Z")), 10);
        UUID conversationId = memory.open(null);

        memory.recordTurn(conversationId, "x".repeat(5000), null);

        assertEquals(4000, memory.history(conversationId).getFirst().getText().length());
    }

    private ConversationMemory newMemory(MutableClock clock, int maxConversations) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(12)
                .build();
        return new ConversationMemory(chatMemory, clock, Duration.ofMinutes(10), maxConversations);
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
