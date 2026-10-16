import type { WorkflowTrajectoryEvent } from '../types/audit'
import type { LiveGovernanceEvent } from '../types/liveGovernanceEvent'

export function appendUniqueLiveGovernanceEvent(
  events: LiveGovernanceEvent[],
  event: LiveGovernanceEvent,
  maximumEvents: number,
): LiveGovernanceEvent[] {
  if (events.some((existing) => existing.eventId === event.eventId)) return events
  return [...events, event].slice(-maximumEvents)
}

export function eventsForWorkflow(
  events: LiveGovernanceEvent[],
  conversationId: string,
  workflowId: string,
): LiveGovernanceEvent[] {
  return events.filter(
    (event) => event.conversationId === conversationId && event.workflowId === workflowId,
  )
}

function canonicalValue(value: unknown): string {
  if (Array.isArray(value)) {
    return `[${value.map(canonicalValue).join(',')}]`
  }
  if (typeof value === 'object' && value !== null) {
    const entries = Object.entries(value).sort(([left], [right]) => left.localeCompare(right))
    return `{${entries.map(([key, entry]) => `${JSON.stringify(key)}:${canonicalValue(entry)}`).join(',')}}`
  }
  return JSON.stringify(value) ?? 'undefined'
}

function eventFingerprint(event: WorkflowTrajectoryEvent): string {
  return canonicalValue(event)
}

export function mergePersistedAndLiveEvents(
  persistedEvents: WorkflowTrajectoryEvent[],
  liveEvents: LiveGovernanceEvent[],
): WorkflowTrajectoryEvent[] {
  const persistedCounts = new Map<string, number>()
  for (const event of persistedEvents) {
    const fingerprint = eventFingerprint(event)
    persistedCounts.set(fingerprint, (persistedCounts.get(fingerprint) ?? 0) + 1)
  }

  const uniqueLiveEvents = new Map<number, LiveGovernanceEvent>()
  for (const liveEvent of liveEvents) {
    if (!uniqueLiveEvents.has(liveEvent.eventId)) {
      uniqueLiveEvents.set(liveEvent.eventId, liveEvent)
    }
  }

  const liveCounts = new Map<string, number>()
  const unpersistedLiveEvents = [...uniqueLiveEvents.values()].filter(({ event }) => {
    const fingerprint = eventFingerprint(event)
    const count = (liveCounts.get(fingerprint) ?? 0) + 1
    liveCounts.set(fingerprint, count)
    return count > (persistedCounts.get(fingerprint) ?? 0)
  })

  return [
    ...persistedEvents.map((event, index) => ({ event, index, eventId: -1 })),
    ...unpersistedLiveEvents.map(({ event, eventId }, index) => ({
      event,
      index: persistedEvents.length + index,
      eventId,
    })),
  ]
    .sort((left, right) => {
      const timeDifference = Date.parse(left.event.timestamp) - Date.parse(right.event.timestamp)
      return Number.isFinite(timeDifference) && timeDifference !== 0
        ? timeDifference
        : left.index - right.index || left.eventId - right.eventId
    })
    .map(({ event }) => event)
}
