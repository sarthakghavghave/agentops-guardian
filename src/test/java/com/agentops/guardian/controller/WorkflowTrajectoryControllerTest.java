package com.agentops.guardian.controller;

import com.agentops.guardian.governance.audit.AuditEventType;
import com.agentops.guardian.governance.audit.dto.WorkflowSummaryResponse;
import com.agentops.guardian.governance.audit.dto.WorkflowTrajectoryEvent;
import com.agentops.guardian.governance.audit.dto.WorkflowTrajectoryResponse;
import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.service.WorkflowTrajectoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkflowTrajectoryController.class)
class WorkflowTrajectoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkflowTrajectoryService trajectoryService;

    @Test
    void returnsWorkflowSummaries() throws Exception {
        when(trajectoryService.getSummaries()).thenReturn(List.of(summary()));

        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workflowId").value("workflow-1"))
                .andExpect(jsonPath("$[0].eventCount").value(1));
    }

    @Test
    void returnsWorkflowTrajectory() throws Exception {
        WorkflowSummaryResponse summary = summary();
        WorkflowTrajectoryEvent event = new WorkflowTrajectoryEvent(
                Instant.parse("2026-10-01T10:00:00Z"),
                AuditEventType.EXECUTION_OUTCOME,
                "call-1",
                "generateReport",
                WorkflowCapability.GENERATE_REPORT,
                1,
                null,
                null,
                null,
                com.agentops.guardian.governance.audit.AuditEvent.ExecutionStatus.SUCCESS,
                null,
                null,
                "CUSTOMER_DATA",
                WorkflowState.DATA_ACQUIRED,
                WorkflowCapability.GENERATE_REPORT,
                "REPORT",
                WorkflowState.REPORT_GENERATED,
                WorkflowCapability.GENERATE_REPORT,
                WorkflowState.DATA_ACQUIRED,
                WorkflowState.REPORT_GENERATED,
                DataClassification.RAW_CUSTOMER_DATA,
                DataClassification.ANALYTICAL,
                "ANALYTICAL",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(trajectoryService.getTrajectory("workflow-1")).thenReturn(
                new WorkflowTrajectoryResponse(
                        summary.workflowId(), summary.agentName(), summary.workflowType(),
                        summary.startedAt(), summary.lastEventAt(), summary.currentNodeId(),
                        summary.currentState(), summary.eventCount(), List.of(event)
                )
        );

        mockMvc.perform(get("/api/workflows/workflow-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value("workflow-1"))
                .andExpect(jsonPath("$.events[0].classificationBefore").value("RAW_CUSTOMER_DATA"))
                .andExpect(jsonPath("$.events[0].classificationAfter").value("ANALYTICAL"))
                .andExpect(jsonPath("$.events[0].transformationType").value("ANALYTICAL"));
    }

    private WorkflowSummaryResponse summary() {
        Instant at = Instant.parse("2026-10-01T10:00:00Z");
        return new WorkflowSummaryResponse(
                "workflow-1",
                "ReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                at,
                at,
                "REPORT",
                WorkflowState.REPORT_GENERATED,
                1
        );
    }
}