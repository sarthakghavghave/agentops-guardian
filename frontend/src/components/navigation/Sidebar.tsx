import { Activity, ArrowUpRight, ClipboardCheck, LayoutDashboard, Workflow } from 'lucide-react'
import { NavLink } from 'react-router-dom'

const navigation = [
  { label: 'Dashboard', to: '/dashboard', icon: LayoutDashboard },
  { label: 'Workflows', to: '/workflows', icon: Workflow },
  { label: 'Interventions', to: '/interventions', icon: ClipboardCheck },
  { label: 'Policies', to: '/policies', icon: Activity },
]

export function Sidebar() {
  return (
    <aside className="flex w-full shrink-0 flex-col border-b border-white/10 bg-ink-950 text-slate-300 md:min-h-screen md:w-64 md:border-b-0 md:border-r md:border-white/10">
      <div className="flex h-[68px] items-center gap-3 border-b border-white/10 px-5">
        <span className="flex h-9 w-9 items-center justify-center rounded-md bg-accent text-white">
          <Activity aria-hidden="true" size={19} strokeWidth={2.2} />
        </span>
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold tracking-wide text-white">AgentOps Guardian</p>
          <p className="mt-0.5 text-[10px] font-medium uppercase tracking-[0.14em] text-slate-400">
            Governance console
          </p>
        </div>
      </div>

      <nav aria-label="Main navigation" className="flex gap-1 overflow-x-auto px-3 py-3 md:flex-col md:py-6">
        <p className="hidden px-3 pb-2 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-500 md:block">
          Workspace
        </p>
        {navigation.map(({ label, to, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex shrink-0 items-center gap-3 rounded-md px-3 py-2.5 text-sm transition-colors ${
                isActive
                  ? 'bg-white/10 font-medium text-white'
                  : 'text-slate-400 hover:bg-white/5 hover:text-slate-100'
              }`
            }
          >
            <Icon aria-hidden="true" size={17} strokeWidth={1.8} />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="mt-auto hidden border-t border-white/10 p-4 md:block">
        <div className="rounded-md border border-white/10 bg-white/[0.03] p-3">
          <div className="flex items-center gap-2 text-xs font-medium text-slate-300">
            <span className="h-1.5 w-1.5 rounded-full bg-amber-400" />
            Backend connection
          </div>
          <p className="mt-2 text-xs leading-5 text-slate-500">
            Live data will appear as API integration is enabled.
          </p>
          <span className="mt-3 flex items-center gap-1 text-[11px] text-slate-400">
            Configuration <ArrowUpRight aria-hidden="true" size={12} />
          </span>
        </div>
      </div>
    </aside>
  )
}
