import { useMemo } from 'react'
import { ArrowUpRight, Workflow } from 'lucide-react'
import { Link } from 'react-router-dom'
import { EmptyState } from '../../components/feedback/EmptyState'
import { ErrorState } from '../../components/feedback/ErrorState'
import { LoadingState } from '../../components/feedback/LoadingState'
import { Panel } from '../../components/ui/Panel'
import type { WorkflowSummaryResponse } from '../../types/workflow'
import { useWorkflowSummaries } from '../../hooks/useWorkflows'

function formatDate(timestamp: string): string {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return 'Unavailable'

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function formatEnum(value: string | null): string {
  return value ? value.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase()) : 'Unavailable'
}

function WorkflowRunRow({ workflow }: { workflow: WorkflowSummaryResponse }) {
  return (
    <Link
      className="group block border-b border-line px-4 py-4 last:border-b-0 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-accent sm:px-5"
      to={`/workflows/${encodeURIComponent(workflow.workflowId)}`}
    >
      <div className="flex min-w-0 items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-slate-100 text-slate-600">
            <Workflow aria-hidden="true" size={17} />
          </span>
          <div className="min-w-0">
            <p className="break-all font-mono text-xs font-semibold text-ink-900 sm:text-sm">
              {workflow.workflowId}
            </p>
            <p className="mt-1 truncate text-xs text-muted">{workflow.agentName}</p>
          </div>
        </div>
        <span className="flex shrink-0 items-center gap-1 text-xs font-medium text-accent opacity-100 sm:opacity-0 sm:group-hover:opacity-100 sm:group-focus-visible:opacity-100">
          Open <ArrowUpRight aria-hidden="true" size={14} />
        </span>
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-3 pl-12 text-xs sm:grid-cols-3 lg:grid-cols-6">
        <div className="min-w-0">
          <dt className="text-muted">Workflow type</dt>
          <dd className="mt-1 truncate font-medium text-slate-700">{formatEnum(workflow.workflowType)}</dd>
        </div>
        <div className="min-w-0">
          <dt className="text-muted">Current state</dt>
          <dd className="mt-1 truncate font-medium text-slate-700">{formatEnum(workflow.currentState)}</dd>
        </div>
        <div className="min-w-0">
          <dt className="text-muted">Current node</dt>
          <dd className="mt-1 truncate font-mono text-slate-700">{workflow.currentNodeId ?? 'Unavailable'}</dd>
        </div>
        <div className="min-w-0">
          <dt className="text-muted">Started</dt>
          <dd className="mt-1 text-slate-700">{formatDate(workflow.startedAt)}</dd>
        </div>
        <div className="min-w-0">
          <dt className="text-muted">Last activity</dt>
          <dd className="mt-1 text-slate-700">{formatDate(workflow.lastEventAt)}</dd>
        </div>
        <div className="min-w-0">
          <dt className="text-muted">Events</dt>
          <dd className="mt-1 font-medium tabular-nums text-slate-700">{workflow.eventCount}</dd>
        </div>
      </dl>
    </Link>
  )
}

export function WorkflowListShell() {
  const workflowsQuery = useWorkflowSummaries()
  const workflows = useMemo(
    () =>
      [...(workflowsQuery.data ?? [])].sort(
        (left, right) =>
          new Date(right.lastEventAt).getTime() - new Date(left.lastEventAt).getTime(),
      ),
    [workflowsQuery.data],
  )

  return (
    <Panel
      title="Workflow runs"
      description="Inspect governed agent runs and open a workflow to review its complete trajectory."
    >
      {workflowsQuery.isPending ? (
        <LoadingState label="Loading workflow runs" />
      ) : workflowsQuery.isError ? (
        <div className="p-5">
          <ErrorState message="Workflow runs could not be loaded. Please try again later." />
        </div>
      ) : workflows.length === 0 ? (
        <EmptyState
          title="No workflow runs found"
          description="Governed workflow runs will appear here when they have been recorded."
        />
      ) : (
        <ul aria-label="Workflow runs" className="divide-y divide-line">
          {workflows.map((workflow) => (
            <li key={workflow.workflowId}>
              <WorkflowRunRow workflow={workflow} />
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}
