import { ArrowLeft, CalendarClock, GitBranch, Layers3, Workflow } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Panel } from '../../components/ui/Panel'
import type { WorkflowTrajectoryResponse } from '../../types/workflow'

function formatEnum(value: string | null): string {
  return value
    ? value.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
    : 'Unavailable'
}

function formatDate(timestamp: string): string {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return 'Unavailable'

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

type SummaryFieldProps = {
  label: string
  value: string
  icon: typeof Workflow
  mono?: boolean
}

function SummaryField({ label, value, icon: Icon, mono = false }: SummaryFieldProps) {
  return (
    <div className="min-w-0 rounded-md border border-line bg-slate-50/70 p-3">
      <dt className="flex items-center gap-1.5 text-[11px] font-medium text-muted">
        <Icon aria-hidden="true" size={13} />
        {label}
      </dt>
      <dd className={`mt-2 break-words text-sm font-medium text-ink-900 ${mono ? 'font-mono text-xs' : ''}`}>
        {value}
      </dd>
    </div>
  )
}

export function WorkflowSummaryHeader({ workflow }: { workflow: WorkflowTrajectoryResponse }) {
  return (
    <div className="space-y-4">
      <Link
        className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-accent"
        to="/workflows"
      >
        <ArrowLeft aria-hidden="true" size={16} />
        Back to workflows
      </Link>

      <div className="flex items-start gap-3">
        <span className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-accent/10 text-accent">
          <Workflow aria-hidden="true" size={19} />
        </span>
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase tracking-[0.12em] text-muted">Workflow run</p>
          <h2 className="mt-1 text-xl font-semibold tracking-tight text-ink-950">
            {workflow.agentName}
          </h2>
          <p className="mt-1 break-all font-mono text-xs text-muted">{workflow.workflowId}</p>
        </div>
      </div>

      <Panel className="p-4 sm:p-5">
        <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-6">
          <SummaryField label="Workflow type" value={formatEnum(workflow.workflowType)} icon={Workflow} />
          <SummaryField label="Current state" value={formatEnum(workflow.currentState)} icon={GitBranch} />
          <SummaryField label="Current node" value={workflow.currentNodeId ?? 'Unavailable'} icon={Layers3} mono />
          <SummaryField label="Started" value={formatDate(workflow.startedAt)} icon={CalendarClock} />
          <SummaryField label="Last event" value={formatDate(workflow.lastEventAt)} icon={CalendarClock} />
          <SummaryField label="Events" value={String(workflow.eventCount)} icon={Layers3} />
        </dl>
      </Panel>
    </div>
  )
}
