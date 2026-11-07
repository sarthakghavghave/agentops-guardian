import {
  Activity,
  ArrowUpRight,
  ClipboardCheck,
  ChevronDown,
  LayoutDashboard,
  Waypoints,
  Workflow,
} from 'lucide-react'
import { NavLink } from 'react-router-dom'

const navigation = [
  { label: 'Dashboard', to: '/dashboard', icon: LayoutDashboard },
  { label: 'Workflows', to: '/workflows', icon: Workflow },
  { label: 'Interventions', to: '/interventions', icon: ClipboardCheck },
  { label: 'Policies', to: '/policies', icon: Activity },
]

const agentPortalUrl = import.meta.env.VITE_AGENT_PORTAL_URL || 'http://localhost:5174'

export function Sidebar() {
  return (
    <aside className="sticky top-0 z-30 flex w-full shrink-0 flex-col border-b border-white/10 bg-ink-950 text-slate-300 md:sticky md:h-screen md:min-h-0 md:w-64 md:border-b-0 md:border-r md:border-white/10">
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
        <a
          className="flex shrink-0 items-center gap-3 rounded-md px-3 py-2.5 text-sm text-slate-400 transition-colors hover:bg-white/5 hover:text-slate-100"
          href={agentPortalUrl}
          rel="noreferrer"
          target="_blank"
        >
          <ArrowUpRight aria-hidden="true" size={17} strokeWidth={1.8} />
          Agent Portal
        </a>
        <div className="ml-auto flex shrink-0 items-center border-l border-white/10 pl-2 md:ml-0 md:mt-4 md:w-full md:flex-col md:items-stretch md:border-l-0 md:border-t md:pl-0 md:pt-4">
          <p className="hidden px-3 pb-2 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-500 md:block">
            Reference
          </p>
          <NavLink
            to="/architecture"
            className={({ isActive }) =>
              `flex shrink-0 items-center gap-3 rounded-md px-3 py-2.5 text-sm transition-colors ${
                isActive
                  ? 'bg-accent/20 font-medium text-teal-100'
                  : 'text-teal-200/80 hover:bg-accent/10 hover:text-teal-100'
              }`
            }
          >
            <Waypoints aria-hidden="true" size={17} strokeWidth={1.8} />
            Architecture
          </NavLink>
        </div>
      </nav>

      <details className="group mt-auto border-t border-white/10 px-4 py-3" open>
        <summary className="flex cursor-pointer list-none items-center justify-between gap-2 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-400 transition-colors hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent [&::-webkit-details-marker]:hidden">
          Technology stack
          <ChevronDown
            aria-hidden="true"
            className="shrink-0 transition-transform group-open:rotate-180"
            size={14}
          />
        </summary>
        <ul className="mt-3 grid grid-cols-2 gap-3 md:grid-cols-1 md:space-y-2.5 md:gap-0">
          <li className="flex items-center gap-2.5">
            <img
              alt=""
              className="h-5 w-5 shrink-0 object-contain"
              height="20"
              src="https://img.icons8.com/color/48/spring-logo.png"
              width="20"
            />
            <span className="min-w-0">
              <span className="block text-[9px] uppercase tracking-[0.1em] text-slate-500">Backend</span>
              <span className="block text-[11px] leading-4 text-slate-300">Spring Boot · Spring AI</span>
            </span>
          </li>
          <li className="flex items-center gap-2.5">
            <img
              alt=""
              className="h-5 w-5 shrink-0 object-contain"
              height="20"
              src="https://img.icons8.com/color/48/postgreesql.png"
              width="20"
            />
            <span className="min-w-0">
              <span className="block text-[9px] uppercase tracking-[0.1em] text-slate-500">Database</span>
              <span className="block text-[11px] leading-4 text-slate-300">PostgreSQL · Neon</span>
            </span>
          </li>
          <li className="flex items-center gap-2.5">
            <img
              alt=""
              className="h-5 w-5 shrink-0 object-contain"
              height="20"
              src="https://img.icons8.com/officexs/16/react.png"
              width="20"
            />
            <span className="min-w-0">
              <span className="block text-[9px] uppercase tracking-[0.1em] text-slate-500">Frontend</span>
              <span className="block text-[11px] leading-4 text-slate-300">React · Vite</span>
            </span>
          </li>
          <li className="flex items-center gap-2.5">
            <img
              alt=""
              className="h-5 w-5 shrink-0 object-contain"
              height="20"
              src="https://img.icons8.com/color/48/docker.png"
              width="20"
            />
            <span className="min-w-0">
              <span className="block text-[9px] uppercase tracking-[0.1em] text-slate-500">Deployment</span>
              <span className="block text-[11px] leading-4 text-slate-300">Docker · Render</span>
            </span>
          </li>
        </ul>
      </details>
    </aside>
  )
}
