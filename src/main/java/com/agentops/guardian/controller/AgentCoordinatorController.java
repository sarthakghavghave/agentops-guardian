package com.agentops.guardian.controller;

import com.agentops.guardian.coordinator.AgentCoordinator;
import com.agentops.guardian.coordinator.ConversationMemory;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-coordinator")
public class AgentCoordinatorController {

    private final AgentCoordinator coordinator;

    public AgentCoordinatorController(AgentCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @PostMapping("/run")
    public AgentCoordinatorResponse run(@Valid @RequestBody AgentCoordinatorRequest request) {
        return coordinator.handle(request);
    }

    @ExceptionHandler(ConversationMemory.ConversationException.class)
    public ResponseEntity<ConversationErrorResponse> handleConversationError(
            ConversationMemory.ConversationException exception
    ) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case UNKNOWN -> HttpStatus.NOT_FOUND;
            case EXPIRED -> HttpStatus.GONE;
        };
        return ResponseEntity.status(status).body(new ConversationErrorResponse(exception.getMessage()));
    }

    public record ConversationErrorResponse(String error) {
    }
}
