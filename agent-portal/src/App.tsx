import { ArrowUpRight, Bot, ShieldCheck, Sparkles } from 'lucide-react'

const guardianUrl = import.meta.env.VITE_GUARDIAN_URL || 'http://localhost:5173'

export function App() {
  return (
    <main className="portal-shell">
      <header className="topbar">
        <a aria-label="Agent Portal home" className="brand" href="/">
          <span className="brand-mark"><Sparkles aria-hidden="true" size={19} /></span>
          <span>
            <strong>AgentOps</strong>
            <small>Agent Portal</small>
          </span>
        </a>
        <a className="guardian-link" href={guardianUrl}>
          Open Guardian
          <ArrowUpRight aria-hidden="true" size={15} />
        </a>
      </header>

      <section aria-labelledby="portal-title" className="welcome">
        <div className="eyebrow"><span /> AGENT WORKSPACE</div>
        <h1 id="portal-title">A clearer way to work with AI agents.</h1>
        <p className="intro">
          Start a conversation with your governed agents and keep the people,
          decisions, and actions in view.
        </p>

        <section aria-label="Portal status" className="workspace-card">
          <div className="workspace-icon"><Bot aria-hidden="true" size={24} /></div>
          <div className="workspace-copy">
            <h2>Your agent workspace</h2>
            <p>The portal shell and service contracts are ready.</p>
          </div>
          <span className="status"><ShieldCheck aria-hidden="true" size={15} /> Governance-first platform</span>
        </section>

        <p className="footnote">
          Agent conversations are handled by the Agent Coordinator. Governance activity
          and intervention review are available in Guardian.
        </p>
      </section>

      <footer className="footer">
        <span>AgentOps Guardian</span>
        <span>Governed agent workspace</span>
      </footer>
    </main>
  )
}
