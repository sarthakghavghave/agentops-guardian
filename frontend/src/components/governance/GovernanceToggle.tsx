import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { LoaderCircle, ShieldCheck } from 'lucide-react'
import {
  getGovernanceSettings,
  updateGovernanceEnabled,
} from '../../api/governanceSettings'

const settingsQueryKey = ['governance-settings'] as const

export function GovernanceToggle() {
  const queryClient = useQueryClient()
  const settings = useQuery({
    queryKey: settingsQueryKey,
    queryFn: getGovernanceSettings,
  })
  const update = useMutation({
    mutationFn: updateGovernanceEnabled,
    onSuccess: (nextSettings) => {
      queryClient.setQueryData(settingsQueryKey, nextSettings)
    },
  })
  const enabled = settings.data?.enabled ?? false
  const busy = settings.isPending || update.isPending

  return (
    <div className="relative flex items-center gap-2 rounded-full border border-line bg-white px-3 py-1.5">
      <ShieldCheck
        aria-hidden="true"
        className={enabled ? 'text-emerald-600' : 'text-slate-400'}
        size={15}
      />
      <span className="text-xs font-medium text-slate-700">Guardian</span>
      <button
        aria-checked={enabled}
        aria-label="Governance enabled"
        className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full border transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:cursor-wait disabled:opacity-60 ${
          enabled
            ? 'border-emerald-500 bg-emerald-500 shadow-[0_0_12px_rgba(16,185,129,0.5)]'
            : 'border-slate-300 bg-slate-300'
        }`}
        disabled={busy || settings.isError}
        onClick={() => update.mutate(!enabled)}
        role="switch"
        title={`${enabled ? 'Governance is enabled' : 'Governance is disabled'}; runtime only, resets on backend restart`}
        type="button"
      >
        <span
          className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
            enabled ? 'translate-x-[22px]' : 'translate-x-1'
          }`}
        />
      </button>
      <span aria-live="polite" className={`text-xs font-semibold ${enabled ? 'text-emerald-700' : 'text-slate-500'}`}>
        {settings.isPending
          ? <LoaderCircle aria-label="Loading governance setting" className="animate-spin" size={13} />
          : enabled ? 'On' : 'Off'}
      </span>
      {(settings.error || update.error) && (
        <span className="absolute right-0 top-full z-50 mt-2 max-w-64 rounded-md border border-red-200 bg-red-50 p-2 text-xs leading-5 text-red-800 shadow-lg" role="alert">
          Governance setting update failed: {(update.error || settings.error)?.message}
        </span>
      )}
    </div>
  )
}
