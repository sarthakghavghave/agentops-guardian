package com.agentops.guardian.controller;

import com.agentops.guardian.coordinator.AgentCoordinator;
import com.agentops.guardian.coordinator.PendingAgentTaskRegistry;
import com.agentops.guardian.governance.intervention.InterventionService;
import com.agentops.guardian.governance.intervention.GovernanceIntervention;
import com.agentops.guardian.governance.intervention.dto.GovernanceInterventionResponse;
import com.agentops.guardian.governance.intervention.dto.InterventionResolutionRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interventions")
public class InterventionController {

    private final InterventionService interventionService;
    private final AgentCoordinator coordinator;
    private final PendingAgentTaskRegistry pendingAgentTaskRegistry;

    @Autowired
    public InterventionController(
            InterventionService interventionService,
            ObjectProvider<AgentCoordinator> coordinator,
            ObjectProvider<PendingAgentTaskRegistry> pendingAgentTaskRegistry
    ) {
        this.interventionService = interventionService;
        this.coordinator = coordinator.getIfAvailable();
        this.pendingAgentTaskRegistry = pendingAgentTaskRegistry.getIfAvailable();
    }

    public InterventionController(InterventionService interventionService) {
        this.interventionService = interventionService;
        this.coordinator = null;
        this.pendingAgentTaskRegistry = null;
    }

    @GetMapping
    public List<GovernanceInterventionResponse> list() {
        return interventionService.list().stream()
                .map(GovernanceInterventionResponse::from)
                .toList();
    }

    @GetMapping("/{interventionId}")
    public GovernanceInterventionResponse get(@PathVariable String interventionId) {
        return GovernanceInterventionResponse.from(interventionService.get(interventionId));
    }

    @PostMapping("/{interventionId}/approve")
    public GovernanceInterventionResponse approve(
            @PathVariable String interventionId,
            @Valid @RequestBody InterventionResolutionRequest request
    ) {
        GovernanceIntervention intervention = interventionService.approve(
                interventionId,
                request.resolvedBy(),
                request.resolutionReason()
        );
        if (coordinator == null) {
            return GovernanceInterventionResponse.from(intervention);
        }
        AgentCoordinator.TaskResumeResult resumeResult = coordinator.resumeApproved(intervention);
        return GovernanceInterventionResponse.from(intervention)
                .withTaskResult(resumeResult.status(), resumeResult.message());
    }

    @PostMapping("/{interventionId}/reject")
    public GovernanceInterventionResponse reject(
            @PathVariable String interventionId,
            @Valid @RequestBody InterventionResolutionRequest request
    ) {
        GovernanceIntervention intervention = interventionService.reject(
                interventionId,
                request.resolvedBy(),
                request.resolutionReason()
        );
        if (pendingAgentTaskRegistry != null) {
            pendingAgentTaskRegistry.terminate(interventionId);
        }
        return GovernanceInterventionResponse.from(intervention).withTaskResult(
                "TERMINATED",
                "The pending task was rejected and cannot be resumed."
        );
    }
}