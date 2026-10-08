export function LoadingState({ label = 'Loading data' }: { label?: string }) {
  return (
    <div className="flex min-h-40 items-center justify-center gap-3 text-sm text-muted" role="status">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-200 border-t-accent" />
      {label}
    </div>
  )
}
