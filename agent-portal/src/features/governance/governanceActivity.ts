import type {
  AuditEventType,
  GovernanceEventStreamPayload,
  WorkflowCapability,
  WorkflowState,
  WorkflowTrajectoryEvent,
  WorkflowType,
} from '../../types/api'

const auditEventTypes = [
  'PROPOSED_ACTION',
  'POLICY_DECISION',
  'RISK_ASSESSMENT',
  'INTERVENTION_CREATED',
  'INTERVENTION_APPROVED',
  'INTERVENTION_REJECTED',
  'INTERVENTION_EXPIRED',
  'EXECUTION_OUTCOME',
  'WORKFLOW_TRANSITION',
] as const satisfies readonly AuditEventType[]

const workflowTypes = [
  'CUSTOMER_SUPPORT',
  'CUSTOMER_REPORTING',
  'EMAIL_COMMUNICATION',
] as const satisfies readonly WorkflowType[]

export type WorkflowActivityGroup = {
  workflowId: string
  conversationId: string | null
  agentName: string
  workflowType: WorkflowType
  events: GovernanceEventStreamPayload[]
}

export function getLatestGovernanceEvent(
  events: GovernanceEventStreamPayload[],
): GovernanceEventStreamPayload | null {
  return events.reduce<GovernanceEventStreamPayload | null>(
    (latest, event) => latest === null || event.eventId > latest.eventId ? event : latest,
    null,
  )
}

export function getVisibleGovernanceEvents(
  events: GovernanceEventStreamPayload[],
  expanded: boolean,
): GovernanceEventStreamPayload[] {
  if (expanded) return [...events].sort((left, right) => left.eventId - right.eventId)
  const latest = getLatestGovernanceEvent(events)
  return latest ? [latest] : []
}

export function parseGovernanceEvent(data: string): GovernanceEventStreamPayload | null {
  let value: unknown
  try {
    value = JSON.parse(data)
  } catch {
    return null
  }

  return isGovernanceEvent(value) ? value : null
}

export function mergeUniqueGovernanceEvents(
  current: GovernanceEventStreamPayload[],
  incoming: GovernanceEventStreamPayload[],
): GovernanceEventStreamPayload[] {
  const eventsById = new Map<number, GovernanceEventStreamPayload>()
  current.forEach((event) => eventsById.set(event.eventId, event))
  incoming.forEach((event) => {
    if (!eventsById.has(event.eventId)) eventsById.set(event.eventId, event)
  })
  return [...eventsById.values()].sort((left, right) => left.eventId - right.eventId)
}

export function selectConversationEvents(
  events: GovernanceEventStreamPayload[],
  conversationId: string | null,
): GovernanceEventStreamPayload[] {
  if (conversationId === null) return []
  return events.filter((event) => event.conversationId === conversationId)
}

export function groupEventsByWorkflow(
  events: GovernanceEventStreamPayload[],
): WorkflowActivityGroup[] {
  const groups = new Map<string, WorkflowActivityGroup>()
  for (const event of events) {
    let group = groups.get(event.workflowId)
    if (!group) {
      group = {
        workflowId: event.workflowId,
        conversationId: event.conversationId,
        agentName: event.agentName,
        workflowType: event.workflowType,
        events: [],
      }
      groups.set(event.workflowId, group)
    }
    group.events.push(event)
  }
  return [...groups.values()]
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isGovernanceEvent(value: unknown): value is GovernanceEventStreamPayload {
  if (!isRecord(value) || !isRecord(value.event)) return false
  return typeof value.eventId === 'number'
    && Number.isSafeInteger(value.eventId)
    && value.eventId >= 0
    && typeof value.workflowId === 'string'
    && value.workflowId.length > 0
    && typeof value.agentName === 'string'
    && value.agentName.length > 0
    && isOneOf(value.workflowType, workflowTypes)
    && (value.conversationId === null || typeof value.conversationId === 'string')
    && isTrajectoryEvent(value.event)
}

function isTrajectoryEvent(value: Record<string, unknown>): value is WorkflowTrajectoryEvent {
  const nullableStrings = [
    'toolCallId',
    'toolName',
    'policyId',
    'policyReason',
    'errorType',
    'errorMessage',
    'nodeBeforeId',
    'nodeAfterId',
    'transformationType',
    'riskReason',
    'interventionId',
    'resolvedBy',
    'resolutionReason',
  ]
  const capabilities: WorkflowCapability[] = [
    'READ_CUSTOMER_DATA',
    'READ_CONVERSATION_HISTORY',
    'READ_ORDER',
    'READ_PRODUCT',
    'GENERATE_REPORT',
    'SEND_EMAIL',
    'RETURN_ORDER',
  ]
  const states: WorkflowState[] = [
    'STARTED',
    'DATA_ACQUIRED',
    'REPORT_GENERATED',
    'DELIVERY_REQUESTED',
    'COMPLETED',
  ]
  return nullableStrings.every((field) => isNullableString(value[field]))
    && (value.sequence === null || (
      typeof value.sequence === 'number' && Number.isSafeInteger(value.sequence)
    ))
    && isOneOf(value.eventType, auditEventTypes)
    && typeof value.timestamp === 'string'
    && isNullableOneOf(value.capability, capabilities)
    && isNullableOneOf(value.policyDecision, ['ALLOW', 'BLOCK'])
    && isNullableOneOf(value.executionStatus, ['SUCCESS', 'FAILED', 'BLOCKED'])
    && isNullableString(value.nodeBeforeId)
    && isNullableOneOf(value.nodeBeforeState, states)
    && isNullableOneOf(value.nodeBeforeCapability, capabilities)
    && isNullableString(value.nodeAfterId)
    && isNullableOneOf(value.nodeAfterState, states)
    && isNullableOneOf(value.nodeAfterCapability, capabilities)
    && isNullableOneOf(value.stateBefore, states)
    && isNullableOneOf(value.stateAfter, states)
    && isNullableOneOf(value.classificationBefore, [
      'RAW_CUSTOMER_DATA',
      'ANALYTICAL',
      'REDACTED_DETAIL',
    ])
    && isNullableOneOf(value.classificationAfter, [
      'RAW_CUSTOMER_DATA',
      'ANALYTICAL',
      'REDACTED_DETAIL',
    ])
    && isNullableString(value.transformationType)
    && isNullableOneOf(value.riskLevel, ['LOW', 'MODERATE', 'HIGH', 'CRITICAL'])
    && isNullableOneOf(value.intervention, ['NONE', 'AUDIT', 'REQUIRE_APPROVAL', 'BLOCK'])
    && isNullableString(value.riskReason)
    && isNullableString(value.interventionId)
    && isNullableOneOf(value.interventionStatus, ['PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'])
    && isNullableString(value.resolvedBy)
    && isNullableString(value.resolutionReason)
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string'
}

function isOneOf<T extends string>(value: unknown, allowed: readonly T[]): value is T {
  return typeof value === 'string' && allowed.some((item) => item === value)
}

function isNullableOneOf<T extends string>(
  value: unknown,
  allowed: readonly T[],
): value is T | null {
  return value === null || isOneOf(value, allowed)
}
