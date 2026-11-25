import {
  useId,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
} from 'react'
import type { AccountStatus, TransactionStatus } from '../api/types'
import { Icon } from './Icon'

/**
 * Shared building blocks (buttons, fields, cards, badges, loading / error / empty states).
 * Every page composes its screen from these, so spacing, colours and focus behaviour stay identical everywhere.
 * Colours come from the tokens in src/index.css, which is why nothing here needs dark: variants for neutrals.
 */

/* ---------- Button ---------- */

type Variant = 'primary' | 'secondary' | 'danger' | 'success' | 'ghost'

const variants: Record<Variant, string> = {
  primary: 'bg-brand-600 text-white shadow-sm hover:bg-brand-700 disabled:bg-brand-600/40',
  secondary: 'border border-line bg-surface text-ink hover:bg-surface-2 disabled:text-faint',
  danger: 'bg-rose-600 text-white shadow-sm hover:bg-rose-700 disabled:bg-rose-600/40',
  success: 'bg-mint-600 text-white shadow-sm hover:bg-mint-500 disabled:bg-mint-600/40',
  ghost: 'text-muted hover:bg-brand-500/10 hover:text-ink disabled:text-faint',
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  loading?: boolean
  small?: boolean
}

export function Button({ variant = 'primary', loading, small, disabled, children, className = '', ...rest }: ButtonProps) {
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition-all disabled:cursor-not-allowed ${
        small ? 'px-3 py-1.5 text-sm' : 'px-4 py-2.5 text-sm'
      } ${variants[variant]} ${className}`}
    >
      {loading && <Spinner small />}
      {children}
    </button>
  )
}

/* ---------- Surfaces ---------- */

export function Card({ children, className = '', padded = true }: { children: ReactNode; className?: string; padded?: boolean }) {
  return (
    <section className={`card-shadow rounded-2xl border border-line bg-surface ${padded ? 'p-5 sm:p-6' : ''} ${className}`}>
      {children}
    </section>
  )
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}

/* ---------- Form controls ---------- */

interface FieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'prefix'> {
  label: string
  error?: string
  hint?: string
  /** Text or icon shown inside the input on the left, e.g. the rupee sign. */
  prefix?: ReactNode
  /** Control shown inside the input on the right, e.g. the show / hide password button. */
  suffix?: ReactNode
}

const inputBase =
  'w-full rounded-xl border bg-surface px-3.5 py-2.5 text-sm text-ink placeholder:text-faint transition-colors focus:border-brand-500 focus:outline-none focus:ring-4 focus:ring-brand-500/15 disabled:bg-surface-2 disabled:text-faint'

export function Field({ label, error, hint, prefix, suffix, className = '', id, ...rest }: FieldProps) {
  const autoId = useId()
  const inputId = id ?? autoId
  const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined
  return (
    <div className={className}>
      <label htmlFor={inputId} className="mb-1.5 block text-sm font-medium text-ink">
        {label}
      </label>
      <div className="relative">
        {prefix && (
          <span className="pointer-events-none absolute inset-y-0 left-3.5 flex items-center text-sm text-muted">{prefix}</span>
        )}
        <input
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          {...rest}
          className={`${inputBase} ${prefix ? 'pl-8' : ''} ${suffix ? 'pr-11' : ''} ${error ? 'border-rose-500' : 'border-line'}`}
        />
        {suffix && <span className="absolute inset-y-0 right-1.5 flex items-center">{suffix}</span>}
      </div>
      {error ? (
        <p id={`${inputId}-error`} role="alert" className="mt-1.5 text-xs text-rose-600 dark:text-rose-400">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${inputId}-hint`} className="mt-1.5 text-xs text-faint">
            {hint}
          </p>
        )
      )}
    </div>
  )
}

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string
  error?: string
  children: ReactNode
}

export function SelectField({ label, error, className = '', id, children, ...rest }: SelectFieldProps) {
  const autoId = useId()
  const selectId = id ?? autoId
  return (
    <div className={className}>
      <label htmlFor={selectId} className="mb-1.5 block text-sm font-medium text-ink">
        {label}
      </label>
      <div className="relative">
        <select id={selectId} {...rest} className={`${inputBase} appearance-none pr-10 ${error ? 'border-rose-500' : 'border-line'}`}>
          {children}
        </select>
        <Icon name="chevronDown" className="pointer-events-none absolute right-3.5 top-1/2 size-4 -translate-y-1/2 text-faint" />
      </div>
      {error && (
        <p role="alert" className="mt-1.5 text-xs text-rose-600 dark:text-rose-400">
          {error}
        </p>
      )}
    </div>
  )
}

