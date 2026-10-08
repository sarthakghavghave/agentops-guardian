import { useId, type ReactNode } from 'react'
import { ChevronDown } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { WorkflowTrajectoryEvent } from '../../types/audit'
import type {
  GovernanceInterventionKind,
  GovernanceInterventionStatus,
} from '../../types/intervention'
import type { RiskLevel } from '../../types/policy'
import {
  availableValues,
  formatGovernanceLabel,
  getGroupInterventionId,
  latestAvailable,
  type GovernanceAttentionLevel,
  type WorkflowTrajectoryGroup,
} from './workflowPresentation'

type BadgeTone = 'neutral' | 'allow' | 'block' | 'risk' | 'success'

function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: BadgeTone }) {
  const styles: Record<BadgeTone, string> = {
    neutral: 'border-slate-200 bg-slate-50 text-slate-700',
    allow: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    block: 'border-red-200 bg-red-50 text-red-800',
    risk: 'border-amber-200 bg-amber-50 text-amber-800',
    success: 'border-blue-200 bg-blue-50 text-blue-800',
  }

  return (
    <span className={`inline-flex rounded border px-2 py-1 text-[11px] font-semibold ${styles[tone]}`}>
      {children}
    </span>
  )
}

function decisionTone(decision: string): BadgeTone {
  if (decision === 'ALLOW') return 'allow'
  if (decision === 'BLOCK') return 'block'
  return 'neutral'
}

function executionTone(status: string): BadgeTone {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'BLOCKED') return 'block'
  return 'neutral'
}

function riskTone(level: RiskLevel): BadgeTone {
  if (level === 'HIGH' || level === 'CRITICAL') return 'block'
  if (level === 'MODERATE') return 'risk'
  return 'neutral'
}

function interventionTone(status: GovernanceInterventionStatus): BadgeTone {
  if (status === 'PENDING') return 'risk'
  if (status === 'APPROVED') return 'allow'
  if (status === 'REJECTED') return 'block'
  return 'neutral'
}

function interventionKindTone(kind: GovernanceInterventionKind): BadgeTone {
  if (kind === 'REQUIRE_APPROVAL') return 'risk'
  if (kind === 'BLOCK') return 'block'
  return 'neutral'
}

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return 'Timestamp unavailable'

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function MetadataRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[5.25rem_minmax(0,1fr)] items-start gap-2 text-xs">
      <span className="text-muted">{label}</span>
      <div className="min-w-0 break-words text-slate-800">{children}</div>
    </div>
  )
}

