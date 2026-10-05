import { useParams } from 'react-router-dom'
import { EmptyState } from '../../components/feedback/EmptyState'
import { Panel } from '../../components/ui/Panel'

export function WorkflowTrajectoryShell() {
  const { workflowId } = useParams<{ workflowId: string }>()

  return (
    <div className="space-y-6">
      <div>
        <p className="text-xs font-medium uppercase tracking-[0.12em] text-muted">Workflow run</p>
        <h2 className="mt-1 break-all font-mono text-lg font-semibold text-ink-950">
          {workflowId}
        </h2>
      </div>
      <Panel
        title="Execution trajectory"
        description="Review workflow transitions, policy outcomes, risk assessments, and intervention lifecycle events."
      >
        <EmptyState
          title="Trajectory visualization is ready for integration"
          description="Timeline events and state transitions will be rendered here from the workflow trajectory API."
        />
      </Panel>
    </div>
  )
}
