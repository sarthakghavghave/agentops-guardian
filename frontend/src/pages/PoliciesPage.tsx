import { Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import { PolicyListShell } from '../features/policies/PolicyListShell'

export function PoliciesPage() {
  return (
    <div className="space-y-2">
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold tracking-tight text-ink-950">Policies</h2>
          <p className="mt-1 text-sm text-muted">
            Inspect enterprise-defined governance controls evaluated by AgentOps Guardian.
          </p>
        </div>
        <Link
          className="inline-flex min-h-10 shrink-0 items-center justify-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2"
          to="/policies/new"
        >
          <Plus aria-hidden="true" size={16} />
          Create policy
        </Link>
      </div>
      <PolicyListShell />
    </div>
  )
}
