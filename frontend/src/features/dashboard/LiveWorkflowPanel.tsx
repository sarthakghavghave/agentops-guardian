import { Panel } from '../../components/ui/Panel'
import type { WorkflowSummaryResponse } from '../../types/workflow'
import { WorkflowTrajectoryRail } from './WorkflowTrajectoryRail'

export function LiveWorkflowPanel({
  focusedWorkflowId,
  hasLiveActivity,
  fallbackSummary,
  loadingSummaries,
  summariesUnavailable,
}: {
  focusedWorkflowId: string | null
  hasLiveActivity: boolean
  fallbackSummary?: WorkflowSummaryResponse
  loadingSummaries: boolean
  summariesUnavailable: boolean
}) {
  const title = hasLiveActivity ? 'Live workflow' : 'Latest workflow'

  return (
    <Panel
      title={title}
      description={
        hasLiveActivity
          ? 'Focused on the workflow associated with the latest received governance event. The persisted trajectory is authoritative.'
          : 'Most recently active workflow from persisted workflow summaries.'
      }
    >
      {!focusedWorkflowId ? (
        <div className="px-5 py-10 text-center">
          <h3 className="text-sm font-medium text-ink-900">
            {loadingSummaries
              ? 'Loading workflow summaries…'
              : summariesUnavailable
                ? 'Workflow summaries unavailable'
                : 'No governed workflows yet.'}
          </h3>
          {!loadingSummaries && !summariesUnavailable && (
            <p className="mt-1 text-sm text-muted">
              Governed workflow trajectories will appear here when available.
            </p>
          )}
        </div>
      ) : (
        <WorkflowTrajectoryRail
          fallbackSummary={fallbackSummary}
          workflowId={focusedWorkflowId}
        />
      )}
    </Panel>
  )
}
