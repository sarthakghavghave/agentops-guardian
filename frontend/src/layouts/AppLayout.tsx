import { Bell, ShieldCheck } from 'lucide-react'
import { Outlet, useLocation } from 'react-router-dom'
import { Sidebar } from '../components/navigation/Sidebar'

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
    : pageNames[pathname] ?? 'AgentOps Guardian'

  return (
    <div className="min-h-screen bg-canvas md:flex">
      <Sidebar />
      <div className="flex min-h-screen min-w-0 flex-1 flex-col">
        <header className="flex h-[68px] items-center justify-between border-b border-line bg-white px-5 sm:px-8">
          <div>
            <p className="text-[11px] font-medium uppercase tracking-[0.12em] text-muted">
              Governance
            </p>
            <h1 className="mt-0.5 text-sm font-semibold text-ink-950">{pageTitle}</h1>
          </div>
          <div className="flex items-center gap-4">
            <span className="hidden items-center gap-2 rounded-full border border-line px-3 py-1.5 text-xs text-slate-600 sm:flex">
              <ShieldCheck aria-hidden="true" size={14} className="text-accent" />
              Control plane
            </span>
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
        <main className="flex-1 px-4 py-6 sm:px-8 sm:py-8">
          <div className="mx-auto w-full max-w-[1440px]">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
