import { useEffect, useState } from 'react'
import { getApiUrl } from '../../api/client'
import { GOVERNANCE_EVENTS_STREAM_PATH } from '../../api/governanceEvents'
import { mergeUniqueGovernanceEvents, parseGovernanceEvent } from './governanceActivity'
import type { GovernanceEventStreamPayload } from '../../types/api'

export type GovernanceStreamStatus = 'CONNECTING' | 'LIVE' | 'RECONNECTING' | 'UNAVAILABLE'

export type GovernanceActivityState = {
  status: GovernanceStreamStatus
  events: GovernanceEventStreamPayload[]
}

export function useGovernanceActivity(): GovernanceActivityState {
  const [state, setState] = useState<GovernanceActivityState>({
    status: 'CONNECTING',
    events: [],
  })

  useEffect(() => {
    if (typeof EventSource === 'undefined') {
      setState((current) => ({ ...current, status: 'UNAVAILABLE' }))
      return
    }

    let source: EventSource
    try {
      source = new EventSource(getApiUrl(GOVERNANCE_EVENTS_STREAM_PATH))
    } catch {
      setState((current) => ({ ...current, status: 'UNAVAILABLE' }))
      return
    }

    const onOpen = () => {
      setState((current) => ({ ...current, status: 'LIVE' }))
    }
    const onError = () => {
      setState((current) => ({
        ...current,
        status: source.readyState === EventSource.CLOSED ? 'UNAVAILABLE' : 'RECONNECTING',
      }))
    }
    const onGovernanceEvent = (message: Event) => {
      const data = (message as MessageEvent<string>).data
      const event = parseGovernanceEvent(data)
      if (!event) {
        console.warn('Ignored malformed governance event from the live stream.')
        return
      }
      setState((current) => ({
        ...current,
        status: 'LIVE',
        events: mergeUniqueGovernanceEvents(current.events, [event]),
      }))
    }

    source.addEventListener('open', onOpen)
    source.addEventListener('error', onError)
    source.addEventListener('governance-event', onGovernanceEvent)

    return () => {
      source.close()
    }
  }, [])

  return state
}
