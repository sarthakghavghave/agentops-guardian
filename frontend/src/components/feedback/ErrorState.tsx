import { AlertTriangle } from 'lucide-react'

export function ErrorState({ message }: { message: string }) {
  return (
    <div className="flex items-start gap-3 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800" role="alert">
      <AlertTriangle aria-hidden="true" className="mt-0.5 shrink-0" size={17} />
      <div>
        <p className="font-medium">Unable to load data</p>
        <p className="mt-1 leading-5">{message}</p>
      </div>
    </div>
  )
}
