import { ArrowUpRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { EmptyState } from '../../components/feedback/EmptyState'
import { ErrorState } from '../../components/feedback/ErrorState'
import { LoadingState } from '../../components/feedback/LoadingState'
import { Panel } from '../../components/ui/Panel'
import type { PolicyDefinition } from '../../types/policy'
import { usePolicies } from '../../hooks/usePolicies'
import { PolicyStatusBadge } from './PolicyStatusBadge'

export function formatPolicyValue(value: string | null | undefined): string {
  if (value == null || value.trim() === '') return 'Not specified'
  return value.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

export function policyConditionPhrase(operator: PolicyDefinition['conditionOperator']): string {
  switch (operator) {
    case 'EQUALS':
      return 'equals'
    case 'NOT_EQUALS':
      return 'does not equal'
    case 'IN':
      return 'is one of'
  }
}

export function policyTargetDescription(policy: PolicyDefinition): string {
  const targets = [policy.targetToolName, policy.targetCapability]
    .filter((value): value is string => value !== null && value.trim() !== '')
    .map(formatPolicyValue)
  return targets.length > 0 ? targets.join(' / ') : 'the targeted action'
}

export function policyExplanation(policy: PolicyDefinition): string {
  const action = policy.action === 'BLOCK' ? 'Blocks' : 'Allows'
  return `${action} ${policyTargetDescription(policy)} when ${formatPolicyValue(policy.conditionField)} ${policyConditionPhrase(policy.conditionOperator)} ${policy.conditionValue}.`
}

function PolicyCard({ policy }: { policy: PolicyDefinition }) {
  return (
    <li>
      <Link
        className="group block border-b border-line px-4 py-4 last:border-b-0 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-accent sm:px-5"
        to={`/policies/${encodeURIComponent(policy.policyId)}`}
      >
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="font-semibold text-ink-950">{policy.name}</h3>
              <PolicyStatusBadge enabled={policy.enabled} />
            </div>
            <p className="mt-1 break-all font-mono text-xs text-muted">{policy.policyId}</p>
            <p className="mt-2 max-w-3xl text-sm leading-5 text-slate-600">
              {policy.description?.trim() || 'Description not specified.'}
            </p>
          </div>
          <span className="inline-flex shrink-0 items-center gap-1 text-xs font-medium text-accent sm:opacity-0 sm:group-hover:opacity-100 sm:group-focus-visible:opacity-100">
            Inspect policy <ArrowUpRight aria-hidden="true" size={14} />
          </span>
        </div>

        <div className="mt-4 grid gap-3 sm:grid-cols-3">
          <div className="min-w-0 rounded-md border border-line bg-slate-50 p-3">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-muted">Target</p>
            <p className="mt-1 break-words text-sm font-medium text-slate-800">
              {policyTargetDescription(policy)}
            </p>
          </div>
          <div className="min-w-0 rounded-md border border-line bg-slate-50 p-3">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-muted">When</p>
            <p className="mt-1 break-words text-sm text-slate-700">
              <span className="font-medium">{formatPolicyValue(policy.conditionField)}</span>
              {' '}
              {policyConditionPhrase(policy.conditionOperator)}
              {' '}
              <span className="font-mono text-xs">{policy.conditionValue}</span>
            </p>
          </div>
          <div className="min-w-0 rounded-md border border-line bg-slate-50 p-3">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-muted">Action / priority</p>
            <p className="mt-1 text-sm font-semibold text-slate-800">
              {formatPolicyValue(policy.action)}
              <span className="ml-2 text-xs font-normal text-muted">Priority {policy.priority}</span>
            </p>
          </div>
        </div>
      </Link>
    </li>
  )
}

export function PolicyListShell() {
  const policiesQuery = usePolicies()

  return (
    <Panel
      title="Enterprise policy controls"
      description="These enterprise-defined rules are evaluated by Guardian when agent actions are governed."
    >
      {policiesQuery.isPending ? (
        <LoadingState label="Loading policy controls" />
      ) : policiesQuery.isError ? (
        <div className="p-5">
          <ErrorState message="Policy controls could not be loaded. Please try again later." />
        </div>
      ) : policiesQuery.data.length === 0 ? (
        <EmptyState
          title="No policy controls found"
          description="No enterprise policy records were returned by the backend."
        />
      ) : (
        <ul aria-label="Enterprise policy controls" className="divide-y divide-line">
          {policiesQuery.data.map((policy) => (
            <PolicyCard key={policy.policyId} policy={policy} />
          ))}
        </ul>
      )}
    </Panel>
  )
}
