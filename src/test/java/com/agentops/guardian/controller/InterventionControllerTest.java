package com.agentops.guardian.controller;

import com.agentops.guardian.coordinator.AgentCoordinator;
import com.agentops.guardian.coordinator.PendingAgentTaskRegistry;
import com.agentops.guardian.governance.intervention.GovernanceIntervention;
import com.agentops.guardian.governance.intervention.GovernanceInterventionStatus;
import com.agentops.guardian.governance.intervention.InterventionService;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterventionController.class)
class InterventionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterventionService interventionService;

    @MockitoBean
    private AgentCoordinator coordinator;

    @MockitoBean
    private PendingAgentTaskRegistry pendingAgentTaskRegistry;

    @Test
    void retrievesListAndSingleInterventionAsDtos() throws Exception {
        GovernanceIntervention pending = intervention(GovernanceInterventionStatus.PENDING);
        when(interventionService.list()).thenReturn(List.of(pending));
        when(interventionService.get("intervention-1")).thenReturn(pending);

        mockMvc.perform(get("/api/interventions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].interventionId").value("intervention-1"))
                .andExpect(jsonPath("$[0].toolCallId").value("call-1"));
        mockMvc.perform(get("/api/interventions/intervention-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void approvesAndRejectsInterventions() throws Exception {
        when(interventionService.approve(eq("intervention-1"), eq("reviewer"), eq("Approved.")))
                .thenReturn(intervention(GovernanceInterventionStatus.APPROVED));
        when(interventionService.reject(eq("intervention-1"), eq("reviewer"), eq("Rejected.")))
                .thenReturn(intervention(GovernanceInterventionStatus.REJECTED));
        when(coordinator.resumeApproved(any())).thenReturn(
                new AgentCoordinator.TaskResumeResult("COMPLETED", "Email completed.", null)
        );

        mockMvc.perform(post("/api/interventions/intervention-1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolvedBy\":\"reviewer\",\"resolutionReason\":\"Approved.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.taskStatus").value("COMPLETED"));
        verify(coordinator).resumeApproved(any());
        mockMvc.perform(post("/api/interventions/intervention-1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolvedBy\":\"reviewer\",\"resolutionReason\":\"Rejected.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.taskStatus").value("TERMINATED"));
        verify(pendingAgentTaskRegistry).terminate("intervention-1");
    }

    private GovernanceIntervention intervention(GovernanceInterventionStatus status) {
        boolean resolved = status != GovernanceInterventionStatus.PENDING;
        return new GovernanceIntervention(
                "intervention-1",
                "workflow-1",
                "call-1",
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                WorkflowCapability.SEND_EMAIL,
                RiskLevel.HIGH,
                com.agentops.guardian.governance.risk.GovernanceIntervention.REQUIRE_APPROVAL,
                status,
                "External communication requires review.",
                Instant.parse("2026-10-03T10:00:00Z"),
                resolved ? Instant.parse("2026-10-03T10:05:00Z") : null,
                resolved ? "reviewer" : null,
                resolved ? "Resolved." : null,
                "REPORT",
                com.agentops.guardian.governance.context.WorkflowState.REPORT_GENERATED,
                com.agentops.guardian.governance.model.DataClassification.ANALYTICAL
        );
    }
}