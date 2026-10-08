import { useState } from 'react'
import type { ReactNode } from 'react'
import { ArrowLeft, ExternalLink, ShieldCheck, ShieldX } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { EmptyState } from '../../components/feedback/EmptyState'
import { ErrorState } from '../../components/feedback/ErrorState'
import { LoadingState } from '../../components/feedback/LoadingState'
import { Panel } from '../../components/ui/Panel'
import type { GovernanceInterventionResponse } from '../../types/intervention'
import { useIntervention, useResolveIntervention } from '../../hooks/useInterventions'
import {
  formatInterventionValue,
  InterventionStatusBadge,
} from './InterventionStatusBadge'
import {
  InterventionResolutionDialog,
  type ResolutionAction,
} from './InterventionResolutionDialog'

function formatTimestamp(timestamp: string | null): string {
  if (!timestamp) return '—'
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return 'Unavailable'

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'full',
    timeStyle: 'long',
  }).format(date)
}

function DetailField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-[11px] font-medium uppercase tracking-wide text-muted">{label}</dt>
      <dd className="mt-1 break-words text-sm leading-6 text-ink-900">{children}</dd>
    </div>
  )
}

function ResolutionPanel({ intervention }: { intervention: GovernanceInterventionResponse }) {
  if (intervention.status === 'PENDING') return null

  const title =
    intervention.status === 'APPROVED'
      ? 'Resolved — Approved'
      : intervention.status === 'REJECTED'
        ? 'Resolved — Rejected'
        : 'Expired — No action available'

  return (
    <div className="rounded-md border border-line bg-slate-50 p-4">
      <h3 className="text-sm font-semibold text-ink-900">{title}</h3>
      {intervention.status === 'APPROVED' && (
        <p className="mt-1 text-sm leading-6 text-slate-600">
          Approval was recorded. The original tool call is not automatically replayed.
        </p>
      )}
      {intervention.status === 'REJECTED' && (
        <p className="mt-1 text-sm leading-6 text-slate-600">
          The intervention was rejected. The original tool call remains blocked.
        </p>
      )}
      <dl className="mt-3 grid gap-3 sm:grid-cols-2">
        <DetailField label="Resolved at">{formatTimestamp(intervention.resolvedAt)}</DetailField>
        <DetailField label="Resolved by">{intervention.resolvedBy ?? 'Unavailable'}</DetailField>
        {intervention.resolutionReason && (
          <div className="sm:col-span-2">
            <DetailField label="Resolution reason">{intervention.resolutionReason}</DetailField>
          </div>
        )}
      </dl>
    </div>
  )
}

