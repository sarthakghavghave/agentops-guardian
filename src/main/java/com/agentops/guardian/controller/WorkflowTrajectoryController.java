package com.agentops.guardian.controller;

import com.agentops.guardian.governance.audit.dto.WorkflowSummaryResponse;
import com.agentops.guardian.governance.audit.dto.WorkflowTrajectoryResponse;
import com.agentops.guardian.service.WorkflowTrajectoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowTrajectoryController {

    private final WorkflowTrajectoryService trajectoryService;

    public WorkflowTrajectoryController(WorkflowTrajectoryService trajectoryService) {
        this.trajectoryService = trajectoryService;
    }

    @GetMapping
    public List<WorkflowSummaryResponse> getSummaries() {
        return trajectoryService.getSummaries();
    }

    @GetMapping("/{workflowId}")
    public WorkflowTrajectoryResponse getTrajectory(@PathVariable String workflowId) {
        return trajectoryService.getTrajectory(workflowId);
    }
}