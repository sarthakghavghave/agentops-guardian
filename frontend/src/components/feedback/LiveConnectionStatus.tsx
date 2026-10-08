import type { LiveConnectionStatus as Status } from '../../hooks/useLiveGovernanceEvents'

const statusPresentation: Record<Status, { label: string; dot: string; text: string }> = {
  CONNECTING: {
    label: 'CONNECTING',
    dot: 'bg-amber-500',
    text: 'text-amber-800',
  },
  LIVE: {
    label: 'LIVE',
    dot: 'bg-emerald-600',
    text: 'text-emerald-800',
  },
  RECONNECTING: {
    label: 'RECONNECTING',
    dot: 'bg-amber-600',
    text: 'text-amber-800',
  },
  DISCONNECTED: {
    label: 'DISCONNECTED',
    dot: 'bg-slate-400',
    text: 'text-slate-600',
  },
}

export function LiveConnectionStatus({ status }: { status: Status }) {
  const presentation = statusPresentation[status]

  return (
    <span
      aria-label={`Live governance events: ${presentation.label.toLowerCase()}`}
      className={`inline-flex items-center gap-2 rounded-full border border-line bg-white px-3 py-1.5 text-[11px] font-semibold tracking-wide ${presentation.text}`}
      role="status"
    >
      <span aria-hidden="true" className={`h-2 w-2 rounded-full ${presentation.dot}`} />
      {presentation.label}
    </span>
  )
}
