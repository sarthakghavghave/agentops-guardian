import { InterventionQueueShell } from '../features/interventions/InterventionQueueShell'

export function InterventionsPage() {
  return (
    <div className="space-y-2">
      <div className="mb-6">
        <h2 className="text-xl font-semibold tracking-tight text-ink-950">Interventions</h2>
        <p className="mt-1 text-sm text-muted">
          Review human approval requests and trace their resolution.
        </p>
      </div>
      <InterventionQueueShell />
    </div>
  )
}
