import { useMemo } from 'react'
import { Check, CircleSlash, GitBranch, ShieldAlert } from 'lucide-react'
import { EmptyState } from '../../components/feedback/EmptyState'
import { ErrorState } from '../../components/feedback/ErrorState'
import { LoadingState } from '../../components/feedback/LoadingState'
import { Panel } from '../../components/ui/Panel'
import type { GovernanceInterventionResponse } from '../../types/intervention'
import type { WorkflowSummaryResponse } from '../../types/workflow'
import { useDashboardQueries } from '../../hooks/useDashboardQueries'

type DashboardActivity =
  | {
      type: 'workflow'
      id: string
      timestamp: string
      workflow: WorkflowSummaryResponse
    }
  | {
      type: 'intervention'
      id: string
      timestamp: string
      intervention: GovernanceInterventionResponse
    }

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return 'Time unavailable'

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function titleCase(value: string): string {
  return value
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function DashboardActivityRow({ activity }: { activity: DashboardActivity }) {
  if (activity.type === 'workflow') {
    const { workflow } = activity

    return (
      <li className="flex flex-col gap-2 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <p className="text-sm font-medium text-ink-900">Workflow run</p>
          <p className="mt-1 truncate text-xs text-muted">
            {workflow.agentName} · {titleCase(workflow.workflowType)} · {workflow.workflowId}
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-3 sm:justify-end">
          <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-700">
            {workflow.currentState ? titleCase(workflow.currentState) : 'State unavailable'}
          </span>
          <time className="text-xs text-muted" dateTime={activity.timestamp}>
            {formatTimestamp(activity.timestamp)}
          </time>
        </div>
      </li>
    )
  }

  const { intervention } = activity
  const statusStyle =
    intervention.status === 'PENDING'
      ? 'bg-amber-50 text-amber-800'
      : intervention.status === 'REJECTED'
        ? 'bg-red-50 text-red-700'
        : 'bg-slate-100 text-slate-700'

  return (
    <li className="flex flex-col gap-2 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <p className="text-sm font-medium text-ink-900">
          {titleCase(intervention.status)} intervention
        </p>
        <p className="mt-1 truncate text-xs text-muted">
          {intervention.agentName} · {titleCase(intervention.capability)} ·{' '}
          {titleCase(intervention.riskLevel)} risk
        </p>
      </div>
      <div className="flex shrink-0 flex-wrap items-center gap-3 sm:justify-end">
        <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusStyle}`}>
          {titleCase(intervention.status)}
        </span>
        <time className="text-xs text-muted" dateTime={activity.timestamp}>
          {formatTimestamp(activity.timestamp)}
        </time>
      </div>
    </li>
  )
}

export function DashboardOverview() {
  const { workflows, interventions } = useDashboardQueries()

  const recentActivity = useMemo<DashboardActivity[]>(() => {
    const workflowActivity: DashboardActivity[] = (workflows.data ?? []).map((workflow) => ({
      type: 'workflow',
      id: workflow.workflowId,
      timestamp: workflow.lastEventAt,
      workflow,
    }))
    const interventionActivity: DashboardActivity[] = (interventions.data ?? []).map(
      (intervention) => ({
        type: 'intervention',
        id: intervention.interventionId,
        timestamp: intervention.resolvedAt ?? intervention.createdAt,
        intervention,
      }),
    )

    return [...workflowActivity, ...interventionActivity].sort(
      (left, right) =>
        new Date(right.timestamp).getTime() - new Date(left.timestamp).getTime(),
    )
  }, [workflows.data, interventions.data])

  if (workflows.isPending && interventions.isPending) {
    return <LoadingState label="Loading governance overview" />
  }

  const workflowCount = workflows.data?.length
  const pendingInterventionCount = interventions.data?.filter(
    (intervention) => intervention.status === 'PENDING',
  ).length

  const metrics = [
    {
      label: 'Total workflows',
      icon: GitBranch,
      value: workflowCount === undefined ? '—' : String(workflowCount),
      detail: workflows.isPending
        ? 'Loading workflow summaries'
        : workflows.isError
          ? 'Workflow summaries unavailable'
          : workflowCount === 0
            ? 'No workflow runs recorded'
            : 'From workflow summaries',
    },
    {
      label: 'Allowed actions',
      icon: Check,
      value: '—',
      detail: 'Not available from the summary APIs',
    },
    {
      label: 'Blocked actions',
      icon: CircleSlash,
      value: '—',
      detail: 'Not available from the summary APIs',
    },
    {
      label: 'Pending interventions',
      icon: ShieldAlert,
      value: pendingInterventionCount === undefined ? '—' : String(pendingInterventionCount),
      detail: interventions.isPending
        ? 'Loading intervention records'
        : interventions.isError
          ? 'Intervention records unavailable'
          : pendingInterventionCount === 0
            ? 'No pending interventions'
            : 'Status: pending',
    },
  ]

  return (
    <div className="space-y-6">
      {workflows.isError && (
        <ErrorState message="Workflow summaries could not be loaded. Other dashboard data remains available." />
      )}
      {interventions.isError && (
        <ErrorState message="Interventions could not be loaded. Other dashboard data remains available." />
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {metrics.map(({ label, icon: Icon, value, detail }) => (
          <Panel key={label} className="p-5">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-sm font-medium text-slate-600">{label}</p>
                <p className="mt-4 text-2xl font-semibold tracking-tight text-ink-950">{value}</p>
              </div>
              <span className="flex h-9 w-9 items-center justify-center rounded-md bg-slate-100 text-slate-600">
                <Icon aria-hidden="true" size={17} />
              </span>
            </div>
            <p className="mt-2 text-xs text-muted">{detail}</p>
          </Panel>
        ))}
      </div>

      <Panel
        title="Recent governance activity"
        description="Recent workflow runs and interventions, ordered by their latest available timestamps. Workflow entries summarize a run; they are not individual audit events."
      >
        {recentActivity.length > 0 ? (
          <ul className="divide-y divide-line">
            {recentActivity.map((activity) => (
              <DashboardActivityRow
                key={`${activity.type}:${activity.id}`}
                activity={activity}
              />
            ))}
          </ul>
        ) : workflows.isPending || interventions.isPending ? (
          <LoadingState label="Loading recent activity" />
        ) : workflows.isError || interventions.isError ? (
          <EmptyState
            title="Recent activity unavailable"
            description="Recent activity could not be displayed because one or more data requests failed."
          />
        ) : (
          <EmptyState
            title="No recent governance activity"
            description="There are no workflow runs or intervention records to display yet."
          />
        )}
      </Panel>
    </div>
  )
}
