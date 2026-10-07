import { ChevronRight } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Panel } from '../../components/ui/Panel'
import { queryKeys } from '../../hooks/queryKeys'
import type { WorkflowSummaryResponse, WorkflowTrajectoryResponse } from '../../types/workflow'
import {
  formatGovernanceLabel,
  getWorkflowGovernanceSummary,
} from './workflowPresentation'

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return 'Time unavailable'

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function OtherWorkflowRow({ workflow }: { workflow: WorkflowSummaryResponse }) {
  const queryClient = useQueryClient()
  const trajectory = queryClient.getQueryData<WorkflowTrajectoryResponse>(
    queryKeys.workflows.trajectory(workflow.workflowId),
  )
  const summary = trajectory ? getWorkflowGovernanceSummary(trajectory) : null
  const attention =
    summary?.attentionLevel === 'BLOCKED'
      ? { label: 'Blocked', className: 'border-red-200 bg-red-50 text-red-800' }
      : summary?.attentionLevel === 'PENDING'
        ? {
            label: 'Approval pending',
            className: 'border-amber-200 bg-amber-50 text-amber-800',
          }
        : summary?.attentionLevel === 'INTERVENTION'
          ? {
              label: 'Intervention',
              className: 'border-amber-200 bg-amber-50 text-amber-800',
            }
          : null

  return (
    <Link
      className="flex items-center gap-3 px-5 py-3 transition-colors hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent"
      to={`/workflows/${encodeURIComponent(workflow.workflowId)}`}
    >
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-medium text-ink-900">
          {workflow.agentName}
        </span>
        <span className="mt-0.5 block truncate text-xs text-muted">
          {workflow.workflowType === 'CUSTOMER_REPORTING'
            ? 'Customer Reporting'
            : 'Customer Support'}
          {' · '}
          {workflow.workflowId}
        </span>
        <span className="mt-0.5 block truncate text-[11px] text-muted">
          Last activity {formatTimestamp(workflow.lastEventAt)}
        </span>
      </span>
      <span className="flex shrink-0 flex-col items-end gap-1">
        <span className="rounded bg-slate-100 px-2.5 py-1 text-xs text-slate-700">
          {workflow.currentState
            ? formatGovernanceLabel(workflow.currentState)
            : 'State unavailable'}
        </span>
        {attention && (
          <span className={`rounded border px-2 py-0.5 text-[10px] font-medium ${attention.className}`}>
            {attention.label}
          </span>
        )}
      </span>
    </Link>
  )
}

export function OtherWorkflowsPanel({
  workflows,
}: {
  workflows: WorkflowSummaryResponse[]
}) {
  return (
    <Panel
      className="p-0"
    >
      <details>
        <summary className="flex cursor-pointer list-none items-center gap-3 px-5 py-4 focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent [&::-webkit-details-marker]:hidden">
          <ChevronRight
            aria-hidden="true"
            className="shrink-0 text-muted transition-transform open:rotate-90"
            size={16}
          />
          <span className="text-sm font-semibold text-ink-950">Other workflows</span>
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600">
            {workflows.length}
          </span>
          <span className="ml-auto hidden text-xs text-muted sm:inline">
            Select a workflow to view its trajectory
          </span>
        </summary>
        <ul className="border-t border-line divide-y divide-line">
          {workflows.map((workflow) => (
            <li key={workflow.workflowId}>
              <OtherWorkflowRow workflow={workflow} />
            </li>
          ))}
        </ul>
      </details>
    </Panel>
  )
}
