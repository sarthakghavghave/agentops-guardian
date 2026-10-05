import { Inbox } from 'lucide-react'

type EmptyStateProps = {
  title: string
  description: string
}

export function EmptyState({ title, description }: EmptyStateProps) {
  return (
    <div className="flex min-h-48 flex-col items-center justify-center px-6 py-10 text-center">
      <span className="mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 text-slate-500">
        <Inbox aria-hidden="true" size={18} />
      </span>
      <h3 className="text-sm font-medium text-ink-900">{title}</h3>
      <p className="mt-1 max-w-md text-sm leading-6 text-muted">{description}</p>
    </div>
  )
}
