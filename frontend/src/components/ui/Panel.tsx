import type { ReactNode } from 'react'

type PanelProps = {
  title?: string
  description?: string
  action?: ReactNode
  children: ReactNode
  className?: string
}

export function Panel({ title, description, action, children, className = '' }: PanelProps) {
  return (
    <section className={`rounded-lg border border-line bg-white shadow-panel ${className}`}>
      {(title || description || action) && (
        <header className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
          <div>
            {title && <h2 className="text-sm font-semibold text-ink-950">{title}</h2>}
            {description && <p className="mt-1 text-xs leading-5 text-muted">{description}</p>}
          </div>
          {action}
        </header>
      )}
      {children}
    </section>
  )
}
