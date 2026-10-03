package com.agentops.guardian.governance.intervention;

import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "governance_interventions",
        indexes = {
                @Index(name = "idx_gov_interventions_workflow", columnList = "workflow_id"),
                @Index(name = "idx_gov_interventions_tool_call", columnList = "tool_call_id"),
                @Index(name = "idx_gov_interventions_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GovernanceInterventionEntity {

    @Id
    @Column(name = "intervention_id", length = 36, nullable = false, updatable = false)
    private String interventionId;

    @Column(name = "workflow_id", nullable = false, length = 100, updatable = false)
    private String workflowId;

    @Column(name = "tool_call_id", nullable = false, length = 200, updatable = false)
    private String toolCallId;

    @Column(name = "agent_name", nullable = false, length = 150, updatable = false)
    private String agentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_type", nullable = false, length = 50, updatable = false)
    private WorkflowType workflowType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80, updatable = false)
    private WorkflowCapability capability;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 30, updatable = false)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "intervention_type", nullable = false, length = 40, updatable = false)
    private com.agentops.guardian.governance.risk.GovernanceIntervention intervention;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GovernanceInterventionStatus status;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 150)
    private String resolvedBy;

    @Column(name = "resolution_reason", columnDefinition = "text")
    private String resolutionReason;

    @Column(name = "workflow_node_id", nullable = false, length = 150, updatable = false)
    private String workflowNodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_state", nullable = false, length = 50, updatable = false)
    private WorkflowState workflowState;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_at_creation", length = 50, updatable = false)
    private DataClassification classificationAtCreation;

    @Version
    private Long version;

    static GovernanceInterventionEntity from(GovernanceIntervention intervention) {
        GovernanceInterventionEntity entity = new GovernanceInterventionEntity();
        entity.updateFrom(intervention);
        return entity;
    }

    void updateFrom(GovernanceIntervention intervention) {
        interventionId = intervention.interventionId();
        workflowId = intervention.workflowId();
        toolCallId = intervention.toolCallId();
        agentName = intervention.agentName();
        workflowType = intervention.workflowType();
        capability = intervention.capability();
        riskLevel = intervention.riskLevel();
        this.intervention = intervention.intervention();
        status = intervention.status();
        reason = intervention.reason();
        createdAt = intervention.createdAt();
        resolvedAt = intervention.resolvedAt();
        resolvedBy = intervention.resolvedBy();
        resolutionReason = intervention.resolutionReason();
        workflowNodeId = intervention.workflowNodeId();
        workflowState = intervention.workflowState();
        classificationAtCreation = intervention.classificationAtCreation();
    }

    GovernanceIntervention toDomain() {
        return new GovernanceIntervention(
                interventionId,
                workflowId,
                toolCallId,
                agentName,
                workflowType,
                capability,
                riskLevel,
                intervention,
                status,
                reason,
                createdAt,
                resolvedAt,
                resolvedBy,
                resolutionReason,
                workflowNodeId,
                workflowState,
                classificationAtCreation
        );
    }
}