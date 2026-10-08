import { DashboardOverview } from '../features/dashboard/DashboardOverview'
import { LiveConnectionStatus } from '../components/feedback/LiveConnectionStatus'
import { useLiveGovernanceEvents } from '../hooks/useLiveGovernanceEvents'

export function DashboardPage() {
  const liveState = useLiveGovernanceEvents()

  return (
    <div className="space-y-2">
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold tracking-tight text-ink-950">Governance overview</h2>
          <p className="mt-1 text-sm text-muted">
            Monitor governed workflows and review activity across your agent operations.
          </p>
        </div>
        <LiveConnectionStatus status={liveState.status} />
      </div>
      <DashboardOverview liveState={liveState} />
    </div>
  )
}
