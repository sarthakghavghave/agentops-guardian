import { ArrowDown, CircleDot, GitBranch, Shield, ShieldAlert, Wrench } from 'lucide-react'
import { EmptyState } from '../../components/feedback/EmptyState'
import { Panel } from '../../components/ui/Panel'
import type {
  AuditEventType,
  WorkflowTrajectoryEvent as WorkflowTrajectoryEventData,
} from '../../types/audit'
import type {
  GovernanceInterventionKind,
  GovernanceInterventionStatus,
} from '../../types/intervention'
import type { RiskLevel } from '../../types/policy'

const eventAccent: Record<AuditEventType, string> = {
  PROPOSED_ACTION: 'border-slate-300 bg-slate-100 text-slate-600',
  POLICY_DECISION: 'border-accent/30 bg-accent/10 text-accent',
  RISK_ASSESSMENT: 'border-amber-300 bg-amber-50 text-amber-700',
  INTERVENTION_CREATED: 'border-amber-300 bg-amber-50 text-amber-700',
  INTERVENTION_APPROVED: 'border-emerald-300 bg-emerald-50 text-emerald-700',
  INTERVENTION_REJECTED: 'border-red-300 bg-red-50 text-red-700',
  INTERVENTION_EXPIRED: 'border-slate-300 bg-slate-100 text-slate-600',
  EXECUTION_OUTCOME: 'border-blue-300 bg-blue-50 text-blue-700',
  WORKFLOW_TRANSITION: 'border-indigo-300 bg-indigo-50 text-indigo-700',
}

function formatEnum(value: string): string {
  return value.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return 'Timestamp unavailable'

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(date)
}

function ValueBadge({
  label,
  tone = 'neutral',
}: {
  label: string
  tone?: 'neutral' | 'allow' | 'block' | 'risk' | 'success'
}) {
  const styles = {
    neutral: 'border-slate-200 bg-slate-50 text-slate-700',
    allow: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    block: 'border-red-200 bg-red-50 text-red-800',
    risk: 'border-amber-200 bg-amber-50 text-amber-800',
    success: 'border-blue-200 bg-blue-50 text-blue-800',
  }

  return (
    <span className={`inline-flex items-center rounded border px-2 py-1 text-xs font-medium ${styles[tone]}`}>
      {label}
    </span>
  )
}

function decisionTone(value: string): 'allow' | 'block' | 'neutral' {
  if (value === 'ALLOW') return 'allow'
  if (value === 'BLOCK') return 'block'
  return 'neutral'
}

function executionTone(value: string): 'success' | 'block' | 'neutral' {
  if (value === 'SUCCESS') return 'success'
  if (value === 'BLOCKED' || value === 'FAILED') return 'block'
  return 'neutral'
}

function riskTone(value: RiskLevel): 'risk' | 'block' | 'neutral' {
  if (value === 'HIGH' || value === 'CRITICAL') return 'block'
  if (value === 'MODERATE') return 'risk'
  return 'neutral'
}

function interventionTone(value: GovernanceInterventionKind): 'risk' | 'block' | 'neutral' {
  if (value === 'REQUIRE_APPROVAL') return 'risk'
  if (value === 'BLOCK') return 'block'
  return 'neutral'
}

function statusTone(value: GovernanceInterventionStatus): 'risk' | 'allow' | 'block' | 'neutral' {
  if (value === 'PENDING') return 'risk'
  if (value === 'APPROVED') return 'allow'
  if (value === 'REJECTED') return 'block'
  return 'neutral'
}

function riskLabel(value: RiskLevel): string {
  return `${formatEnum(value)} risk`
}

function EventTimestamp({ value }: { value: string }) {
  return (
    <time className="text-xs text-muted" dateTime={value}>
      {formatTimestamp(value)}
    </time>
  )
}

