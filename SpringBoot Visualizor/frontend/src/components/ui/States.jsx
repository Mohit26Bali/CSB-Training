import Card from './Card'
import Icon from './Icon'

export function LoadingState({ label = 'Loading…' }) {
  return (
    <div role="status" className="flex items-center gap-2 py-6 text-sm text-muted">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-line-strong border-t-accent motion-reduce:animate-none" aria-hidden="true" />
      {label}
    </div>
  )
}

export function ErrorState({ children }) {
  return (
    <p role="alert" className="flex items-start gap-2 rounded-md border border-error-line bg-error-soft px-3 py-2 text-sm text-error">
      <Icon name="alert" className="mt-0.5 h-4 w-4" />
      <span>{children}</span>
    </p>
  )
}

export function EmptyState({ title, children, action }) {
  return (
    <Card className="border-dashed text-center">
      <p className="font-medium text-fg">{title}</p>
      {children && <p className="mt-1 text-sm text-fg-2">{children}</p>}
      {action && <div className="mt-3">{action}</div>}
    </Card>
  )
}
