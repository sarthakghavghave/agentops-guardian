import type { GovernanceInterventionKind, GovernanceInterventionStatus } from './intervention'
import type { RiskLevel } from './policy'
import type { WorkflowCapability, WorkflowState, WorkflowType } from './workflow'

export type AuditEventType =
  | 'PROPOSED_ACTION'
  | 'POLICY_DECISION'
  | 'RISK_ASSESSMENT'
  | 'INTERVENTION_CREATED'
  | 'INTERVENTION_APPROVED'
  | 'INTERVENTION_REJECTED'
  | 'INTERVENTION_EXPIRED'
  | 'EXECUTION_OUTCOME'
  | 'WORKFLOW_TRANSITION'

export type DataClassification =
  | 'RAW_CUSTOMER_DATA'
  | 'ANALYTICAL'
  | 'REDACTED_DETAIL'

export type GovernanceDecision = 'ALLOW' | 'BLOCK'
export type ExecutionStatus = 'SUCCESS' | 'FAILED' | 'BLOCKED'

export type WorkflowTrajectoryEvent = {
  timestamp: string
  eventType: AuditEventType
  toolCallId: string | null
  toolName: string | null
  capability: WorkflowCapability | null
  sequence: number | null
  policyDecision: GovernanceDecision | null
  policyId: string | null
  policyReason: string | null
  executionStatus: ExecutionStatus | null
  errorType: string | null
  errorMessage: string | null
  nodeBeforeId: string | null
  nodeBeforeState: WorkflowState | null
  nodeBeforeCapability: WorkflowCapability | null
  nodeAfterId: string | null
  nodeAfterState: WorkflowState | null
  nodeAfterCapability: WorkflowCapability | null
  stateBefore: WorkflowState | null
  stateAfter: WorkflowState | null
  classificationBefore: DataClassification | null
  classificationAfter: DataClassification | null
  transformationType: string | null
  riskLevel: RiskLevel | null
  intervention: GovernanceInterventionKind | null
  riskReason: string | null
  interventionId: string | null
  interventionStatus: GovernanceInterventionStatus | null
  resolvedBy: string | null
  resolutionReason: string | null
}

export type AuditEvent = WorkflowTrajectoryEvent & {
  workflowId: string
  agentName: string
  workflowType: WorkflowType
}
