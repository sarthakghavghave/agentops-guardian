package com.agentops.guardian.controller;

import com.agentops.guardian.coordinator.AgentCoordinator;
import com.agentops.guardian.coordinator.ConversationMemory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentCoordinatorControllerTest {

    private final AgentCoordinatorController controller =
            new AgentCoordinatorController(mock(AgentCoordinator.class));

    @Test
    void returnsDistinctHttpStatusesForInvalidUnknownAndExpiredIds() {
        assertEquals(
                HttpStatus.BAD_REQUEST,
                controller.handleConversationError(new ConversationMemory.ConversationException(
                        ConversationMemory.ConversationException.Kind.INVALID,
                        "Conversation id is invalid."
                )).getStatusCode()
        );
        assertEquals(
                HttpStatus.NOT_FOUND,
                controller.handleConversationError(new ConversationMemory.ConversationException(
                        ConversationMemory.ConversationException.Kind.UNKNOWN,
                        "Conversation was not found."
                )).getStatusCode()
        );
        assertEquals(
                HttpStatus.GONE,
                controller.handleConversationError(new ConversationMemory.ConversationException(
                        ConversationMemory.ConversationException.Kind.EXPIRED,
                        "Conversation has expired."
                )).getStatusCode()
        );
    }
}
