import { ShieldCheck, ShieldX } from 'lucide-react'

export function PolicyStatusBadge({ enabled }: { enabled: boolean }) {
  const Icon = enabled ? ShieldCheck : ShieldX
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${
        enabled ? 'bg-emerald-50 text-emerald-800' : 'bg-slate-100 text-slate-600'
      }`}
    >
      <Icon aria-hidden="true" size={14} />
      {enabled ? 'Enabled' : 'Disabled'}
    </span>
  )
}
