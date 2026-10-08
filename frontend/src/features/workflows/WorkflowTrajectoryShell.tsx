import { ArrowLeft } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { EmptyState } from '../../components/feedback/EmptyState'
import { ErrorState } from '../../components/feedback/ErrorState'
import { LoadingState } from '../../components/feedback/LoadingState'
import { useWorkflowTrajectory } from '../../hooks/useWorkflows'
import { WorkflowSummaryHeader } from './WorkflowSummaryHeader'
import { WorkflowTrajectory } from './WorkflowTrajectory'

function WorkflowListLink() {
  return (
    <Link
      className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-accent"
      to="/workflows"
    >
      <ArrowLeft aria-hidden="true" size={16} />
      Back to workflows
    </Link>
  )
}

export function WorkflowTrajectoryShell() {
  const { workflowId = '' } = useParams<{ workflowId: string }>()
  const workflowQuery = useWorkflowTrajectory(workflowId)

  if (workflowQuery.isPending) {
    return <LoadingState label="Loading workflow trajectory" />
  }

  if (workflowQuery.isError && !workflowQuery.data) {
    const isNotFound =
      workflowQuery.error instanceof ApiError && workflowQuery.error.status === 404

    return (
      <div className="space-y-5">
        <WorkflowListLink />
        {isNotFound ? (
          <EmptyState
            title="Workflow not found"
            description="This workflow run could not be found. It may no longer be available, or the identifier may be incorrect."
          />
        ) : (
          <ErrorState message="The workflow trajectory could not be loaded. Please try again later." />
        )}
      </div>
    )
  }

  const workflow = workflowQuery.data
  if (!workflow) {
    return (
      <div className="space-y-5">
        <WorkflowListLink />
        <ErrorState message="The workflow response was unavailable. Please try again later." />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {workflowQuery.isError && (
        <ErrorState message="The latest workflow refresh failed. The previously loaded trajectory is still shown." />
      )}
      <WorkflowSummaryHeader workflow={workflow} />
      <WorkflowTrajectory events={workflow.events} />
    </div>
  )
}
