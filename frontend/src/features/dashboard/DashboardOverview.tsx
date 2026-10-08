import { useMemo } from 'react'
import { Check, CircleSlash, GitBranch, ShieldAlert } from 'lucide-react'
import { ErrorState } from '../../components/feedback/ErrorState'
import { Panel } from '../../components/ui/Panel'
import type { LiveGovernanceState } from '../../hooks/useLiveGovernanceEvents'
import { useDashboardQueries } from '../../hooks/useDashboardQueries'
import type { WorkflowSummaryResponse } from '../../types/workflow'
import { LiveWorkflowPanel } from './LiveWorkflowPanel'
import { OtherWorkflowsPanel } from './OtherWorkflowsPanel'

function latestFirst(
  workflows: WorkflowSummaryResponse[],
): WorkflowSummaryResponse[] {
  return [...workflows].sort(
    (left, right) =>
      new Date(right.lastEventAt).getTime() - new Date(left.lastEventAt).getTime(),
  )
}

export function DashboardOverview({ liveState }: { liveState: LiveGovernanceState }) {
  const { workflows, interventions } = useDashboardQueries()
  const sortedWorkflows = useMemo(
    () => latestFirst(workflows.data ?? []),
    [workflows.data],
  )
  const latestLiveEvent = liveState.events[liveState.events.length - 1]
  const focusedWorkflowId = latestLiveEvent?.workflowId ?? sortedWorkflows[0]?.workflowId ?? null
  const fallbackSummary = sortedWorkflows.find(
    (workflow) => workflow.workflowId === focusedWorkflowId,
  )
  const otherWorkflows = sortedWorkflows.filter(
    (workflow) => workflow.workflowId !== focusedWorkflowId,
  )

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
        <ErrorState message="Workflow summaries could not be loaded. Live event focus and trajectory data remain available when received." />
      )}
      {interventions.isError && (
        <ErrorState message="Interventions could not be loaded. Workflow data remains available." />
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

      <LiveWorkflowPanel
        fallbackSummary={fallbackSummary}
        focusedWorkflowId={focusedWorkflowId}
        hasLiveActivity={latestLiveEvent !== undefined}
        loadingSummaries={workflows.isPending}
        summariesUnavailable={workflows.isError}
      />

      {otherWorkflows.length > 0 && <OtherWorkflowsPanel workflows={otherWorkflows} />}
    </div>
  )
}
