import { ArrowUpRight, ExternalLink, ShieldAlert } from 'lucide-react'
import { Link } from 'react-router-dom'
import { EmptyState } from '../../components/feedback/EmptyState'
import { ErrorState } from '../../components/feedback/ErrorState'
import { LoadingState } from '../../components/feedback/LoadingState'
import { Panel } from '../../components/ui/Panel'
import type { GovernanceInterventionResponse } from '../../types/intervention'
import { useInterventions } from '../../hooks/useInterventions'
import { formatInterventionValue, InterventionStatusBadge } from './InterventionStatusBadge'

function formatTimestamp(timestamp: string | null): string {
  if (!timestamp) return '—'
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return 'Unavailable'

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function InterventionCard({ intervention }: { intervention: GovernanceInterventionResponse }) {
  const pending = intervention.status === 'PENDING'

  return (
    <li>
      <article
        className={`rounded-lg border bg-white shadow-panel ${
          pending ? 'border-amber-300 ring-1 ring-amber-100' : 'border-line'
        }`}
      >
        <div className="flex flex-col gap-4 border-b border-line p-4 sm:flex-row sm:items-start sm:justify-between sm:p-5">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              {pending && (
                <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-2.5 py-1 text-xs font-semibold text-amber-900">
                  <ShieldAlert aria-hidden="true" size={13} />
                  Action required
                </span>
              )}
              <InterventionStatusBadge status={intervention.status} />
            </div>
            <Link
              className="mt-3 inline-flex max-w-full items-center gap-1.5 break-all font-mono text-xs font-semibold text-ink-900 hover:text-accent sm:text-sm"
              to={`/interventions/${encodeURIComponent(intervention.interventionId)}`}
            >
              {intervention.interventionId}
              <ArrowUpRight aria-hidden="true" className="shrink-0" size={14} />
            </Link>
            <p className="mt-1 text-sm font-medium text-slate-700">{intervention.agentName}</p>
          </div>

          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-muted sm:justify-end">
            <span>{formatTimestamp(intervention.createdAt)}</span>
            {intervention.resolvedAt && (
              <span>Resolved {formatTimestamp(intervention.resolvedAt)}</span>
            )}
          </div>
        </div>

        <div className="grid gap-4 p-4 sm:grid-cols-2 sm:p-5 xl:grid-cols-4">
          <div className="min-w-0">
            <p className="text-[11px] font-medium uppercase tracking-wide text-muted">Workflow</p>
            <Link
              className="mt-1 inline-flex max-w-full items-center gap-1 break-all font-mono text-xs text-accent hover:underline"
              to={`/workflows/${encodeURIComponent(intervention.workflowId)}`}
            >
              {intervention.workflowId}
              <ExternalLink aria-hidden="true" className="shrink-0" size={12} />
            </Link>
          </div>
          <div>
            <p className="text-[11px] font-medium uppercase tracking-wide text-muted">Type / capability</p>
            <p className="mt-1 text-xs text-slate-700">
              {formatInterventionValue(intervention.workflowType)}
            </p>
            <p className="mt-1 text-xs font-medium text-slate-800">
              {formatInterventionValue(intervention.capability)}
            </p>
          </div>
          <div>
            <p className="text-[11px] font-medium uppercase tracking-wide text-muted">Risk / intervention</p>
            <p className="mt-1 text-xs font-semibold text-slate-800">
              {formatInterventionValue(intervention.riskLevel)}
            </p>
            <p className="mt-1 text-xs text-slate-600">
              {formatInterventionValue(intervention.intervention)}
            </p>
          </div>
          <div>
            <p className="text-[11px] font-medium uppercase tracking-wide text-muted">Resolved by</p>
            <p className="mt-1 break-words text-xs text-slate-700">
              {intervention.resolvedBy ?? 'Not resolved'}
            </p>
          </div>
          <div className="sm:col-span-2 xl:col-span-4">
            <p className="text-[11px] font-medium uppercase tracking-wide text-muted">Reason Guardian intervened</p>
            <p className="mt-1 whitespace-pre-wrap break-words text-sm leading-6 text-slate-700">
              {intervention.reason}
            </p>
          </div>
          {intervention.resolutionReason && (
            <div className="sm:col-span-2 xl:col-span-4">
              <p className="text-[11px] font-medium uppercase tracking-wide text-muted">Resolution reason</p>
              <p className="mt-1 whitespace-pre-wrap break-words text-sm leading-6 text-slate-700">
                {intervention.resolutionReason}
              </p>
            </div>
          )}
        </div>
      </article>
    </li>
  )
}

export function InterventionList() {
  const interventionsQuery = useInterventions()

  if (interventionsQuery.isPending) {
    return <LoadingState label="Loading interventions" />
  }

  if (interventionsQuery.isError) {
    return (
      <div className="p-5">
        <ErrorState message="Intervention records could not be loaded. Please try again later." />
      </div>
    )
  }

  if (interventionsQuery.data.length === 0) {
    return (
      <EmptyState
        title="No interventions recorded"
        description="Governance interventions will appear here when an action requires human review."
      />
    )
  }

  const interventions = [...interventionsQuery.data].sort((left, right) => {
    if (left.status === 'PENDING' && right.status !== 'PENDING') return -1
    if (right.status === 'PENDING' && left.status !== 'PENDING') return 1
    return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()
  })

  return (
    <ul aria-label="Governance interventions" className="space-y-4 p-4 sm:p-5">
      {interventions.map((intervention) => (
        <InterventionCard key={intervention.interventionId} intervention={intervention} />
      ))}
    </ul>
  )
}

export function InterventionListPanel() {
  return (
    <Panel
      title="Intervention records"
      description="Pending interventions are prioritized. Open a record to inspect its governance rationale and, when pending, record a human resolution."
    >
      <InterventionList />
    </Panel>
  )
}