function WorkflowTransition({ event }: { event: WorkflowTrajectoryEventData }) {
  const hasTransition = Boolean(
    event.nodeBeforeId ||
      event.nodeAfterId ||
      event.nodeBeforeCapability ||
      event.nodeAfterCapability ||
      event.nodeBeforeState ||
      event.nodeAfterState ||
      event.stateBefore ||
      event.stateAfter,
  )
  if (!hasTransition) return null

  const beforeState = event.nodeBeforeState ?? event.stateBefore
  const afterState = event.nodeAfterState ?? event.stateAfter
  const beforeNode = event.nodeBeforeId
  const afterNode = event.nodeAfterId
  const beforeCapability = event.nodeBeforeCapability
  const afterCapability = event.nodeAfterCapability

  return (
    <div className="rounded-md border border-line bg-slate-50/70 p-3">
      <p className="flex items-center gap-2 text-xs font-semibold text-slate-700">
        <GitBranch aria-hidden="true" size={14} />
        Workflow transition
      </p>
      <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
        {(beforeNode || beforeState || beforeCapability) && (
          <span className="rounded bg-white px-2 py-1 text-slate-700 ring-1 ring-line">
            {[
              beforeNode,
              beforeState ? formatEnum(beforeState) : null,
              beforeCapability ? formatEnum(beforeCapability) : null,
            ]
              .filter(Boolean)
              .join(' · ')}
          </span>
        )}
        {(beforeNode || beforeState || beforeCapability) &&
          (afterNode || afterState || afterCapability) && (
          <ArrowDown aria-hidden="true" className="text-muted" size={14} />
        )}
        {(afterNode || afterState || afterCapability) && (
          <span className="rounded bg-white px-2 py-1 font-medium text-slate-800 ring-1 ring-line">
            {[
              afterNode,
              afterState ? formatEnum(afterState) : null,
              afterCapability ? formatEnum(afterCapability) : null,
            ]
              .filter(Boolean)
              .join(' · ')}
          </span>
        )}
      </div>
    </div>
  )
}

function DataClassificationTransition({ event }: { event: WorkflowTrajectoryEventData }) {
  const hasClassification =
    event.classificationBefore !== null ||
    event.classificationAfter !== null ||
    event.transformationType !== null
  if (!hasClassification) return null

  return (
    <div className="rounded-md border border-accent/20 bg-accent/[0.04] p-3">
      <p className="flex items-center gap-2 text-xs font-semibold text-accent">
        <Shield aria-hidden="true" size={14} />
        Data governance
      </p>
      {event.classificationBefore || event.classificationAfter ? (
        <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
          <ValueBadge label={event.classificationBefore ? formatEnum(event.classificationBefore) : 'Not specified'} />
          <ArrowDown aria-hidden="true" className="text-muted" size={14} />
          <ValueBadge label={event.classificationAfter ? formatEnum(event.classificationAfter) : 'Not specified'} />
        </div>
      ) : null}
      {event.transformationType && (
        <p className="mt-2 text-xs text-slate-600">
          Transformation: <span className="font-medium text-slate-800">{formatEnum(event.transformationType)}</span>
        </p>
      )}
    </div>
  )
}

function GovernanceDetails({ event }: { event: WorkflowTrajectoryEventData }) {
  const hasGovernanceDetails = Boolean(
    event.policyDecision ||
      event.policyReason ||
    event.policyId ||
      event.executionStatus ||
      event.errorType ||
      event.riskLevel ||
      event.intervention ||
      event.riskReason ||
      event.interventionId ||
      event.interventionStatus ||
      event.resolvedBy ||
      event.resolutionReason,
  )
  if (!hasGovernanceDetails) return null

  return (
    <div className="grid gap-3 md:grid-cols-2">
      {(event.policyDecision || event.policyReason || event.policyId) && (
        <div className="rounded-md border border-line p-3">
          <p className="text-xs font-semibold text-slate-700">Policy</p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {event.policyDecision && (
              <ValueBadge label={formatEnum(event.policyDecision)} tone={decisionTone(event.policyDecision)} />
            )}
            {event.policyId && <span className="font-mono text-xs text-muted">{event.policyId}</span>}
          </div>
          {event.policyReason && <p className="mt-2 text-xs leading-5 text-slate-600">{event.policyReason}</p>}
        </div>
      )}

      {(event.executionStatus || event.errorType) && (
        <div className="rounded-md border border-line p-3">
          <p className="text-xs font-semibold text-slate-700">Execution</p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {event.executionStatus && (
              <ValueBadge label={formatEnum(event.executionStatus)} tone={executionTone(event.executionStatus)} />
            )}
            {event.errorType && <span className="text-xs text-muted">{formatEnum(event.errorType)}</span>}
          </div>
        </div>
      )}

      {(event.riskLevel || event.intervention || event.riskReason) && (
        <div className="rounded-md border border-line p-3">
          <p className="flex items-center gap-2 text-xs font-semibold text-slate-700">
            <ShieldAlert aria-hidden="true" size={14} />
            Risk assessment
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {event.riskLevel && (
              <ValueBadge label={riskLabel(event.riskLevel)} tone={riskTone(event.riskLevel)} />
            )}
            {event.intervention && (
              <ValueBadge
                label={formatEnum(event.intervention)}
                tone={interventionTone(event.intervention)}
              />
            )}
          </div>
          {event.riskReason && <p className="mt-2 text-xs leading-5 text-slate-600">{event.riskReason}</p>}
        </div>
      )}

      {(event.interventionId ||
        event.interventionStatus ||
        event.resolvedBy ||
        event.resolutionReason) && (
        <div className="rounded-md border border-line p-3">
          <p className="text-xs font-semibold text-slate-700">Intervention lifecycle</p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {event.interventionStatus && (
              <ValueBadge
                label={formatEnum(event.interventionStatus)}
                tone={statusTone(event.interventionStatus)}
              />
            )}
            {event.interventionId && (
              <span className="break-all font-mono text-xs text-muted">{event.interventionId}</span>
            )}
          </div>
          {event.resolvedBy && (
            <p className="mt-2 text-xs text-slate-600">Resolved by {event.resolvedBy}</p>
          )}
          {event.resolutionReason && (
            <p className="mt-1 text-xs leading-5 text-slate-600">{event.resolutionReason}</p>
          )}
        </div>
      )}
    </div>
  )
}

