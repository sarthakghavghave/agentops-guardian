package com.agentops.guardian.controller;

import com.agentops.guardian.governance.intervention.InterventionService;
import com.agentops.guardian.governance.intervention.dto.GovernanceInterventionResponse;
import com.agentops.guardian.governance.intervention.dto.InterventionResolutionRequest;
import jakarta.validation.Valid;
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

    public InterventionController(InterventionService interventionService) {
        this.interventionService = interventionService;
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
        return GovernanceInterventionResponse.from(interventionService.approve(
                interventionId,
                request.resolvedBy(),
                request.resolutionReason()
        ));
    }

    @PostMapping("/{interventionId}/reject")
    public GovernanceInterventionResponse reject(
            @PathVariable String interventionId,
            @Valid @RequestBody InterventionResolutionRequest request
    ) {
        return GovernanceInterventionResponse.from(interventionService.reject(
                interventionId,
                request.resolvedBy(),
                request.resolutionReason()
        ));
    }
}