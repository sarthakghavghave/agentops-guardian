export type WorkflowType = 'CUSTOMER_SUPPORT' | 'CUSTOMER_REPORTING' | 'EMAIL_COMMUNICATION'

export type WorkflowState =
  | 'STARTED'
  | 'DATA_ACQUIRED'
  | 'REPORT_GENERATED'
  | 'DELIVERY_REQUESTED'
  | 'COMPLETED'

export type WorkflowCapability =
  | 'READ_CONVERSATION_HISTORY'
  | 'READ_CUSTOMER_DATA'
  | 'READ_ORDER'
  | 'READ_PRODUCT'
  | 'GENERATE_REPORT'
  | 'SEND_EMAIL'
  | 'RETURN_ORDER'

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
  allowedActionCount?: number
  blockedActionCount?: number
}

export type WorkflowTrajectoryResponse = WorkflowSummaryResponse & {
  events: WorkflowTrajectoryEvent[]
}

export type WorkflowTrajectoryEvent = import('./audit').WorkflowTrajectoryEvent
