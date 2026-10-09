package com.agentops.guardian.controller;

import com.agentops.guardian.coordinator.AgentCoordinator;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorResponse;
import jakarta.validation.Valid;
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
}
