import type {
  AuditEventType,
  DataClassification,
  ExecutionStatus,
  GovernanceDecision,
  WorkflowTrajectoryEvent,
} from './audit'
import type {
  GovernanceInterventionKind,
  GovernanceInterventionStatus,
} from './intervention'
import type { RiskLevel } from './policy'
import type { WorkflowCapability, WorkflowState, WorkflowType } from './workflow'

export type LiveGovernanceEvent = {
  eventId: number
  workflowId: string
  agentName: string
  workflowType: WorkflowType
  event: WorkflowTrajectoryEvent
}

const auditEventTypes: readonly AuditEventType[] = [
  'PROPOSED_ACTION',
  'POLICY_DECISION',
  'RISK_ASSESSMENT',
  'INTERVENTION_CREATED',
  'INTERVENTION_APPROVED',
  'INTERVENTION_REJECTED',
  'INTERVENTION_EXPIRED',
  'EXECUTION_OUTCOME',
  'WORKFLOW_TRANSITION',
]
const classifications: readonly DataClassification[] = [
  'RAW_CUSTOMER_DATA',
  'ANALYTICAL',
  'REDACTED_DETAIL',
]
const executionStatuses: readonly ExecutionStatus[] = ['SUCCESS', 'FAILED', 'BLOCKED']
const governanceDecisions: readonly GovernanceDecision[] = ['ALLOW', 'BLOCK']
const interventionKinds: readonly GovernanceInterventionKind[] = [
  'NONE',
  'AUDIT',
  'REQUIRE_APPROVAL',
  'BLOCK',
]
const interventionStatuses: readonly GovernanceInterventionStatus[] = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'EXPIRED',
]
const riskLevels: readonly RiskLevel[] = ['LOW', 'MODERATE', 'HIGH', 'CRITICAL']
const workflowCapabilities: readonly WorkflowCapability[] = [
  'READ_CUSTOMER_DATA',
  'READ_ORDER',
  'READ_PRODUCT',
  'GENERATE_REPORT',
  'SEND_EMAIL',
  'RETURN_ORDER',
]
const workflowStates: readonly WorkflowState[] = [
  'STARTED',
  'DATA_ACQUIRED',
  'REPORT_GENERATED',
  'DELIVERY_REQUESTED',
  'COMPLETED',
]

function isOneOf<T extends string>(value: unknown, values: readonly T[]): value is T {
  return typeof value === 'string' && values.some((candidate) => candidate === value)
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string'
}

function isNullableEnum<T extends string>(
  value: unknown,
  values: readonly T[],
): value is T | null {
  return value === null || isOneOf(value, values)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isTrajectoryEvent(value: unknown): value is WorkflowTrajectoryEvent {
  if (!isRecord(value)) return false

  return (
    typeof value.timestamp === 'string' &&
    isOneOf(value.eventType, auditEventTypes) &&
    isNullableString(value.toolCallId) &&
    isNullableString(value.toolName) &&
    isNullableEnum(value.capability, workflowCapabilities) &&
    (value.sequence === null || (typeof value.sequence === 'number' && Number.isInteger(value.sequence))) &&
    isNullableEnum(value.policyDecision, governanceDecisions) &&
    isNullableString(value.policyId) &&
    isNullableString(value.policyReason) &&
    isNullableEnum(value.executionStatus, executionStatuses) &&
    isNullableString(value.errorType) &&
    isNullableString(value.errorMessage) &&
    isNullableString(value.nodeBeforeId) &&
    isNullableEnum(value.nodeBeforeState, workflowStates) &&
    isNullableEnum(value.nodeBeforeCapability, workflowCapabilities) &&
    isNullableString(value.nodeAfterId) &&
    isNullableEnum(value.nodeAfterState, workflowStates) &&
    isNullableEnum(value.nodeAfterCapability, workflowCapabilities) &&
    isNullableEnum(value.stateBefore, workflowStates) &&
    isNullableEnum(value.stateAfter, workflowStates) &&
    isNullableEnum(value.classificationBefore, classifications) &&
    isNullableEnum(value.classificationAfter, classifications) &&
    isNullableString(value.transformationType) &&
    isNullableEnum(value.riskLevel, riskLevels) &&
    isNullableEnum(value.intervention, interventionKinds) &&
    isNullableString(value.riskReason) &&
    isNullableString(value.interventionId) &&
    isNullableEnum(value.interventionStatus, interventionStatuses) &&
    isNullableString(value.resolvedBy) &&
    isNullableString(value.resolutionReason)
  )
}

export function parseLiveGovernanceEvent(data: string): LiveGovernanceEvent | null {
  try {
    const value: unknown = JSON.parse(data)
    if (
      !isRecord(value) ||
      typeof value.eventId !== 'number' ||
      !Number.isInteger(value.eventId) ||
      value.eventId < 0 ||
      typeof value.workflowId !== 'string' ||
      typeof value.agentName !== 'string' ||
      !isOneOf(value.workflowType, ['CUSTOMER_SUPPORT', 'CUSTOMER_REPORTING'] as const) ||
      !isTrajectoryEvent(value.event)
    ) {
      return null
    }

    return {
      eventId: value.eventId,
      workflowId: value.workflowId,
      agentName: value.agentName,
      workflowType: value.workflowType,
      event: value.event,
    }
  } catch {
    return null
  }
}