function InterventionRecord({
  intervention: initialIntervention,
}: {
  intervention: GovernanceInterventionResponse
}) {
  const [dialogAction, setDialogAction] = useState<ResolutionAction | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const resolution = useResolveIntervention(initialIntervention.interventionId)
  const isMutationPending = resolution.approve.isPending || resolution.reject.isPending
  const currentIntervention =
    resolution.approve.data ?? resolution.reject.data ?? initialIntervention

  async function submitResolution(resolvedBy: string, resolutionReason: string) {
    if (!dialogAction || currentIntervention.status !== 'PENDING') return

    const action = dialogAction
    const request = { resolvedBy, resolutionReason }
    try {
      if (action === 'approve') {
        await resolution.approve.mutateAsync(request)
        setSuccessMessage(
          'Approval recorded. The original tool call is not automatically replayed.',
        )
      } else {
        await resolution.reject.mutateAsync(request)
        setSuccessMessage('Intervention rejected. The original tool call remains blocked.')
      }
      setDialogAction(null)
    } catch {
      // TanStack Query retains the mutation error for display in the confirmation dialog.
    }
  }

  const mutationError = resolution.approve.error ?? resolution.reject.error
  const mutationErrorMessage =
    mutationError instanceof ApiError && mutationError.status === 409
      ? 'This intervention is no longer pending. Refresh the record to see its current status.'
      : 'The resolution could not be recorded. The intervention status has not been changed in this view.'

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link
          className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-accent"
          to="/interventions"
        >
          <ArrowLeft aria-hidden="true" size={16} />
          Back to interventions
        </Link>
        <InterventionStatusBadge status={currentIntervention.status} />
      </div>

      <div>
        <p className="text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Governance intervention
        </p>
        <h2 className="mt-1 break-all font-mono text-lg font-semibold text-ink-950">
          {currentIntervention.interventionId}
        </h2>
      </div>

      {successMessage && (
        <div
          className="flex items-start gap-3 rounded-md border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900"
          role="status"
        >
          <ShieldCheck aria-hidden="true" className="mt-0.5 shrink-0" size={17} />
          <p className="leading-6">{successMessage}</p>
        </div>
      )}

      <Panel
        title="Why Guardian intervened"
        description="This governance rationale comes from the intervention record."
      >
        <div className="p-5 sm:p-6">
          <p className="whitespace-pre-wrap break-words text-sm leading-7 text-ink-900">
            {currentIntervention.reason}
          </p>
        </div>
      </Panel>

      <Panel title="Intervention details" description="Recorded governance context and lifecycle information.">
        <dl className="grid gap-x-6 gap-y-5 p-5 sm:grid-cols-2 sm:p-6 xl:grid-cols-3">
          <DetailField label="Agent">{currentIntervention.agentName}</DetailField>
          <DetailField label="Workflow">
            <Link
              className="inline-flex items-center gap-1.5 break-all font-mono text-xs text-accent hover:underline"
              to={`/workflows/${encodeURIComponent(currentIntervention.workflowId)}`}
            >
              {currentIntervention.workflowId}
              <ExternalLink aria-hidden="true" className="shrink-0" size={13} />
            </Link>
          </DetailField>
          <DetailField label="Workflow type">
            {formatInterventionValue(currentIntervention.workflowType)}
          </DetailField>
          <DetailField label="Capability">
            {formatInterventionValue(currentIntervention.capability)}
          </DetailField>
          <DetailField label="Risk level">
            <span
              className={`font-semibold ${
                currentIntervention.riskLevel === 'HIGH' || currentIntervention.riskLevel === 'CRITICAL'
                  ? 'text-red-800'
                  : currentIntervention.riskLevel === 'MODERATE'
                    ? 'text-amber-800'
                    : 'text-slate-800'
              }`}
            >
              {formatInterventionValue(currentIntervention.riskLevel)}
            </span>
          </DetailField>
          <DetailField label="Intervention type">
            {formatInterventionValue(currentIntervention.intervention)}
          </DetailField>
          <DetailField label="Created at">{formatTimestamp(currentIntervention.createdAt)}</DetailField>
          <DetailField label="Resolved at">{formatTimestamp(currentIntervention.resolvedAt)}</DetailField>
          <DetailField label="Resolved by">{currentIntervention.resolvedBy ?? '—'}</DetailField>
          {currentIntervention.resolutionReason && (
            <div className="sm:col-span-2 xl:col-span-3">
              <DetailField label="Resolution reason">{currentIntervention.resolutionReason}</DetailField>
            </div>
          )}
        </dl>
      </Panel>

      <Panel
        title="Human resolution"
        description="Policy determines whether an action is permitted. Risk can require additional human governance; recording approval does not bypass policy or replay the original tool call."
      >
        <div className="space-y-4 p-5 sm:p-6">
          <ResolutionPanel intervention={currentIntervention} />
          {currentIntervention.status === 'PENDING' ? (
            <div className="flex flex-col gap-3 sm:flex-row">
              <button
                className="inline-flex items-center justify-center gap-2 rounded-md bg-accent px-4 py-2.5 text-sm font-semibold text-white hover:bg-accent/90 disabled:cursor-not-allowed disabled:opacity-60"
                disabled={isMutationPending}
                onClick={() => {
                  resolution.approve.reset()
                  resolution.reject.reset()
                  setSuccessMessage(null)
                  setDialogAction('approve')
                }}
                type="button"
              >
                <ShieldCheck aria-hidden="true" size={16} />
                Approve
              </button>
              <button
                className="inline-flex items-center justify-center gap-2 rounded-md border border-red-300 bg-white px-4 py-2.5 text-sm font-semibold text-red-800 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-60"
                disabled={isMutationPending}
                onClick={() => {
                  resolution.approve.reset()
                  resolution.reject.reset()
                  setSuccessMessage(null)
                  setDialogAction('reject')
                }}
                type="button"
              >
                <ShieldX aria-hidden="true" size={16} />
                Reject
              </button>
            </div>
          ) : (
            <p className="text-sm text-muted">No resolution actions are available for this status.</p>
          )}
        </div>
      </Panel>

      {dialogAction && currentIntervention.status === 'PENDING' && (
        <InterventionResolutionDialog
          action={dialogAction}
          errorMessage={mutationError ? mutationErrorMessage : null}
          intervention={currentIntervention}
          isPending={isMutationPending}
          onClose={() => setDialogAction(null)}
          onSubmit={submitResolution}
        />
      )}
    </div>
  )
}

export function InterventionDetail() {
  const { interventionId = '' } = useParams<{ interventionId: string }>()
  const interventionQuery = useIntervention(interventionId)

  if (interventionQuery.isPending) {
    return <LoadingState label="Loading intervention record" />
  }

  if (interventionQuery.isError && !interventionQuery.data) {
    const isNotFound =
      interventionQuery.error instanceof ApiError && interventionQuery.error.status === 404

    return isNotFound ? (
      <EmptyState
        title="Intervention not found"
        description="This governance intervention could not be found. It may no longer be available, or the identifier may be incorrect."
      />
    ) : (
      <ErrorState message="Intervention details could not be loaded. Please try again later." />
    )
  }

  if (!interventionQuery.data) {
    return <ErrorState message="The intervention response was unavailable. Please try again later." />
  }

  return (
    <div className="space-y-6">
      {interventionQuery.isError && (
        <ErrorState message="The latest refresh failed. Previously loaded intervention information is still shown." />
      )}
      <InterventionRecord intervention={interventionQuery.data} />
    </div>
  )
}
