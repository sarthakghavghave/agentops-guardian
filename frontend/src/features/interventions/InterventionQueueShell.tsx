import { EmptyState } from '../../components/feedback/EmptyState'
import { Panel } from '../../components/ui/Panel'

export function InterventionQueueShell() {
  return (
    <Panel
      title="Intervention queue"
      description="Review approval-required actions and their resolution status."
    >
      <EmptyState
        title="No intervention data connected"
        description="Pending approval cards and their resolution actions will appear here when the intervention API is integrated."
      />
    </Panel>
  )
}
