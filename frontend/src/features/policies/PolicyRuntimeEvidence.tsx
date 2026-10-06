import { ArrowUpRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ErrorState } from '../../components/feedback/ErrorState'
import { LoadingState } from '../../components/feedback/LoadingState'
import { Panel } from '../../components/ui/Panel'
import { usePolicyRuntimeEvidence } from '../../hooks/usePolicyRuntimeEvidence'
import type { PolicyDefinition } from '../../types/policy'
import { formatPolicyValue } from './PolicyListShell'

type Props = {
  policy: PolicyDefinition
}

type EvidenceDecision = 'ALLOW' | 'BLOCK' | 'OTHER'

function decisionCategory(decision: string): EvidenceDecision {
  if (decision === 'ALLOW' || decision === 'BLOCK') return decision
  return 'OTHER'
}

function decisionStyle(decision: string): string {
  switch (decisionCategory(decision)) {
    case 'ALLOW':
      return 'bg-emerald-50 text-emerald-800'
    case 'BLOCK':
      return 'bg-red-50 text-red-800'
    case 'OTHER':
      return 'bg-slate-100 text-slate-700'
  }
}

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return timestamp || 'Unavailable'

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function RuntimeDecisionList({ evidence }: { evidence: ReturnType<typeof usePolicyRuntimeEvidence>['evidence'] }) {
  const sortedEvidence = [...evidence].sort(
    (left, right) =>
      new Date(right.event.timestamp).getTime() - new Date(left.event.timestamp).getTime(),
  )

  return (
    <ol className="divide-y divide-line">
      {sortedEvidence.map(({ workflowId, event }, index) => {
        const decision = event.policyDecision ?? 'UNKNOWN'
        const toolOrCapability = event.capability ?? event.toolName ?? 'Unavailable'
        return (
          <li className="p-4 sm:px-5" key={`${workflowId}-${event.timestamp}-${event.toolCallId ?? index}`}>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div className="min-w-0 space-y-3">
                <div className="flex flex-wrap items-center gap-2">
                  <span className={`inline-flex rounded-full px-2.5 py-1 font-mono text-xs font-semibold ${decisionStyle(decision)}`}>
                    {decision}
                  </span>
                  <span className="text-xs text-muted">{formatTimestamp(event.timestamp)}</span>
                </div>
                <div>
                  <p className="text-[11px] font-semibold uppercase tracking-wide text-muted">Policy reason</p>
                  <p className="mt-1 whitespace-pre-wrap break-words text-sm leading-5 text-slate-700">
                    {event.policyReason?.trim() || 'No policy reason recorded.'}
                  </p>
                </div>
                <dl className="grid gap-x-5 gap-y-2 text-xs sm:grid-cols-2">
                  <div className="min-w-0">
                    <dt className="text-muted">Workflow</dt>
                    <dd className="mt-0.5 break-all font-mono text-slate-700">{workflowId}</dd>
                  </div>
                  <div>
                    <dt className="text-muted">Capability / tool</dt>
                    <dd className="mt-0.5 break-words font-medium text-slate-700">
                      {formatPolicyValue(toolOrCapability)}
                    </dd>
                  </div>
                </dl>
              </div>
              <Link
                className="inline-flex shrink-0 items-center gap-1.5 self-start text-sm font-medium text-accent hover:underline"
                to={`/workflows/${encodeURIComponent(workflowId)}`}
              >
                View workflow <ArrowUpRight aria-hidden="true" size={14} />
              </Link>
            </div>
          </li>
        )
      })}
    </ol>
  )
}

export function PolicyRuntimeEvidence({ policy }: Props) {
  const { evidence, isError, isLoading } = usePolicyRuntimeEvidence(policy.policyId)

  let evidenceContent
  if (isLoading) {
    evidenceContent = <LoadingState label="Loading policy runtime evidence" />
  } else if (isError) {
    evidenceContent = (
      <div className="p-4 sm:p-5">
        <ErrorState message="Runtime evidence unavailable. One or more workflow trajectory requests could not be loaded, so policy evidence counts cannot be determined." />
      </div>
    )
  } else if (evidence.length === 0) {
    evidenceContent = (
      <p className="p-5 text-sm text-slate-600">No runtime evidence recorded yet.</p>
    )
  } else {
    const allowed = evidence.filter((item) => item.event.policyDecision === 'ALLOW').length
    const blocked = evidence.filter((item) => item.event.policyDecision === 'BLOCK').length
    const other = evidence.length - allowed - blocked

    evidenceContent = (
      <>
        <dl className="grid grid-cols-2 divide-x divide-y divide-line border-b border-line sm:grid-cols-4 sm:divide-y-0">
          <div className="p-4 sm:px-5">
            <dt className="text-xs text-muted">Evaluations</dt>
            <dd className="mt-1 text-xl font-semibold tabular-nums text-ink-950">{evidence.length}</dd>
          </div>
          <div className="p-4 sm:px-5">
            <dt className="text-xs text-muted">Allowed</dt>
            <dd className="mt-1 text-xl font-semibold tabular-nums text-emerald-800">{allowed}</dd>
          </div>
          <div className="p-4 sm:px-5">
            <dt className="text-xs text-muted">Blocked</dt>
            <dd className="mt-1 text-xl font-semibold tabular-nums text-red-800">{blocked}</dd>
          </div>
          <div className="p-4 sm:px-5">
            <dt className="text-xs text-muted">Other decisions</dt>
            <dd className="mt-1 text-xl font-semibold tabular-nums text-slate-700">{other}</dd>
          </div>
        </dl>
        <div className="border-b border-line px-4 py-3 sm:px-5">
          <h3 className="text-sm font-semibold text-ink-950">Recent decisions</h3>
        </div>
        <RuntimeDecisionList evidence={evidence} />
      </>
    )
  }

  return (
    <Panel
      title="Runtime Evidence"
      description="Recorded policy-decision events matched by exact policy ID in workflow trajectories. Risk interventions are separate governance events."
    >
      {evidenceContent}
    </Panel>
  )
}
