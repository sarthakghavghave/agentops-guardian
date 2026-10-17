import { Bell } from 'lucide-react'
import { Outlet, useLocation } from 'react-router-dom'
import { Sidebar } from '../components/navigation/Sidebar'
import { GovernanceToggle } from '../components/governance/GovernanceToggle'

const pageNames: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/workflows': 'Workflows',
  '/interventions': 'Interventions',
  '/policies': 'Policies',
}

export function AppLayout() {
  const { pathname } = useLocation()
  const pageTitle = pathname.startsWith('/workflows/')
    ? 'Workflow details'
    : pathname.startsWith('/interventions/')
      ? 'Intervention details'
    : pageNames[pathname] ?? 'AgentOps Guardian'

  return (
    <div className="flex h-screen flex-col overflow-hidden bg-canvas md:flex-row">
      <Sidebar />
      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-[68px] shrink-0 items-center justify-between border-b border-line bg-white px-5 sm:px-8">
          <div>
            <p className="text-[11px] font-medium uppercase tracking-[0.12em] text-muted">
              Governance
            </p>
            <h1 className="mt-0.5 text-sm font-semibold text-ink-950">{pageTitle}</h1>
          </div>
          <div className="flex items-center gap-4">
            {pathname === '/dashboard' && <GovernanceToggle />}
            <button
              aria-label="Notifications"
              className="rounded-md p-2 text-slate-500 hover:bg-slate-100"
              type="button"
            >
              <Bell aria-hidden="true" size={17} />
            </button>
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-200 text-xs font-semibold text-slate-700">
              AG
            </span>
          </div>
        </header>
        <main className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-4 py-6 sm:px-8 sm:py-8">
          <div className="mx-auto w-full max-w-[1440px]">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
