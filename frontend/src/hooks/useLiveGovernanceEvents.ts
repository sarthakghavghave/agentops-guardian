import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { getApiUrl } from '../api/client'
import { queryKeys } from './queryKeys'
import { parseLiveGovernanceEvent } from '../types/liveGovernanceEvent'
import type { LiveGovernanceEvent } from '../types/liveGovernanceEvent'

export type LiveConnectionStatus =
  | 'CONNECTING'
  | 'LIVE'
  | 'RECONNECTING'
  | 'DISCONNECTED'

export type LiveGovernanceState = {
  status: LiveConnectionStatus
  events: LiveGovernanceEvent[]
  lastEventId: number | null
  error: string | null
}

const maximumEventHistory = 100
const invalidationDebounceMs = 100

export function useLiveGovernanceEvents(): LiveGovernanceState {
  const queryClient = useQueryClient()
  const [state, setState] = useState<LiveGovernanceState>({
    status: 'CONNECTING',
    events: [],
    lastEventId: null,
    error: null,
  })
  const seenEventIds = useRef(new Set<number>())

  useEffect(() => {
    let active = true
    let source: EventSource
    let invalidationTimer: ReturnType<typeof setTimeout> | undefined
    const pendingWorkflowIds = new Set<string>()
    const pendingInterventionIds = new Set<string>()

    const flushInvalidations = () => {
      invalidationTimer = undefined
      const workflowIds = [...pendingWorkflowIds]
      const interventionIds = [...pendingInterventionIds]
      pendingWorkflowIds.clear()
      pendingInterventionIds.clear()

      const invalidations: Promise<void>[] = []
      if (workflowIds.length > 0) {
        invalidations.push(
          queryClient.invalidateQueries({ queryKey: queryKeys.workflows.summaries }),
        )
        invalidations.push(
          ...workflowIds.map((workflowId) =>
            queryClient.invalidateQueries({
              queryKey: queryKeys.workflows.trajectory(workflowId),
            }),
          ),
        )
      }
      for (const interventionId of interventionIds) {
        invalidations.push(
          queryClient.invalidateQueries({ queryKey: queryKeys.interventions.list }),
          queryClient.invalidateQueries({
            queryKey: queryKeys.interventions.detail(interventionId),
          }),
        )
      }
      void Promise.all(invalidations)
    }

    const scheduleInvalidation = (event: LiveGovernanceEvent) => {
      pendingWorkflowIds.add(event.workflowId)
      if (event.event.interventionId) {
        pendingInterventionIds.add(event.event.interventionId)
      }
      if (invalidationTimer !== undefined) clearTimeout(invalidationTimer)
      invalidationTimer = setTimeout(flushInvalidations, invalidationDebounceMs)
    }

    const onGovernanceEvent = (message: Event) => {
      if (!active) return
      const event = parseLiveGovernanceEvent((message as MessageEvent<string>).data)
      if (!event) {
        console.warn('Ignored malformed governance event from the live stream.')
        return
      }

      if (seenEventIds.current.has(event.eventId)) return
      seenEventIds.current.add(event.eventId)
      if (seenEventIds.current.size > maximumEventHistory) {
        const oldestEventId = seenEventIds.current.values().next().value
        if (oldestEventId !== undefined) seenEventIds.current.delete(oldestEventId)
      }
      setState((current) => ({
        ...current,
        events: [...current.events, event].slice(-maximumEventHistory),
        lastEventId: event.eventId,
        error: null,
      }))
      scheduleInvalidation(event)
    }

    try {
      source = new EventSource(getApiUrl('/events/stream'))
    } catch {
      setState((current) => ({
        ...current,
        status: 'DISCONNECTED',
        error: 'The live governance event stream could not be opened.',
      }))
      return
    }

    source.addEventListener('open', () => {
      if (!active) return
      setState((current) => ({ ...current, status: 'LIVE', error: null }))
    })
    source.addEventListener('governance-event', onGovernanceEvent)
    source.addEventListener('error', () => {
      if (!active) return
      const disconnected = source.readyState === EventSource.CLOSED
      setState((current) => ({
        ...current,
        status: disconnected
          ? 'DISCONNECTED'
          : 'RECONNECTING',
        error: 'The live governance event stream is unavailable; the browser will retry automatically.',
      }))
    })

    return () => {
      active = false
      source.close()
      if (invalidationTimer !== undefined) clearTimeout(invalidationTimer)
    }
  }, [queryClient])

  return state
}
