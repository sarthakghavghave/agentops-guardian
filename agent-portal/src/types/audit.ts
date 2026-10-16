import type {
  AuditEventType,
  WorkflowCapability,
  WorkflowState,
} from './api'

export type WorkflowTrajectoryEvent = {
  timestamp: string
  eventType: AuditEventType
  toolCallId: string | null
  toolName: string | null
  capability: WorkflowCapability | null
  sequence: number | null
  policyDecision: 'ALLOW' | 'BLOCK' | null
  policyId: string | null
  policyReason: string | null
  executionStatus: 'SUCCESS' | 'FAILED' | 'BLOCKED' | null
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
  classificationBefore: 'RAW_CUSTOMER_DATA' | 'ANALYTICAL' | 'REDACTED_DETAIL' | null
  classificationAfter: 'RAW_CUSTOMER_DATA' | 'ANALYTICAL' | 'REDACTED_DETAIL' | null
  transformationType: string | null
  riskLevel: 'LOW' | 'MODERATE' | 'HIGH' | 'CRITICAL' | null
  intervention: 'NONE' | 'AUDIT' | 'REQUIRE_APPROVAL' | 'BLOCK' | null
  riskReason: string | null
  interventionId: string | null
  interventionStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | null
  resolvedBy: string | null
  resolutionReason: string | null
}
