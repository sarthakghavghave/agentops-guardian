package com.agentops.guardian.governance.audit;

import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.risk.GovernanceIntervention;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.intervention.GovernanceInterventionStatus;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
		name = "audit_events",
		indexes = {
				@Index(name = "idx_audit_events_workflow_time", columnList = "workflow_id, event_timestamp"),
				@Index(name = "idx_audit_events_tool_call", columnList = "tool_call_id")
		}
)
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEventEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "workflow_id", nullable = false, length = 100)
	private String workflowId;

	@Column(name = "agent_name", nullable = false, length = 150)
	private String agentName;

	@Enumerated(EnumType.STRING)
	@Column(name = "workflow_type", nullable = false, length = 50)
	private WorkflowType workflowType;

	@Column(name = "event_timestamp", nullable = false)
	private Instant timestamp;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 50)
	private AuditEventType eventType;

	@Column(name = "tool_call_id", length = 200)
	private String toolCallId;

	@Column(name = "tool_name", length = 200)
	private String toolName;

	@Enumerated(EnumType.STRING)
	@Column(length = 80)
	private WorkflowCapability capability;

	@Column(name = "action_sequence")
	private Integer sequence;

	@Enumerated(EnumType.STRING)
	@Column(name = "policy_decision", length = 30)
	private GovernanceDecision.DecisionType policyDecision;

	@Column(name = "policy_id", length = 100)
	private String policyId;

	@Column(name = "policy_reason", columnDefinition = "text")
	private String policyReason;

	@Enumerated(EnumType.STRING)
	@Column(name = "execution_status", length = 30)
	private AuditEvent.ExecutionStatus executionStatus;

	@Column(name = "error_type", length = 300)
	private String errorType;

	@Column(name = "error_message", columnDefinition = "text")
	private String errorMessage;

	@Column(name = "node_before_id", length = 150)
	private String nodeBeforeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "node_before_state", length = 50)
	private WorkflowState nodeBeforeState;

	@Enumerated(EnumType.STRING)
	@Column(name = "node_before_capability", length = 80)
	private WorkflowCapability nodeBeforeCapability;

	@Column(name = "node_after_id", length = 150)
	private String nodeAfterId;

	@Enumerated(EnumType.STRING)
	@Column(name = "node_after_state", length = 50)
	private WorkflowState nodeAfterState;

	@Enumerated(EnumType.STRING)
	@Column(name = "node_after_capability", length = 80)
	private WorkflowCapability nodeAfterCapability;

	@Enumerated(EnumType.STRING)
	@Column(name = "state_before", length = 50)
	private WorkflowState stateBefore;

	@Enumerated(EnumType.STRING)
	@Column(name = "state_after", length = 50)
	private WorkflowState stateAfter;

	@Enumerated(EnumType.STRING)
	@Column(name = "classification_before", length = 50)
	private DataClassification classificationBefore;

	@Enumerated(EnumType.STRING)
	@Column(name = "classification_after", length = 50)
	private DataClassification classificationAfter;

	@Column(name = "transformation_type", length = 100)
	private String transformationType;

	@Enumerated(EnumType.STRING)
	@Column(name = "risk_level", length = 30)
	private RiskLevel riskLevel;

	@Enumerated(EnumType.STRING)
	@Column(name = "intervention", length = 40)
	private GovernanceIntervention intervention;

	@Column(name = "risk_reason", columnDefinition = "text")
	private String riskReason;

	@Column(name = "intervention_id", length = 36)
	private String interventionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "intervention_status", length = 30)
	private GovernanceInterventionStatus interventionStatus;

	@Column(name = "intervention_resolved_by", length = 150)
	private String interventionResolvedBy;

	@Column(name = "intervention_resolution_reason", columnDefinition = "text")
	private String interventionResolutionReason;

	static AuditEventEntity from(AuditEvent event) {
		AuditEventEntity entity = new AuditEventEntity();
		entity.workflowId = event.workflowId();
		entity.agentName = event.agentName();
		entity.workflowType = event.workflowType();
		entity.timestamp = event.timestamp();
		entity.eventType = event.eventType();
		entity.toolCallId = event.toolCallId();
		entity.toolName = event.toolName();
		entity.capability = event.capability();
		entity.sequence = event.sequence();
		entity.policyDecision = event.policyDecision();
		entity.policyId = event.policyId();
		entity.policyReason = event.policyReason();
		entity.executionStatus = event.executionStatus();
		entity.errorType = event.errorType();
		entity.errorMessage = event.errorMessage();
		if (event.nodeBefore() != null) {
			entity.nodeBeforeId = event.nodeBefore().nodeId();
			entity.nodeBeforeState = event.nodeBefore().state();
			entity.nodeBeforeCapability = event.nodeBefore().capability();
		}
		if (event.nodeAfter() != null) {
			entity.nodeAfterId = event.nodeAfter().nodeId();
			entity.nodeAfterState = event.nodeAfter().state();
			entity.nodeAfterCapability = event.nodeAfter().capability();
		}
		entity.stateBefore = event.stateBefore();
		entity.stateAfter = event.stateAfter();
		entity.classificationBefore = event.classificationBefore();
		entity.classificationAfter = event.classificationAfter();
		entity.transformationType = event.transformationType();
		entity.riskLevel = event.riskLevel();
		entity.intervention = event.intervention();
		entity.riskReason = event.riskReason();
		entity.interventionId = event.interventionId();
		entity.interventionStatus = event.interventionStatus();
		entity.interventionResolvedBy = event.resolvedBy();
		entity.interventionResolutionReason = event.resolutionReason();
		return entity;
	}
}
