import type { WorkflowTrajectoryEvent } from '../../types/audit'
import type { WorkflowState, WorkflowTrajectoryResponse } from '../../types/workflow'

export type WorkflowTrajectoryGroup =
  | {
      kind: 'action'
      key: string
      toolCallId: string
      events: WorkflowTrajectoryEvent[]
    }
  | {
      kind: 'standalone'
      key: string
      event: WorkflowTrajectoryEvent
    }

export type GovernanceAttentionLevel = 'BLOCKED' | 'PENDING' | 'INTERVENTION' | 'ACTIVITY' | 'NONE'

export type WorkflowGovernanceSummary = {
  currentState: WorkflowState | null
  latestActivity: {
    label: string
    timestamp: string
  } | null
  latestGovernanceOutcome: {
    label: string
    tone: 'block' | 'attention' | 'allow' | 'neutral'
  } | null
  attentionLevel: GovernanceAttentionLevel
  attentionGroupKey: string | null
  attentionActionId: string | null
  actionCount: number
  eventCount: number
  latestTimestamp: string | null
}

export function groupTrajectoryEvents(
  events: WorkflowTrajectoryEvent[],
): WorkflowTrajectoryGroup[] {
  const orderedGroups: Array<{ group: WorkflowTrajectoryGroup; lastEventIndex: number }> = []
  const actionGroups = new Map<
    string,
    {
      group: Extract<WorkflowTrajectoryGroup, { kind: 'action' }>
      lastEventIndex: number
    }
  >()

  events.forEach((event, eventIndex) => {
    if (event.toolCallId === null) {
      orderedGroups.push({
        group: {
          kind: 'standalone',
          key: `event:${eventIndex}`,
          event,
        },
        lastEventIndex: eventIndex,
      })
      return
    }

    let entry = actionGroups.get(event.toolCallId)
    if (!entry) {
      const group = {
        kind: 'action' as const,
        key: `tool-call:${event.toolCallId}`,
        toolCallId: event.toolCallId,
        events: [],
      }
      entry = { group, lastEventIndex: eventIndex }
      actionGroups.set(event.toolCallId, entry)
      orderedGroups.push(entry)
    }
    entry.group.events.push(event)
    entry.lastEventIndex = eventIndex
  })

  // Place each action where its final audit event occurs, preserving rail chronology.
  return orderedGroups
    .sort((left, right) => left.lastEventIndex - right.lastEventIndex)
    .map(({ group }) => group)
}

export function formatGovernanceLabel(value: string): string {
  return value
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase())
}

export function availableValues<T>(
  events: WorkflowTrajectoryEvent[],
  getValue: (event: WorkflowTrajectoryEvent) => T | null,
): T[] {
  const values: T[] = []
  for (const event of events) {
    const value = getValue(event)
    if (value !== null && !values.includes(value)) values.push(value)
  }
  return values
}

export function latestAvailable<T>(
  events: WorkflowTrajectoryEvent[],
  getValue: (event: WorkflowTrajectoryEvent) => T | null,
): T | null {
  for (let index = events.length - 1; index >= 0; index -= 1) {
    const value = getValue(events[index])
    if (value !== null) return value
  }
  return null
}

function governanceOutcome(
  event: WorkflowTrajectoryEvent,
): WorkflowGovernanceSummary['latestGovernanceOutcome'] {
  const labels: string[] = []
  let tone: NonNullable<WorkflowGovernanceSummary['latestGovernanceOutcome']>['tone'] = 'neutral'

  if (event.policyDecision) {
    labels.push(`Policy ${formatGovernanceLabel(event.policyDecision)}`)
    tone = event.policyDecision === 'BLOCK' ? 'block' : 'allow'
  }
  if (event.intervention && event.intervention !== 'NONE') {
    labels.push(formatGovernanceLabel(event.intervention))
    if (event.intervention === 'BLOCK') tone = 'block'
    else if (event.intervention === 'REQUIRE_APPROVAL' && tone !== 'block') tone = 'attention'
  }
  if (event.interventionStatus) {
    labels.push(formatGovernanceLabel(event.interventionStatus))
    if (event.interventionStatus === 'PENDING' && tone !== 'block') tone = 'attention'
    else if (event.interventionStatus === 'REJECTED') tone = 'block'
    else if (event.interventionStatus === 'APPROVED' && tone === 'neutral') tone = 'allow'
  }
  return labels.length > 0 ? { label: labels.join(' · '), tone } : null
}