function ActionDetails({ events }: { events: WorkflowTrajectoryEvent[] }) {
  const interventionId = latestAvailable(events, (event) => event.interventionId)
  const policyDecisions = availableValues(events, (event) => event.policyDecision)
  const policyIds = availableValues(events, (event) => event.policyId)
  const policyReasons = availableValues(events, (event) => event.policyReason)
  const riskLevels = availableValues(events, (event) => event.riskLevel)
  const riskReasons = availableValues(events, (event) => event.riskReason)
  const executionStatuses = availableValues(events, (event) => event.executionStatus)
  const interventions = availableValues(events, (event) => event.intervention)
  const interventionStatuses = availableValues(events, (event) => event.interventionStatus)
  const dataTransitions = [...new Set(
    events
      .filter((event) => event.classificationBefore || event.classificationAfter)
      .map((event) =>
        `${event.classificationBefore ? formatGovernanceLabel(event.classificationBefore) : 'Not specified'} → ${
          event.classificationAfter ? formatGovernanceLabel(event.classificationAfter) : 'Not specified'
        }`,
      ),
  )]
  const transformationTypes = availableValues(events, (event) => event.transformationType)
  const workflowStateTransitions = [...new Set(
    events
      .filter((event) =>
        event.nodeBeforeState || event.stateBefore || event.nodeAfterState || event.stateAfter,
      )
      .map((event) => {
        const before = event.nodeBeforeState ?? event.stateBefore
        const after = event.nodeAfterState ?? event.stateAfter
        return `${before ? formatGovernanceLabel(before) : 'Not specified'} → ${
          after ? formatGovernanceLabel(after) : 'Not specified'
        }`
      }),
  )]

  return (
    <div className="space-y-3 border-t border-line bg-slate-50/70 px-4 py-3">
      {policyDecisions.length > 0 && (
        <MetadataRow label="Policy">
          <div className="flex flex-wrap gap-1.5">
            {policyDecisions.map((decision) => (
              <Badge key={decision} tone={decisionTone(decision)}>
                {formatGovernanceLabel(decision)}
              </Badge>
            ))}
          </div>
          {policyIds.length > 0 && (
            <p className="mt-1 font-mono text-[11px] text-muted">{policyIds.join(', ')}</p>
          )}
        </MetadataRow>
      )}
      {policyReasons.length > 0 && (
        <MetadataRow label="Policy reason">
          <ul className="space-y-1">
            {policyReasons.map((reason) => <li key={reason}>{reason}</li>)}
          </ul>
        </MetadataRow>
      )}
      {riskLevels.length > 0 && (
        <MetadataRow label="Risk">
          <div className="flex flex-wrap gap-1.5">
            {riskLevels.map((level) => (
              <Badge key={level} tone={riskTone(level)}>
                {formatGovernanceLabel(level)}
              </Badge>
            ))}
          </div>
          {riskReasons.length > 0 && (
            <ul className="mt-1 space-y-1">
              {riskReasons.map((reason) => <li key={reason}>{reason}</li>)}
            </ul>
          )}
        </MetadataRow>
      )}
      {(interventions.length > 0 || interventionStatuses.length > 0) && (
        <MetadataRow label="Intervention">
          <div className="flex flex-wrap gap-1.5">
            {interventions.map((kind) => (
              <Badge key={kind} tone={interventionKindTone(kind)}>
                {formatGovernanceLabel(kind)}
              </Badge>
            ))}
            {interventionStatuses.map((status) => (
              <Badge key={status} tone={interventionTone(status)}>
                {formatGovernanceLabel(status)}
              </Badge>
            ))}
          </div>
        </MetadataRow>
      )}
      {interventionId && (
        <MetadataRow label="Review">
          <Link
            aria-label={`Review intervention ${interventionId}`}
            className="inline-flex min-h-8 items-center font-medium text-accent underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent"
            to={`/interventions/${encodeURIComponent(interventionId)}`}
          >
            Review intervention →
          </Link>
          <p className="mt-1 break-all font-mono text-[11px] text-muted">{interventionId}</p>
        </MetadataRow>
      )}
      {executionStatuses.length > 0 && (
        <MetadataRow label="Execution">
          <div className="flex flex-wrap gap-1.5">
            {executionStatuses.map((status) => (
              <Badge key={status} tone={executionTone(status)}>
                {formatGovernanceLabel(status)}
              </Badge>
            ))}
          </div>
        </MetadataRow>
      )}
      {workflowStateTransitions.length > 0 && (
        <MetadataRow label="Workflow state">
          <ul className="space-y-1">
            {workflowStateTransitions.map((transition) => <li key={transition}>{transition}</li>)}
          </ul>
        </MetadataRow>
      )}
      {(dataTransitions.length > 0 || transformationTypes.length > 0) && (
        <MetadataRow label="Data">
          {dataTransitions.length > 0 && (
            <ul className="space-y-1">
              {dataTransitions.map((transition) => <li key={transition}>{transition}</li>)}
            </ul>
          )}
          {transformationTypes.length > 0 && (
            <p className="mt-1 text-[11px] text-muted">
              {transformationTypes.map(formatGovernanceLabel).join(' · ')}
            </p>
          )}
        </MetadataRow>
      )}
      <MetadataRow label="Timestamps">
        <ul className="space-y-1">
          {events.map((event, index) => (
            <li key={`${event.timestamp}:${event.eventType}:${index}`}>
              {formatGovernanceLabel(event.eventType)} · {formatTimestamp(event.timestamp)}
            </li>
          ))}
        </ul>
      </MetadataRow>
    </div>
  )
}

function eventActionName(event: WorkflowTrajectoryEvent): string {
  if (event.toolName) return formatGovernanceLabel(event.toolName)
  if (event.capability) return formatGovernanceLabel(event.capability)
  return formatGovernanceLabel(event.eventType)
}

