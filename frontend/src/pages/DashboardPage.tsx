import { DashboardOverview } from '../features/dashboard/DashboardOverview'

export function DashboardPage() {
  return (
    <div className="space-y-2">
      <div className="mb-6">
        <h2 className="text-xl font-semibold tracking-tight text-ink-950">Governance overview</h2>
        <p className="mt-1 text-sm text-muted">
          Monitor governed workflows and review activity across your agent operations.
        </p>
      </div>
      <DashboardOverview />
    </div>
  )
}
