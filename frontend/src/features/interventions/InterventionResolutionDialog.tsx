import { useEffect, useRef, useState, type FormEvent } from 'react'
import { AlertTriangle, X } from 'lucide-react'
import type { GovernanceInterventionResponse } from '../../types/intervention'
import { formatInterventionValue } from './InterventionStatusBadge'

export type ResolutionAction = 'approve' | 'reject'

type InterventionResolutionDialogProps = {
  action: ResolutionAction
  intervention: GovernanceInterventionResponse
  isPending: boolean
  errorMessage: string | null
  onClose: () => void
  onSubmit: (resolvedBy: string, resolutionReason: string) => void
}

export function InterventionResolutionDialog({
  action,
  intervention,
  isPending,
  errorMessage,
  onClose,
  onSubmit,
}: InterventionResolutionDialogProps) {
  const [resolvedBy, setResolvedBy] = useState('')
  const [resolutionReason, setResolutionReason] = useState('')
  const [validationMessage, setValidationMessage] = useState<string | null>(null)
  const resolverInputRef = useRef<HTMLInputElement>(null)
  const approve = action === 'approve'

  useEffect(() => {
    resolverInputRef.current?.focus()
  }, [])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const resolver = resolvedBy.trim()
    const reason = resolutionReason.trim()
    if (!resolver || !reason) {
      setValidationMessage('Enter both your identity and a resolution reason.')
      return
    }

    setValidationMessage(null)
    onSubmit(resolver, reason)
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-ink-950/50 p-0 sm:items-center sm:p-5"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !isPending) onClose()
      }}
    >
      <section
        aria-labelledby="resolution-dialog-title"
        aria-modal="true"
        className="max-h-[90vh] w-full overflow-y-auto rounded-t-xl border border-line bg-white shadow-xl sm:max-w-xl sm:rounded-xl"
        onKeyDown={(event) => {
          if (event.key === 'Escape' && !isPending) onClose()
        }}
        role="dialog"
      >
        <header className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
          <div>
            <p className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-amber-800">
              <AlertTriangle aria-hidden="true" size={14} />
              Consequential governance decision
            </p>
            <h2 id="resolution-dialog-title" className="mt-2 text-lg font-semibold text-ink-950">
              {approve ? 'Approve intervention?' : 'Reject intervention?'}
            </h2>
          </div>
          <button
            aria-label="Close confirmation"
            className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 disabled:opacity-50"
            disabled={isPending}
            onClick={onClose}
            type="button"
          >
            <X aria-hidden="true" size={18} />
          </button>
        </header>

        <form onSubmit={handleSubmit}>
          <div className="space-y-4 px-5 py-5">
            <dl className="grid grid-cols-2 gap-3 rounded-md border border-line bg-slate-50 p-3 text-xs">
              <div>
                <dt className="text-muted">Capability</dt>
                <dd className="mt-1 font-medium text-slate-800">
                  {formatInterventionValue(intervention.capability)}
                </dd>
              </div>
              <div>
                <dt className="text-muted">Risk level</dt>
                <dd className="mt-1 font-semibold text-slate-800">
                  {formatInterventionValue(intervention.riskLevel)}
                </dd>
              </div>
              <div className="col-span-2">
                <dt className="text-muted">Reason for intervention</dt>
                <dd className="mt-1 whitespace-pre-wrap break-words leading-5 text-slate-700">
                  {intervention.reason}
                </dd>
              </div>
            </dl>

            <label className="block text-sm font-medium text-slate-700" htmlFor="resolved-by">
              Your identity
              <span className="ml-1 text-red-700" aria-hidden="true">*</span>
              <input
                ref={resolverInputRef}
                autoComplete="name"
                className="mt-1.5 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent focus:ring-2 focus:ring-accent/20 disabled:bg-slate-50"
                disabled={isPending}
                id="resolved-by"
                maxLength={200}
                onChange={(event) => setResolvedBy(event.target.value)}
                placeholder="Enter your name or operator ID"
                required
                value={resolvedBy}
              />
            </label>

            <label className="block text-sm font-medium text-slate-700" htmlFor="resolution-reason">
              Resolution reason
              <span className="ml-1 text-red-700" aria-hidden="true">*</span>
              <textarea
                className="mt-1.5 block min-h-24 w-full resize-y rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent focus:ring-2 focus:ring-accent/20 disabled:bg-slate-50"
                disabled={isPending}
                id="resolution-reason"
                maxLength={2000}
                onChange={(event) => setResolutionReason(event.target.value)}
                placeholder="Explain the governance decision"
                required
                value={resolutionReason}
              />
            </label>

            <p className="text-xs leading-5 text-slate-600">
              {approve
                ? 'Approval records a human governance decision only. It does not bypass policy or replay the original tool call.'
                : 'Rejection records a human governance decision. The original tool call remains blocked.'}
            </p>

            {(validationMessage || errorMessage) && (
              <p className="text-sm text-red-700" role="alert">
                {validationMessage ?? errorMessage}
              </p>
            )}
          </div>

          <footer className="flex flex-col-reverse gap-2 border-t border-line bg-slate-50 px-5 py-4 sm:flex-row sm:justify-end">
            <button
              className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
              disabled={isPending}
              onClick={onClose}
              type="button"
            >
              Cancel
            </button>
            <button
              className={`rounded-md px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60 ${
                approve ? 'bg-accent hover:bg-accent/90' : 'bg-red-700 hover:bg-red-800'
              }`}
              disabled={isPending}
              type="submit"
            >
              {isPending
                ? 'Recording decision…'
                : approve
                  ? 'Record approval'
                  : 'Confirm rejection'}
            </button>
          </footer>
        </form>
      </section>
    </div>
  )
}
