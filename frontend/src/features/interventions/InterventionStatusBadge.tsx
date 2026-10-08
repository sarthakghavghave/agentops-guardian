import type { GovernanceInterventionStatus } from '../../types/intervention'

const statusStyles: Record<GovernanceInterventionStatus, string> = {
  PENDING: 'border-amber-200 bg-amber-50 text-amber-800',
  APPROVED: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  REJECTED: 'border-red-200 bg-red-50 text-red-800',
  EXPIRED: 'border-slate-200 bg-slate-100 text-slate-700',
}

export function formatInterventionValue(value: string): string {
  return value.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

export function InterventionStatusBadge({
  status,
}: {
  status: GovernanceInterventionStatus
}) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-semibold ${statusStyles[status]}`}
    >
      {formatInterventionValue(status)}
    </span>
  )
}
