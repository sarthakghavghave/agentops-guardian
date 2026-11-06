import {
  ArrowDown,
  ArrowLeftRight,
  Ban,
  Bot,
  Check,
  ClipboardCheck,
  Database,
  FileText,
  Info,
  Mail,
  Radio,
  Server,
  ShieldCheck,
  Users,
  Workflow,
  type LucideIcon,
} from 'lucide-react'

type ArchitectureCardProps = {
  icon: LucideIcon
  title: string
  description: string
  detail?: string
  dark?: boolean
  titleHint?: string
  badge?: string
  centerContent?: boolean
}

function ArchitectureCard({
  icon: Icon,
  title,
  description,
  detail,
  dark = false,
  titleHint,
  badge,
  centerContent = false,
}: ArchitectureCardProps) {
  return (
    <article
      aria-label={`${title}. ${description}`}
      className={`group rounded-xl border p-4 transition-all duration-200 hover:-translate-y-0.5 hover:border-accent/50 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent ${
        dark
          ? 'border-white/10 bg-white/[0.04] text-white hover:bg-white/[0.07]'
          : 'border-accent/15 bg-[#f9fcfb] text-ink-950 hover:bg-[#f1f8f6]'
      } ${centerContent ? 'flex h-full items-center' : ''}`}
      tabIndex={0}
      title={titleHint ?? description}
    >
      <div className={`flex gap-3 ${centerContent ? 'w-full items-center' : 'items-start'}`}>
        <span
          className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${
            dark ? 'bg-accent/20 text-teal-200' : 'bg-accent/10 text-accent'
          }`}
        >
          <Icon aria-hidden="true" size={18} strokeWidth={1.8} />
        </span>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className={`text-sm font-semibold ${dark ? 'text-white' : 'text-ink-950'}`}>
              {title}
            </h3>
            {badge && (
              <span className="rounded-full border border-accent/15 bg-[#e8f2ef] px-2.5 py-1 text-[10px] font-medium text-ink-800">
                {badge}
              </span>
            )}
          </div>
          <p className={`mt-1 text-xs leading-5 ${dark ? 'text-slate-300' : 'text-muted'}`}>
            {description}
          </p>
          {detail && (
            <p className={`mt-2 text-[11px] leading-4 ${dark ? 'text-slate-400' : 'text-slate-500'}`}>
              {detail}
            </p>
          )}
        </div>
      </div>
    </article>
  )
}

function InfoTooltip({ text }: { text: string }) {
  return (
    <span className="group/tooltip relative inline-flex">
      <button
        aria-label={`More information: ${text}`}
        className="inline-flex h-6 w-6 items-center justify-center rounded-full text-slate-400 transition-colors hover:bg-accent/10 hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
        type="button"
      >
        <Info aria-hidden="true" size={14} />
      </button>
      <span
        className="pointer-events-none absolute bottom-full left-1/2 z-30 mb-2 w-56 -translate-x-1/2 translate-y-1 rounded-lg border border-accent/15 bg-white px-3 py-2 text-left text-[11px] leading-4 text-ink-800 opacity-0 shadow-lg transition duration-150 group-hover/tooltip:translate-y-0 group-hover/tooltip:opacity-100 group-focus-within/tooltip:translate-y-0 group-focus-within/tooltip:opacity-100"
        role="tooltip"
      >
        {text}
      </span>
    </span>
  )
}

function FlowArrow({ label }: { label?: string }) {
  return (
    <div aria-hidden="true" className="flex flex-col items-center py-2 text-slate-400">
      {label && (
        <span className="mb-1 text-[10px] font-medium uppercase tracking-[0.12em]">{label}</span>
      )}
      <ArrowDown size={16} strokeWidth={1.8} />
    </div>
  )
}

function StageLabel({ children }: { children: string }) {
  return (
    <p className="mb-3 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-400">
      {children}
    </p>
  )
}

export function ArchitecturePage() {
  return (
    <div className="space-y-6">
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-xl font-semibold tracking-tight text-ink-950">
            How AgentOps Guardian works
          </h2>
          <span className="rounded-full border border-line bg-white px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.12em] text-muted">
            Architecture
          </span>
        </div>
        <p className="mt-1 max-w-3xl text-sm leading-6 text-muted">
          A high-level view of how requests reach agents, how proposed tool actions are governed,
          and where the application stores data and sends updates.
        </p>
      </div>

      <section
        aria-label="AgentOps Guardian architecture diagram"
        className="rounded-xl border border-line bg-white p-4 shadow-panel sm:p-6"
      >
        <div className="mx-auto max-w-4xl">
          <StageLabel>People and browser applications</StageLabel>
          <div className="grid gap-3 sm:grid-cols-2">
            <ArchitectureCard
              icon={Users}
              title="Agent portal"
              description="React client for starting and continuing agent conversations."
            />
            <ArchitectureCard
              icon={ShieldCheck}
              title="Governance dashboard"
              description="React client for reviewing workflows, policies, and interventions."
            />
          </div>

          <FlowArrow label="API requests" />

          <section className="rounded-2xl border border-accent/15 bg-[#eaf2ef] p-4 text-ink-950 shadow-sm sm:p-6">
            <div className="mb-5 flex flex-wrap items-center justify-between gap-2">
              <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-muted">
                Application runtime
              </p>
              <span className="rounded-full border border-accent/15 bg-white px-2.5 py-1 text-[10px] font-medium text-ink-800">
                Render · Docker · Spring Boot
              </span>
            </div>

            <ArchitectureCard
              icon={Server}
              title="Agent Coordinator"
              description="Receives requests and routes them to the appropriate agent workflow."
              badge="Spring AI"
            />

            <FlowArrow />

            <div className="grid items-stretch gap-3 lg:grid-cols-[1fr_auto_0.7fr]">
              <div className="rounded-xl border border-accent/10 bg-white p-3">
                <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                  <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-400">
                    Agents
                  </p>
                  <span className="rounded-full border border-accent/15 bg-[#e8f2ef] px-2.5 py-1 text-[10px] font-medium text-ink-800">
                    Spring AI
                  </span>
                </div>
                <div className="grid gap-2 sm:grid-cols-3 lg:grid-cols-1">
                  {[
                    {
                      icon: Bot,
                      name: 'Customer support',
                      hint: 'Answers customer, order, product, and city questions using registered read tools.',
                    },
                    {
                      icon: FileText,
                      name: 'Customer reporting',
                      hint: 'Retrieves customer data and generates analytical or redacted reports in PDF format.',
                    },
                    {
                      icon: Mail,
                      name: 'Email',
                      hint: 'Handles email requests and sends messages through the governed communication tool.',
                    },
                  ].map(({ icon: Icon, name, hint }) => (
                    <div
                      key={name}
                      className="flex min-w-0 items-center justify-between gap-1 rounded-lg border border-accent/10 bg-[#f8fbfa] px-2.5 py-2 text-xs text-ink-800"
                    >
                      <span className="flex min-w-0 items-center gap-2">
                        <Icon aria-hidden="true" className="shrink-0 text-accent" size={15} />
                        <span className="truncate">{name}</span>
                      </span>
                      <InfoTooltip text={hint} />
                    </div>
                  ))}
                </div>
              </div>

              <div
                aria-label="Agents exchange requests and responses with the language model"
                className="flex items-center justify-center gap-2 py-1 text-[10px] font-medium uppercase tracking-[0.1em] text-muted lg:flex-col"
              >
                <span className="lg:hidden">Model calls</span>
                <ArrowLeftRight aria-hidden="true" className="hidden lg:block" size={18} />
                <ArrowDown aria-hidden="true" className="lg:hidden" size={15} />
                <span className="hidden lg:block">Model calls</span>
              </div>

              <ArchitectureCard
                icon={Bot}
                title="Google Gemini"
                description="Language model used by the coordinator and agents."
                centerContent
              />
            </div>

            <FlowArrow label="Tool proposals" />

            <div className="rounded-xl border border-accent/20 bg-white p-4 shadow-sm sm:p-5">
              <div className="flex items-start gap-3">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-accent/10 text-accent">
                  <ShieldCheck aria-hidden="true" size={18} />
                </span>
                <div className="min-w-0 flex-1">
                  <h3 className="text-sm font-semibold text-ink-950">Governance checkpoint</h3>
                  <p className="mt-1 text-xs leading-5 text-muted">
                    Each proposed tool action is checked before it can run.
                  </p>
                  <div className="mt-4 flex flex-wrap gap-2">
                    {[
                      ['1', 'Policy', 'Enterprise rules'],
                      ['2', 'Workflow', 'Execution sequence of a task'],
                      ['3', 'Conversation History', 'What happened so far in the current chat'],
                      ['4', 'Risk', 'Context and consequence'],
                    ].map(([number, title, description]) => (
                      <div
                        key={title}
                        className="flex w-fit max-w-full items-center gap-2 rounded-lg border border-accent/10 bg-[#f1f7f5] px-3 py-2"
                      >
                        <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-accent/10 text-[10px] font-semibold text-accent">
                          {number}
                        </span>
                        <span className="min-w-0">
                          <span className="block text-xs font-medium text-ink-950">{title}</span>
                          <span className="block text-[10px] text-muted">{description}</span>
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>

            <FlowArrow label="Governance outcome" />

            <div className="grid gap-3 md:grid-cols-3">
              <div className="rounded-xl border border-emerald-700/15 bg-emerald-50 p-3.5">
                <div className="flex items-center gap-2 text-emerald-800">
                  <Check aria-hidden="true" size={16} />
                  <h3 className="text-xs font-semibold">Continue</h3>
                </div>
                <p className="mt-1.5 text-[11px] leading-4 text-emerald-950/70">
                  Low and moderate risk actions may proceed.
                </p>
              </div>
              <div className="group/approval rounded-xl border border-amber-700/15 bg-amber-50 p-3.5 transition hover:border-amber-700/30 hover:shadow-sm">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2 text-amber-900">
                      <ClipboardCheck aria-hidden="true" size={16} />
                      <h3 className="text-xs font-semibold">Approval required</h3>
                  </div>
                  <InfoTooltip text="After approval, the pending task is resumed and revalidated before its action is executed." />
                </div>
                <p className="mt-1.5 text-[11px] leading-4 text-amber-950/70">
                  High-risk actions wait for a decision; approved actions are revalidated.
                </p>
              </div>
              <div className="rounded-xl border border-rose-700/15 bg-rose-50 p-3.5">
                <div className="flex items-center gap-2 text-rose-800">
                  <Ban aria-hidden="true" size={16} />
                  <h3 className="text-xs font-semibold">Blocked</h3>
                </div>
                <p className="mt-1.5 text-[11px] leading-4 text-rose-950/70">
                  Policy blocks and critical risk prevent execution.
                </p>
              </div>
            </div>

            <FlowArrow label="Allowed or re-approved actions" />

            <div className="rounded-xl border border-accent/15 bg-white p-4">
              <div className="flex items-center gap-2">
                <Workflow aria-hidden="true" className="text-accent" size={17} />
                <h3 className="text-sm font-semibold text-ink-950">Agent tools</h3>
              </div>
              <p className="mt-1 text-xs leading-5 text-muted">
                Read customer and business data, generate reports, or prepare and send email.
              </p>
            </div>

            <div
              aria-label="An analytical report PDF is generated in the Render runtime and can be handed to the email workflow"
              className="mt-3 flex items-center gap-3 rounded-xl border border-accent/10 bg-white/80 px-4 py-3"
              tabIndex={0}
              title="The generated file is available within the running backend for the email handoff."
            >
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-accent/10 text-accent">
                <FileText aria-hidden="true" size={16} />
              </span>
              <p className="text-xs leading-5 text-muted">
                <span className="font-medium text-ink-950">Report handoff:</span> analytical PDF
                output is generated in the Render runtime and can be attached in an email using
                PDF metadata.
              </p>
            </div>
          </section>

          <FlowArrow label="Connected services" />

          <div className="grid gap-3 md:grid-cols-2">
            <ArchitectureCard
              icon={Database}
              title="Neon PostgreSQL"
              description="Stores business records, policy definitions, interventions, and audit events."
            />
            <ArchitectureCard
              icon={Mail}
              title="SMTP provider"
              description="Delivers email from the communication tool."
              titleHint="Deployment uses Gmail or Brevo through SMTP."
            />
          </div>

          <div className="mt-3 flex flex-col gap-3 rounded-xl border border-accent/15 bg-[#f3f9f7] p-4 shadow-sm sm:flex-row sm:items-center">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-accent/10 text-accent">
              <Radio aria-hidden="true" size={17} />
            </span>
            <div>
              <h3 className="text-xs font-semibold text-ink-950">Audit and live updates</h3>
              <p className="mt-1 text-xs leading-5 text-muted">
                Governance, execution, and intervention events are audited to PostgreSQL. Live
                governance updates are streamed to both frontends over SSE.
              </p>
            </div>
          </div>
        </div>
      </section>

      <p className="text-[11px] leading-5 text-muted">
        This page summarizes the implemented demonstration flow. It does not represent every
        internal component or deployment setting.
      </p>
    </div>
  )
}