function WorkflowTrajectoryEvent({ event, index }: { event: WorkflowTrajectoryEventData; index: number }) {
  const hasActionContext = Boolean(event.toolName || event.capability || event.toolCallId)

  return (
    <li
      aria-label={`Trajectory event ${index + 1}`}
      className="relative pl-9 sm:pl-11"
    >
      <span
        aria-hidden="true"
        className={`absolute left-0 top-1 flex h-6 w-6 items-center justify-center rounded-full border ${eventAccent[event.eventType]}`}
      >
        <CircleDot size={12} />
      </span>
      <div className="min-w-0 rounded-lg border border-line bg-white p-4 shadow-panel sm:p-5">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex min-w-0 flex-wrap items-center gap-2">
            <span className={`rounded border px-2 py-1 text-xs font-semibold ${eventAccent[event.eventType]}`}>
              {formatEnum(event.eventType)}
            </span>
            {event.sequence !== null && (
              <span className="text-xs tabular-nums text-muted">Event {event.sequence}</span>
            )}
          </div>
          <EventTimestamp value={event.timestamp} />
        </div>

        {hasActionContext && (
          <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-line pb-4 text-xs">
            {event.toolName && (
              <span className="inline-flex items-center gap-1.5 font-medium text-ink-900">
                <Wrench aria-hidden="true" size={13} className="text-muted" />
                {event.toolName}
              </span>
            )}
            {event.capability && (
              <span className="text-slate-600">Capability: {formatEnum(event.capability)}</span>
            )}
            {event.toolCallId && (
              <span className="break-all font-mono text-muted">Call {event.toolCallId}</span>
            )}
          </div>
        )}

        <div className="mt-4 space-y-3">
          <GovernanceDetails event={event} />
          <WorkflowTransition event={event} />
          <DataClassificationTransition event={event} />
        </div>
      </div>
    </li>
  )
}

export function WorkflowTrajectory({ events }: { events: WorkflowTrajectoryEventData[] }) {
  if (events.length === 0) {
    return (
      <EmptyState
        title="No trajectory events"
        description="This workflow run does not currently contain any recorded trajectory events."
      />
    )
  }

  const chronologicalEvents = events
    .map((event, index) => ({ event, index }))
    .sort((left, right) => {
      const timeDifference =
        new Date(left.event.timestamp).getTime() - new Date(right.event.timestamp).getTime()
      return Number.isNaN(timeDifference) || timeDifference === 0
        ? left.index - right.index
        : timeDifference
    })

  return (
    <Panel
      title="Execution trajectory"
      description="Chronological audit events recorded for this workflow. Each entry retains its backend event type and available governance context."
      action={
        <span className="shrink-0 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium tabular-nums text-slate-700">
          {events.length} {events.length === 1 ? 'event' : 'events'}
        </span>
      }
    >
      <ol aria-label="Workflow trajectory" className="space-y-4 px-4 py-5 sm:px-5 sm:py-6">
        {chronologicalEvents.map(({ event, index }) => (
          <WorkflowTrajectoryEvent
            key={`${event.timestamp}:${event.sequence ?? index}:${event.eventType}`}
            event={event}
            index={index}
          />
        ))}
      </ol>
    </Panel>
  )
}
