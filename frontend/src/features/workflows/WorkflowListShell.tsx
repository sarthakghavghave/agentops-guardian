import { EmptyState } from '../../components/feedback/EmptyState'
import { Panel } from '../../components/ui/Panel'

export function WorkflowListShell() {
  return (
    <Panel
      title="Workflow runs"
      description="Inspect agent runs, current workflow state, and the activity recorded for each execution."
    >
      <EmptyState
        title="Workflow data is not connected"
        description="The workflow list will show run identifiers, agents, states, and event counts once backend data integration is enabled."
      />
    </Panel>
  )
}
