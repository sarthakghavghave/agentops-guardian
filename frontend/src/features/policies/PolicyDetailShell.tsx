import { ArrowLeft } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { EmptyState } from '../../components/feedback/EmptyState'
import { ErrorState } from '../../components/feedback/ErrorState'
import { LoadingState } from '../../components/feedback/LoadingState'
import { Panel } from '../../components/ui/Panel'
import { usePolicy } from '../../hooks/usePolicies'
import type { PolicyDefinition } from '../../types/policy'
import {
  policyConditionPhrase,
  policyExplanation,
  policyTargetDescription,
} from './PolicyListShell'
import { PolicyStatusBadge } from './PolicyStatusBadge'

function PolicyDetail({ policy }: { policy: PolicyDefinition }) {
  return (
    <div className="space-y-6">
      <header className="space-y-3">
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="break-words text-2xl font-semibold tracking-tight text-ink-950">
            {policy.name}
          </h2>
          <PolicyStatusBadge enabled={policy.enabled} />
        </div>
        <p className="break-all font-mono text-xs text-muted">{policy.policyId}</p>
        <p className="max-w-3xl text-sm leading-6 text-slate-600">
          {policy.description?.trim() || 'Description not specified.'}
        </p>
      </header>

      <Panel
        title="Enforcement rule"
        description={
          policy.enabled
            ? 'Enterprise-defined policy fields evaluated by Guardian.'
            : 'This policy is disabled and is not currently enforcing this rule.'
        }
      >
        <div className="grid min-w-0 gap-3 p-4 sm:grid-cols-3 sm:p-5">
          <div className="min-w-0 rounded-md border border-line bg-slate-50 p-4">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-muted">Target</p>
            <p className="mt-2 break-all font-mono text-sm font-semibold text-slate-800">
              {policyTargetDescription(policy)}
            </p>
          </div>
          <div className="min-w-0 rounded-md border border-line bg-slate-50 p-4">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-muted">When</p>
            <div className="mt-2 text-sm text-slate-800">
              <p className="break-all font-mono font-semibold">{policy.conditionField}</p>
              <p className="my-1 text-xs text-muted">
                {policyConditionPhrase(policy.conditionOperator)} ({policy.conditionOperator})
              </p>
              <p className="break-all font-mono font-semibold">{policy.conditionValue}</p>
            </div>
          </div>
          <div className="min-w-0 rounded-md border border-line bg-slate-50 p-4">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-muted">Action</p>
            <p
              className={`mt-2 inline-flex rounded-md px-2.5 py-1 font-mono text-sm font-bold ${
                policy.action === 'BLOCK'
                  ? 'bg-red-50 text-red-800'
                  : 'bg-emerald-50 text-emerald-800'
              }`}
            >
              {policy.action}
            </p>
          </div>
        </div>
      </Panel>

      <Panel
        title="Policy details"
        description="Values below are read directly from the backend policy record."
      >
        <dl className="grid min-w-0 gap-x-6 gap-y-5 p-4 sm:grid-cols-2 sm:p-5">
          <div className="min-w-0">
            <dt className="text-xs font-medium text-muted">Enabled state</dt>
            <dd className="mt-1"><PolicyStatusBadge enabled={policy.enabled} /></dd>
          </div>
          <div className="min-w-0">
            <dt className="text-xs font-medium text-muted">Priority</dt>
            <dd className="mt-1 text-sm font-medium text-slate-800">{policy.priority}</dd>
          </div>
          <div className="min-w-0">
            <dt className="text-xs font-medium text-muted">Target tool</dt>
            <dd className="mt-1 break-words font-mono text-sm text-slate-800">
              {policy.targetToolName?.trim() || 'Not specified'}
            </dd>
          </div>
          <div className="min-w-0">
            <dt className="text-xs font-medium text-muted">Target capability</dt>
            <dd className="mt-1 break-words font-mono text-sm text-slate-800">
              {policy.targetCapability?.trim() || 'Not specified'}
            </dd>
          </div>
          <div className="min-w-0">
            <dt className="text-xs font-medium text-muted">Condition field</dt>
            <dd className="mt-1 break-words font-mono text-sm text-slate-800">{policy.conditionField}</dd>
          </div>
          <div className="min-w-0">
            <dt className="text-xs font-medium text-muted">Condition operator</dt>
            <dd className="mt-1 break-words font-mono text-sm text-slate-800">{policy.conditionOperator}</dd>
          </div>
          <div className="min-w-0">
            <dt className="text-xs font-medium text-muted">Condition value</dt>
            <dd className="mt-1 break-words font-mono text-sm text-slate-800">{policy.conditionValue}</dd>
          </div>
          <div className="min-w-0">
            <dt className="text-xs font-medium text-muted">Action</dt>
            <dd className="mt-1 break-words font-mono text-sm font-semibold text-slate-800">{policy.action}</dd>
          </div>
        </dl>
      </Panel>

      <Panel title="Policy interpretation">
        <p className="p-4 text-sm leading-6 text-slate-700 sm:p-5">{policyExplanation(policy)}</p>
      </Panel>
    </div>
  )
}

export function PolicyDetailShell() {
  const { policyId = '' } = useParams<{ policyId: string }>()
  const policyQuery = usePolicy(policyId)

  if (policyQuery.isPending) {
    return <LoadingState label="Loading policy details" />
  }

  if (policyQuery.isError && !policyQuery.data) {
    const isNotFound = policyQuery.error instanceof ApiError && policyQuery.error.status === 404
    return (
      <div className="space-y-5">
        <PolicyListLink />
        {isNotFound ? (
          <EmptyState
            title="Policy not found"
            description="This policy record could not be found. It may no longer be available, or the identifier may be incorrect."
          />
        ) : (
          <ErrorState message="Policy details could not be loaded. Please try again later." />
        )}
      </div>
    )
  }

  if (!policyQuery.data) {
    return (
      <div className="space-y-5">
        <PolicyListLink />
        <ErrorState message="The policy response was unavailable. Please try again later." />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <PolicyListLink />
        <Link
          className="inline-flex min-h-10 items-center justify-center gap-2 rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2"
          to={`/policies/${encodeURIComponent(policyQuery.data.policyId)}/edit`}
        >
          Edit policy
        </Link>
      </div>
      {policyQuery.isError && (
        <ErrorState message="The latest refresh failed. Previously loaded policy information is still shown." />
      )}
      <PolicyDetail policy={policyQuery.data} />
    </div>
  )
}

function PolicyListLink() {
  return (
    <Link
      className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-accent"
      to="/policies"
    >
      <ArrowLeft aria-hidden="true" size={16} />
      Back to policies
    </Link>
  )
}
