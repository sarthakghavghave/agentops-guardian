import { EmptyState } from '../../components/feedback/EmptyState'
import { Panel } from '../../components/ui/Panel'

export function PolicyListShell() {
  return (
    <Panel
      title="Policy definitions"
      description="Review configured governance rules and their targets, conditions, and actions."
    >
      <EmptyState
        title="Policy data is not connected"
        description="Policies will be listed here with their enabled state, priority, target, and configured action."
      />
    </Panel>
  )
}
