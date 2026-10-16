import { useState } from 'react'
import { Activity, ChevronDown, ChevronUp, Radio } from 'lucide-react'
import {
  getVisibleGovernanceEvents,
  groupEventsByWorkflow,
} from './governanceActivity'
import type { GovernanceStreamStatus } from './useGovernanceActivity'
import type { GovernanceEventStreamPayload, WorkflowTrajectoryEvent } from '../../types/api'

type Props = {
  conversationId: string | null
  events: GovernanceEventStreamPayload[]
  status: GovernanceStreamStatus
}

const statusLabels: Record<GovernanceStreamStatus, string> = {
  CONNECTING: 'Connecting',
  LIVE: 'Live',
  RECONNECTING: 'Reconnecting',
  UNAVAILABLE: 'Unavailable',
}

export function GovernanceActivityPanel({ conversationId, events, status }: Props) {
  const [expanded, setExpanded] = useState(false)
  const visibleEvents = getVisibleGovernanceEvents(events, expanded)
  const workflows = groupEventsByWorkflow(visibleEvents)
  const toggleLabel = expanded ? 'Collapse governance activity' : 'Expand governance activity'

  return (
    <section aria-labelledby="governance-heading" className="governance-panel">
      <div className="side-panel-heading">
        <h2 id="governance-heading">Governance activity</h2>
        <div className="governance-panel-actions">
          <span className={`connection-state connection-${status.toLowerCase()}`}>
            <Radio aria-hidden="true" size={12} />
            {statusLabels[status]}
          </span>
          <button
            aria-controls="governance-activity-content"
            aria-expanded={expanded}
            aria-label={toggleLabel}
            className="activity-toggle"
            onClick={() => setExpanded((current) => !current)}
            type="button"
          >
            {expanded
              ? <ChevronUp aria-hidden="true" size={15} />
              : <ChevronDown aria-hidden="true" size={15} />}
          </button>
        </div>
      </div>
      <p className={`activity-caption${expanded ? '' : ' activity-caption-collapsed'}`}>
        {conversationId
          ? 'Live events associated with this conversation'
          : 'Conversation events are shown after the coordinator returns its ID'}
      </p>
      <div
        aria-live="polite"
        className={`activity-list${expanded ? ' activity-list-expanded' : ' activity-list-collapsed'}`}
        id="governance-activity-content"
      >
        {!conversationId ? (
          <p className="activity-empty">No conversation is selected yet.</p>
        ) : visibleEvents.length === 0 ? (
          <p className="activity-empty">No governance events observed for this conversation yet.</p>
        ) : (
          workflows.map((workflow) => (
            <article className="activity-workflow" key={workflow.workflowId}>
              <header className="activity-workflow-heading">
                <strong>{workflow.agentName}</strong>
                <span>{workflow.workflowType.replace(/_/g, ' ')}</span>
              </header>
              <p className="workflow-id" title={workflow.workflowId}>Workflow {workflow.workflowId}</p>
              <ol className="activity-events">
                {workflow.events.map((payload) => (
                  <ActivityEvent key={payload.eventId} payload={payload} />
                ))}
              </ol>
            </article>
          ))
        )}
      </div>
    </section>
  )
}

function ActivityEvent({ payload }: { payload: GovernanceEventStreamPayload }) {
  const event = payload.event
  const action = event.toolName || event.capability || event.eventType.replace(/_/g, ' ')
  const outcomes = getGovernanceOutcomes(event)

  return (
    <li className="activity-event">
      <span aria-hidden="true" className="event-marker"><Activity size={11} /></span>
      <div className="activity-event-body">
        <strong className="activity-action">{action}</strong>
        <div className="activity-event-meta">
          <span>{payload.agentName}</span>
          <time dateTime={event.timestamp}>{formatTimestamp(event.timestamp)}</time>
        </div>
        {outcomes.length > 0 && (
          <ul className="event-outcomes">
            {outcomes.map((outcome) => <li key={outcome}>{outcome}</li>)}
          </ul>
        )}
        {(event.policyReason || event.riskReason || event.errorMessage || event.resolutionReason) && (
          <details className="event-details">
            <summary>Details</summary>
            <div>
              {event.policyReason && <p>Policy: {event.policyReason}</p>}
              {event.riskReason && <p>Risk: {event.riskReason}</p>}
              {event.errorMessage && <p>Error: {event.errorMessage}</p>}
              {event.resolutionReason && <p>Resolution: {event.resolutionReason}</p>}
            </div>
          </details>
        )}
      </div>
    </li>
  )
}

function getGovernanceOutcomes(event: WorkflowTrajectoryEvent): string[] {
  const outcomes: string[] = []
  if (event.policyDecision) outcomes.push(`Policy ${event.policyDecision}`)
  if (event.riskLevel) outcomes.push(`Risk ${event.riskLevel}`)
  if (event.intervention && event.intervention !== 'NONE') {
    outcomes.push(`Intervention ${event.intervention.replace(/_/g, ' ')}`)
  }
  if (event.interventionStatus) outcomes.push(`Status ${event.interventionStatus}`)
  if (event.executionStatus) outcomes.push(`Execution ${event.executionStatus}`)
  return outcomes
}

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp)
  return Number.isNaN(date.getTime()) ? timestamp : date.toLocaleTimeString()
}
