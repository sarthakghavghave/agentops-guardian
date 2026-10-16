import type { WorkflowTrajectoryEvent } from './audit'

export type { WorkflowTrajectoryEvent } from './audit'

export type AgentCoordinatorAction =
  | 'REPORT_REQUEST'
  | 'REPORT_METADATA_REQUEST'
  | 'EMAIL_REQUEST'
  | 'GENERAL_RESPONSE'
  | 'CLARIFICATION_REQUIRED'

export type ReportType = 'ANALYTICAL' | 'REDACTED_DETAIL'

export type AgentCoordinatorRequest = {
  message: string
  recipient?: string | null
  report?: {
    type: ReportType
    redacted: boolean
    customerCount: number
    content: string
  } | null
  conversationId?: string | null
  subjectInstructions?: string | null
  bodyInstructions?: string | null
}

export type AgentCoordinatorResponse = {
  action: AgentCoordinatorAction
  response: string
  clarificationRequired: boolean
  conversationId: string
  approvalRequired: boolean
  interventionId: string | null
}

export type WorkflowType = 'CUSTOMER_SUPPORT' | 'CUSTOMER_REPORTING' | 'EMAIL_COMMUNICATION'
export type WorkflowCapability =
  | 'READ_CUSTOMER_DATA'
  | 'READ_CONVERSATION_HISTORY'
  | 'READ_ORDER'
  | 'READ_PRODUCT'
  | 'GENERATE_REPORT'
  | 'SEND_EMAIL'
  | 'RETURN_ORDER'
export type WorkflowState =
  | 'STARTED'
  | 'DATA_ACQUIRED'
  | 'REPORT_GENERATED'
  | 'DELIVERY_REQUESTED'
  | 'COMPLETED'

export type GovernanceInterventionStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED'
export type RiskLevel = 'LOW' | 'MODERATE' | 'HIGH' | 'CRITICAL'
export type InterventionKind = 'NONE' | 'AUDIT' | 'REQUIRE_APPROVAL' | 'BLOCK'

export type GovernanceInterventionResponse = {
  interventionId: string
  workflowId: string
  toolCallId: string
  toolName: string | null
  agentName: string
  workflowType: WorkflowType
  capability: WorkflowCapability
  riskLevel: RiskLevel
  intervention: InterventionKind
  status: GovernanceInterventionStatus
  reason: string
  createdAt: string
  resolvedAt: string | null
  resolvedBy: string | null
  resolutionReason: string | null
  conversationId: string | null
  taskStatus: string | null
  taskMessage: string | null
}

export type InterventionResolutionRequest = {
  resolvedBy: string
  resolutionReason: string
}

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

export type WorkflowSummaryResponse = {
  workflowId: string
  conversationId: string | null
  agentName: string
  workflowType: WorkflowType
  startedAt: string
  lastEventAt: string
  currentNodeId: string | null
  currentState: WorkflowState | null
  eventCount: number
}

export type WorkflowTrajectoryResponse = WorkflowSummaryResponse & {
  events: WorkflowTrajectoryEvent[]
}

export type GovernanceEventStreamPayload = {
  eventId: number
  conversationId: string | null
  workflowId: string
  agentName: string
  workflowType: WorkflowType
  event: WorkflowTrajectoryEvent
}
