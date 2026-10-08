import { WorkflowListShell } from '../features/workflows/WorkflowListShell'

export function WorkflowsPage() {
  return (
    <div className="space-y-2">
      <div className="mb-6">
        <h2 className="text-xl font-semibold tracking-tight text-ink-950">Workflows</h2>
        <p className="mt-1 text-sm text-muted">
          Explore workflow executions and their governance history.
        </p>
      </div>
      <WorkflowListShell />
    </div>
  )
}
