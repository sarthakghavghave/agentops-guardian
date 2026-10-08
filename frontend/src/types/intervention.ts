import type { RiskLevel } from './policy'
import type { WorkflowCapability, WorkflowType } from './workflow'

export type GovernanceInterventionStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXPIRED'

export type GovernanceInterventionKind =
  | 'NONE'
  | 'AUDIT'
  | 'REQUIRE_APPROVAL'
  | 'BLOCK'

export type GovernanceInterventionResponse = {
  interventionId: string
  workflowId: string
  toolCallId: string
  agentName: string
  workflowType: WorkflowType
  capability: WorkflowCapability
  riskLevel: RiskLevel
  intervention: GovernanceInterventionKind
  status: GovernanceInterventionStatus
  reason: string
  createdAt: string
  resolvedAt: string | null
  resolvedBy: string | null
  resolutionReason: string | null
}

export type InterventionResolutionRequest = {
  resolvedBy: string
  resolutionReason: string
}

export type GovernanceIntervention = GovernanceInterventionResponse
