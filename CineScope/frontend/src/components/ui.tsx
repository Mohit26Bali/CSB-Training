import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { buttonClass, type ButtonVariant } from '../lib/buttonStyles'
import { Icon } from './Icon'

/**
 * Shared building blocks (buttons, chips, loading / error / empty states) used by every page, so spacing, colours and
 * focus behaviour stay identical everywhere. Colours come from the tokens in src/index.css.
 */

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  loading?: boolean
}

export function Button({ variant = 'primary', loading, disabled, children, className = '', ...rest }: ButtonProps) {
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || loading}
      className={`${buttonClass(variant)} disabled:cursor-not-allowed ${className}`}
    >
      {loading && <Spinner small />}
      {children}
    </button>
  )
}

export function Chip({ children, className = '', 'data-testid': testId }: { children: ReactNode; className?: string; 'data-testid'?: string }) {
  return (
    <span data-testid={testId} className={`inline-flex items-center rounded-full border border-line px-3 py-1 text-xs font-medium text-muted ${className}`}>
      {children}
    </span>
  )
}

export function Spinner({ small }: { small?: boolean }) {
  return (
    <span
      role="presentation"
      className={`inline-block animate-spin rounded-full border-2 border-current border-t-transparent ${small ? 'size-4' : 'size-6'}`}
    />
  )
}

export function Skeleton({ className = '' }: { className?: string }) {
  return <div aria-hidden="true" className={`skeleton rounded-xl ${className}`} />
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="mx-auto max-w-lg rounded-2xl border border-line bg-surface p-8 text-center">
      <span className="mx-auto mb-3 grid size-12 place-items-center rounded-full bg-ember-500/15 text-ember-400">
        <Icon name="alert" className="size-6" />
      </span>
      <p className="font-semibold text-ink">We couldn’t load this</p>
      <p className="mt-1 text-sm text-muted">{message}</p>
      {onRetry && (
        <Button variant="secondary" className="mt-5" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

export function EmptyState({ title, hint, icon = 'search' }: { title: string; hint?: string; icon?: 'search' | 'film' }) {
  return (
    <div className="mx-auto max-w-md rounded-2xl border border-dashed border-line p-10 text-center">
      <span className="mx-auto mb-3 grid size-12 place-items-center rounded-full bg-ink/8 text-faint">
        <Icon name={icon} className="size-6" />
      </span>
      <p className="font-semibold text-ink">{title}</p>
      {hint && <p className="mt-1 text-sm text-muted">{hint}</p>}
    </div>
  )
}

export function SectionTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-4 flex items-end justify-between gap-4">
      <h2 className="text-xl font-bold tracking-tight text-ink sm:text-2xl">{children}</h2>
      {action}
    </div>
  )
}