/** Alert box for form-level errors and notices. */
export function Notice({ tone, children }: { tone: 'error' | 'warning' | 'success' | 'info'; children: ReactNode }) {
  const styles = {
    error: 'border-rose-500/30 bg-rose-500/10 text-rose-800 dark:text-rose-200',
    warning: 'border-amber-500/30 bg-amber-500/10 text-amber-900 dark:text-amber-200',
    success: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-900 dark:text-emerald-200',
    info: 'border-brand-500/25 bg-brand-500/10 text-brand-800 dark:text-brand-200',
  }
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={`flex items-start gap-2.5 rounded-xl border px-3.5 py-3 text-sm ${styles[tone]}`}>
      <Icon name={tone === 'success' ? 'checkCircle' : 'alert'} className="mt-0.5 size-4 shrink-0" />
      <div>{children}</div>
    </div>
  )
}

/* ---------- Feedback states ---------- */

export function Spinner({ small }: { small?: boolean }) {
  return (
    <span
      role="presentation"
      className={`inline-block animate-spin rounded-full border-2 border-current border-t-transparent ${small ? 'size-4' : 'size-6'}`}
    />
  )
}

export function Skeleton({ className = '' }: { className?: string }) {
  return <div aria-hidden="true" className={`skeleton rounded-lg ${className}`} />
}

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-3 py-16 text-muted">
      <Spinner />
      <span>{label}</span>
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="rounded-2xl border border-rose-500/30 bg-rose-500/10 p-6 text-center">
      <p className="font-semibold text-rose-800 dark:text-rose-200">We couldn’t load this</p>
      <p className="mt-1 text-sm text-rose-700 dark:text-rose-300">{message}</p>
      {onRetry && (
        <Button variant="secondary" small className="mt-4" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

export function EmptyState({ title, hint, action, icon = 'receipt' }: { title: string; hint?: string; action?: ReactNode; icon?: 'receipt' | 'wallet' | 'search' }) {
  return (
    <div className="rounded-2xl border border-dashed border-line bg-surface p-10 text-center">
      <span className="mx-auto mb-3 grid size-12 place-items-center rounded-2xl bg-brand-500/10 text-brand-600 dark:text-brand-300">
        <Icon name={icon} className="size-6" />
      </span>
      <p className="font-semibold text-ink">{title}</p>
      {hint && <p className="mx-auto mt-1 max-w-sm text-sm text-muted">{hint}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

/* ---------- Badges ---------- */

const accountStatusStyles: Record<AccountStatus, string> = {
  ACTIVE: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
  INACTIVE: 'bg-amber-500/14 text-amber-800 dark:text-amber-300',
  BLOCKED: 'bg-rose-500/12 text-rose-700 dark:text-rose-300',
}

const accountStatusLabels: Record<AccountStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  BLOCKED: 'Blocked',
}

export function StatusBadge({ status }: { status: AccountStatus }) {
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ${accountStatusStyles[status]}`}>
      <span className="size-1.5 rounded-full bg-current" />
      {accountStatusLabels[status]}
    </span>
  )
}

export function TxStatusBadge({ status }: { status: TransactionStatus }) {
  return (
    <span
      className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ${
        status === 'SUCCESS' ? 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300' : 'bg-rose-500/12 text-rose-700 dark:text-rose-300'
      }`}
    >
      {status === 'SUCCESS' ? 'Success' : 'Failed'}
    </span>
  )
}

export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' | 'lg' }) {
  const parts = name.split(/\s+/).filter(Boolean).slice(0, 2)
  const text = parts.map((p) => p[0]!.toUpperCase()).join('')
  const dims = { sm: 'size-8 text-xs', md: 'size-10 text-sm', lg: 'size-14 text-lg' }[size]
  return (
    <span className={`grid shrink-0 place-items-center rounded-full bg-linear-to-br from-brand-500 to-brand-800 font-semibold text-white ${dims}`}>
      {text}
    </span>
  )
}

/** Pill-style switch between a few options, e.g. "Own account / Another account". */
export function Segmented<T extends string>({
  value,
  onChange,
  options,
  label,
}: {
  value: T
  onChange: (value: T) => void
  options: { value: T; label: string }[]
  label: string
}) {
  return (
    <div role="radiogroup" aria-label={label} className="inline-flex rounded-xl border border-line bg-surface-2 p-1">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={value === option.value}
          onClick={() => onChange(option.value)}
          className={`rounded-lg px-3.5 py-1.5 text-sm font-medium transition-colors ${
            value === option.value ? 'bg-surface text-ink shadow-sm' : 'text-muted hover:text-ink'
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
