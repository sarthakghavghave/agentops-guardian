export type WorkflowType = 'CUSTOMER_SUPPORT' | 'CUSTOMER_REPORTING'

export type WorkflowState =
  | 'STARTED'
  | 'DATA_ACQUIRED'
  | 'REPORT_GENERATED'
  | 'DELIVERY_REQUESTED'
  | 'COMPLETED'

export type WorkflowCapability =
  | 'READ_CUSTOMER_DATA'
  | 'READ_ORDER'
  | 'READ_PRODUCT'
  | 'GENERATE_REPORT'
  | 'SEND_EMAIL'
  | 'RETURN_ORDER'

export type WorkflowSummaryResponse = {
  workflowId: string
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

export type WorkflowTrajectoryEvent = import('./audit').WorkflowTrajectoryEvent
