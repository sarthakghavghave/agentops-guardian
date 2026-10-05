import { Check, CircleSlash, GitBranch, ShieldAlert } from 'lucide-react'
import { EmptyState } from '../../components/feedback/EmptyState'
import { Panel } from '../../components/ui/Panel'

const metrics = [
  { label: 'Total workflows', icon: GitBranch },
  { label: 'Allowed actions', icon: Check },
  { label: 'Blocked actions', icon: CircleSlash },
  { label: 'Pending interventions', icon: ShieldAlert },
]

export function DashboardOverview() {
  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {metrics.map(({ label, icon: Icon }) => (
          <Panel key={label} className="p-5">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-sm font-medium text-slate-600">{label}</p>
                <p className="mt-4 text-2xl font-semibold tracking-tight text-ink-950">—</p>
              </div>
              <span className="flex h-9 w-9 items-center justify-center rounded-md bg-slate-100 text-slate-600">
                <Icon aria-hidden="true" size={17} />
              </span>
            </div>
            <p className="mt-2 text-xs text-muted">Pending backend integration</p>
          </Panel>
        ))}
      </div>

      <Panel
        title="Recent governance activity"
        description="A chronological view of recent policy decisions, risk assessments, and interventions."
      >
        <EmptyState
          title="Activity will appear here"
          description="Governance events are not connected yet. Activity will be populated from workflow trajectory data in a future integration phase."
        />
      </Panel>
    </div>
  )
}