function ActionCard({
  group,
  expanded,
  attentionLevel,
  onToggle,
}: {
  group: WorkflowTrajectoryGroup
  expanded: boolean
  attentionLevel: GovernanceAttentionLevel
  onToggle: (element: HTMLButtonElement) => void
}) {
  const events = group.kind === 'action' ? group.events : [group.event]
  const latestEvent = events[events.length - 1]
  const policyDecision = latestAvailable(events, (event) => event.policyDecision)
  const intervention = latestAvailable(events, (event) => event.intervention)
  const interventionStatus = latestAvailable(events, (event) => event.interventionStatus)
  const executionStatus = latestAvailable(events, (event) => event.executionStatus)
  const riskLevel = latestAvailable(events, (event) => event.riskLevel)
  const interventionId = getGroupInterventionId(group)
  const capability = latestAvailable(events, (event) => event.capability)
  const toolName = latestAvailable(events, (event) => event.toolName)
  const title = toolName
    ? formatGovernanceLabel(toolName)
    : group.kind === 'standalone'
      ? eventActionName(latestEvent)
      : capability
        ? formatGovernanceLabel(capability)
        : 'Governed action'
  const detailsId = useId()
  const hasBlock =
    policyDecision === 'BLOCK' ||
    executionStatus === 'BLOCKED' ||
    intervention === 'BLOCK'
  const requiresApproval =
    intervention === 'REQUIRE_APPROVAL' &&
    (interventionStatus === null || interventionStatus === 'PENDING')
  const attentionBadge =
    attentionLevel === 'BLOCKED'
      ? { label: 'BLOCKED', tone: 'block' as const }
      : attentionLevel === 'PENDING'
        ? { label: 'APPROVAL PENDING', tone: 'risk' as const }
        : attentionLevel === 'INTERVENTION'
          ? { label: 'INTERVENTION', tone: 'risk' as const }
          : null
  const primaryBadge = hasBlock
    ? {
        label: policyDecision === 'BLOCK'
          ? 'Policy BLOCK'
          : intervention === 'BLOCK'
            ? 'Intervention BLOCK'
            : 'BLOCKED',
        tone: 'block' as const,
      }
    : requiresApproval
      ? { label: 'REQUIRE APPROVAL', tone: 'risk' as const }
      : policyDecision
        ? { label: `Policy ${formatGovernanceLabel(policyDecision)}`, tone: decisionTone(policyDecision) }
        : interventionStatus
          ? { label: formatGovernanceLabel(interventionStatus), tone: interventionTone(interventionStatus) }
          : executionStatus
            ? { label: formatGovernanceLabel(executionStatus), tone: executionTone(executionStatus) }
            : null
  const secondaryBadges = [
    interventionId &&
    interventionStatus &&
    (!requiresApproval || interventionStatus !== 'PENDING') ? {
      key: 'intervention-status',
      label: formatGovernanceLabel(interventionStatus),
      tone: interventionTone(interventionStatus),
    } : null,
    riskLevel ? {
      key: 'risk',
      label: formatGovernanceLabel(riskLevel),
      tone: riskTone(riskLevel),
    } : null,
    executionStatus && executionStatus !== 'BLOCKED' ? {
      key: 'execution',
      label: formatGovernanceLabel(executionStatus),
      tone: executionTone(executionStatus),
    } : null,
    interventionStatus && !interventionId && (!requiresApproval || interventionStatus !== 'PENDING') ? {
      key: 'intervention-status',
      label: formatGovernanceLabel(interventionStatus),
      tone: interventionTone(interventionStatus),
    } : null,
  ].filter((badge): badge is { key: string; label: string; tone: BadgeTone } => badge !== null)
    .slice(0, 2)
  const timestamp = latestEvent?.timestamp

  return (
    <article
      className={`w-[min(18rem,82vw)] shrink-0 overflow-hidden rounded-md border bg-white transition-[border-color,box-shadow,transform] duration-150 ${
        attentionLevel === 'BLOCKED'
          ? expanded
            ? 'border-red-300 shadow-md'
            : 'border-red-200 shadow-sm hover:border-red-300 hover:shadow-panel'
          : attentionLevel === 'PENDING' || attentionLevel === 'INTERVENTION'
            ? expanded
              ? 'border-amber-300 shadow-md'
              : 'border-amber-200 shadow-sm hover:border-amber-300 hover:shadow-panel'
            : expanded
              ? 'border-accent/50 shadow-md'
              : 'border-line shadow-sm hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-panel'
      }`}
    >
      <button
        aria-controls={detailsId}
        aria-expanded={expanded}
        aria-label={`${expanded ? 'Collapse' : 'Expand'} ${title} workflow event details`}
        className="block w-full px-4 py-3 text-left focus-visible:outline focus-visible:outline-2 focus-visible:outline-inset focus-visible:outline-accent"
        onClick={(event) => onToggle(event.currentTarget)}
        type="button"
      >
        <span className="flex items-start justify-between gap-3">
          <span className="min-w-0">
            <span className="block truncate text-sm font-semibold text-ink-950">{title}</span>
            {capability && (
              <span className="mt-0.5 block truncate text-xs text-muted">
                {formatGovernanceLabel(capability)}
              </span>
            )}
          </span>
          <ChevronDown
            aria-hidden="true"
            className={`mt-0.5 shrink-0 text-muted transition-transform duration-150 ${
              expanded ? 'rotate-180' : ''
            }`}
            size={16}
          />
        </span>
        {(attentionBadge || primaryBadge || secondaryBadges.length > 0) && (
          <span className="mt-3 flex flex-wrap gap-1.5">
            {attentionBadge && <Badge tone={attentionBadge.tone}>{attentionBadge.label}</Badge>}
            {primaryBadge && <Badge tone={primaryBadge.tone}>{primaryBadge.label}</Badge>}
            {secondaryBadges.map((badge) => (
              <Badge key={badge.key} tone={badge.tone}>{badge.label}</Badge>
            ))}
          </span>
        )}
        {timestamp && (
          <time className="mt-3 block text-[11px] text-muted" dateTime={timestamp}>
            {formatTimestamp(timestamp)}
          </time>
        )}
      </button>
      <div hidden={!expanded} id={detailsId}>
        <ActionDetails events={events} />
      </div>
    </article>
  )
}

export function WorkflowActionCard({
  group,
  expanded,
  attentionLevel = 'NONE',
  onToggle,
}: {
  group: WorkflowTrajectoryGroup
  expanded: boolean
  attentionLevel?: GovernanceAttentionLevel
  onToggle: (element: HTMLButtonElement) => void
}) {
  return (
    <ActionCard
      attentionLevel={attentionLevel}
      group={group}
      expanded={expanded}
      onToggle={onToggle}
    />
  )
}
