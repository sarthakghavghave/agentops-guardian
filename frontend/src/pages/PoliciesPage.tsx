import { PolicyListShell } from '../features/policies/PolicyListShell'

export function PoliciesPage() {
  return (
    <div className="space-y-2">
      <div className="mb-6">
        <h2 className="text-xl font-semibold tracking-tight text-ink-950">Policies</h2>
        <p className="mt-1 text-sm text-muted">
          Inspect and manage the rules applied by the governance control plane.
        </p>
      </div>
      <PolicyListShell />
    </div>
  )
}