function groupAttentionLevel(group: WorkflowTrajectoryGroup): GovernanceAttentionLevel {
  const events = group.kind === 'action' ? group.events : [group.event]
  const latestPolicyDecision = latestAvailable(events, (event) => event.policyDecision)
  const latestExecutionStatus = latestAvailable(events, (event) => event.executionStatus)
  const latestIntervention = latestAvailable(events, (event) => event.intervention)
  const latestInterventionStatus = latestAvailable(events, (event) => event.interventionStatus)

  if (
    latestPolicyDecision === 'BLOCK' ||
    latestExecutionStatus === 'BLOCKED' ||
    latestIntervention === 'BLOCK'
  ) {
    return 'BLOCKED'
  }
  if (
    latestInterventionStatus === 'PENDING' ||
    (latestIntervention === 'REQUIRE_APPROVAL' && latestInterventionStatus === null)
  ) {
    return 'PENDING'
  }
  if (
    (latestIntervention !== null && latestIntervention !== 'NONE') ||
    latestInterventionStatus !== null
  ) {
    return 'INTERVENTION'
  }
  if (
    latestPolicyDecision === 'ALLOW' || latestExecutionStatus === 'SUCCESS'
  ) {
    return 'ACTIVITY'
  }
  return 'NONE'
}

function attentionPriority(level: GovernanceAttentionLevel): number {
  switch (level) {
    case 'BLOCKED':
      return 4
    case 'PENDING':
      return 3
    case 'INTERVENTION':
      return 2
    case 'ACTIVITY':
      return 1
    case 'NONE':
      return 0
  }
}

export function getWorkflowGovernanceSummary(
  trajectory: WorkflowTrajectoryResponse,
): WorkflowGovernanceSummary {
  const events = trajectory.events
  const groups = groupTrajectoryEvents(events)
  const latestEvent = events[events.length - 1] ?? null

  let latestOutcome: WorkflowGovernanceSummary['latestGovernanceOutcome'] = null
  for (let index = events.length - 1; index >= 0; index -= 1) {
    latestOutcome = governanceOutcome(events[index])
    if (latestOutcome) break
  }

  let attentionGroup: WorkflowTrajectoryGroup | null = null
  let attentionLevel: GovernanceAttentionLevel = 'NONE'
  for (const group of groups) {
    const candidateLevel = groupAttentionLevel(group)
    if (
      candidateLevel !== 'NONE' &&
      attentionPriority(candidateLevel) >= attentionPriority(attentionLevel)
    ) {
      attentionGroup = group
      attentionLevel = candidateLevel
    }
  }

  const attentionEvents = attentionGroup
    ? attentionGroup.kind === 'action'
      ? attentionGroup.events
      : [attentionGroup.event]
    : []
  const attentionActionId =
    attentionGroup?.kind === 'action'
      ? attentionGroup.toolCallId
      : attentionEvents[0]?.toolCallId ?? null

  return {
    currentState:
      latestAvailable(events, (event) => event.stateAfter) ??
      latestAvailable(events, (event) => event.stateBefore),
    latestActivity: latestEvent
      ? {
          label: formatGovernanceLabel(
            latestEvent.toolName ?? latestEvent.capability ?? latestEvent.eventType,
          ),
          timestamp: latestEvent.timestamp,
        }
      : null,
    latestGovernanceOutcome: latestOutcome,
    attentionLevel,
    attentionGroupKey: attentionGroup?.key ?? null,
    attentionActionId,
    actionCount: groups.filter((group) => group.kind === 'action').length,
    eventCount: events.length,
    latestTimestamp: latestEvent?.timestamp ?? null,
  }
}
