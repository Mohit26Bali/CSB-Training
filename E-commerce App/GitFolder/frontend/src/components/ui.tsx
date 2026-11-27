import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react'
import { formatPrice } from '../lib/format'

type ButtonVariant = 'primary' | 'accent' | 'outline' | 'ghost' | 'danger'

const buttonStyles: Record<ButtonVariant, string> = {
  primary: 'bg-brand-600 text-white hover:bg-brand-700 disabled:bg-brand-300',
  accent: 'bg-accent-400 text-slate-900 hover:bg-accent-500 disabled:opacity-60',
  outline: 'border border-slate-300 bg-white text-slate-800 hover:border-brand-500 hover:text-brand-700 disabled:opacity-60',
  ghost: 'text-brand-700 hover:bg-brand-50 disabled:opacity-60',
  danger: 'border border-red-200 bg-white text-red-600 hover:bg-red-50 disabled:opacity-60',
}

export function Button({
  variant = 'primary',
  busy = false,
  className = '',
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; busy?: boolean }) {
  return (
    <button
      {...props}
      disabled={props.disabled || busy}
      className={`inline-flex items-center justify-center gap-2 rounded-md px-4 py-2.5 text-sm font-semibold transition-colors ${buttonStyles[variant]} ${className}`}
    >
      {busy && <Spinner small />}
      {children}
    </button>
  )
}

export function Spinner({ small = false }: { small?: boolean }) {
  return (
    <span
      role="status"
      aria-label="Loading"
      className={`inline-block animate-spin rounded-full border-2 border-current border-r-transparent ${small ? 'h-4 w-4' : 'h-8 w-8 text-brand-600'}`}
    />
  )
}

export function PageSpinner() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center">
      <Spinner />
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="mx-auto my-12 max-w-md rounded-lg bg-white p-6 text-center shadow-sm">
      <p className="font-medium text-slate-800">Something went wrong</p>
      <p className="mt-1 text-sm text-slate-500">{message}</p>
      {onRetry && (
        <Button variant="outline" className="mt-4" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

export function EmptyState({ title, text, action }: { title: string; text?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center rounded-lg bg-white px-6 py-14 text-center shadow-sm">
      <p className="text-lg font-semibold text-slate-800">{title}</p>
      {text && <p className="mt-1 max-w-sm text-sm text-slate-500">{text}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function RatingBadge({ rating, count, single = false }: { rating: number; count?: number; single?: boolean }) {
  if (!single && !count) return <span className="text-xs text-slate-400">No ratings yet</span>
  const colour = rating >= 3.5 ? 'bg-green-700' : rating >= 2.5 ? 'bg-amber-500' : 'bg-red-600'
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-slate-500">
      <span className={`inline-flex items-center gap-0.5 rounded px-1.5 py-0.5 font-semibold text-white ${colour}`}>
        {single ? rating : rating.toFixed(1)} <span aria-hidden>★</span>
      </span>
      {!single && `(${count!.toLocaleString('en-IN')})`}
    </span>
  )
}

export function PriceLine({ price, mrp, discount, large = false }: { price: number; mrp: number; discount: number; large?: boolean }) {
  return (
    <div className="flex flex-wrap items-baseline gap-x-2">
      <span className={`font-semibold text-slate-900 ${large ? 'text-3xl' : 'text-base'}`}>{formatPrice(price)}</span>
      {discount > 0 && (
        <>
          <span className={`text-slate-400 line-through ${large ? 'text-base' : 'text-xs'}`}>{formatPrice(mrp)}</span>
          <span className={`font-semibold text-green-700 ${large ? 'text-base' : 'text-xs'}`}>{discount}% off</span>
        </>
      )}
    </div>
  )
}

export function Field({
  label,
  error,
  hint,
  className = '',
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string }) {
  const id = props.id ?? props.name
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-slate-700">
        {label}
      </label>
      <input
        {...props}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        className={`w-full rounded-md border bg-white px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 ${
          error ? 'border-red-400' : 'border-slate-300'
        }`}
      />
      {error ? (
        <p id={`${id}-error`} className="mt-1 text-xs text-red-600">
          {error}
        </p>
      ) : (
        hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>
      )}
    </div>
  )
}

export function SelectField({
  label,
  error,
  className = '',
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string; error?: string }) {
  const id = props.id ?? props.name
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-slate-700">
        {label}
      </label>
      <select
        {...props}
        id={id}
        aria-invalid={error ? true : undefined}
        className={`w-full rounded-md border bg-white px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 ${
          error ? 'border-red-400' : 'border-slate-300'
        }`}
      >
        {children}
      </select>
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  )
}

export function FormError({ message }: { message?: string | null }) {
  if (!message) return null
  return (
    <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
      {message}
    </p>
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`rounded-lg bg-white p-5 shadow-sm ${className}`}>{children}</section>
}
