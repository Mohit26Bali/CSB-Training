import { useEffect, useId, useRef, useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react'
import type { EnrollmentStatus } from '../api/types'

/* ---------- Button ---------- */

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

const variants: Record<Variant, string> = {
  primary: 'bg-brand-700 text-white hover:bg-brand-800 disabled:bg-slate-300',
  secondary: 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 disabled:text-slate-400',
  danger: 'bg-rose-600 text-white hover:bg-rose-700 disabled:bg-slate-300',
  ghost: 'text-slate-600 hover:bg-slate-100 disabled:text-slate-400',
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
      className={`inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors disabled:cursor-not-allowed ${
        small ? 'px-3 py-1.5 text-sm' : 'px-4 py-2 text-sm'
      } ${variants[variant]} ${className}`}
    >
      {loading && <Spinner small />}
      {children}
    </button>
  )
}

/* ---------- Feedback states ---------- */

export function Spinner({ small }: { small?: boolean }) {
  return (
    <span
      role="presentation"
      className={`inline-block animate-spin rounded-full border-2 border-current border-t-transparent ${
        small ? 'size-4' : 'size-6'
      }`}
    />
  )
}

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-3 py-16 text-slate-500">
      <Spinner />
      <span>{label}</span>
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-6 text-center">
      <p className="font-medium text-rose-900">We couldn’t load this</p>
      <p className="mt-1 text-sm text-rose-800">{message}</p>
      {onRetry && (
        <Button variant="secondary" small className="mt-4" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

export function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="rounded-xl border border-dashed border-slate-300 bg-white p-10 text-center">
      <p className="font-medium text-slate-800">{title}</p>
      {hint && <p className="mt-1 text-sm text-slate-500">{hint}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

/* ---------- Badges ---------- */

const statusStyles: Record<EnrollmentStatus, string> = {
  PENDING: 'bg-amber-100 text-amber-800',
  APPROVED: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-rose-100 text-rose-800',
}

const statusLabels: Record<EnrollmentStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
}

export function StatusBadge({ status }: { status: EnrollmentStatus }) {
  return (
    <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ${statusStyles[status]}`}>
      {statusLabels[status]}
    </span>
  )
}

export function Tag({ children, tone = 'slate' }: { children: ReactNode; tone?: 'slate' | 'green' }) {
  return (
    <span
      className={`inline-flex rounded-md px-2 py-0.5 text-xs font-medium ${
        tone === 'green' ? 'bg-brand-100 text-brand-800' : 'bg-slate-100 text-slate-600'
      }`}
    >
      {children}
    </span>
  )
}

/* ---------- Course picture ---------- */

const fallbackGradients = [
  'from-teal-600 to-emerald-800',
  'from-sky-600 to-indigo-800',
  'from-violet-600 to-fuchsia-800',
  'from-amber-500 to-rose-700',
  'from-cyan-600 to-blue-800',
]

/** The course photo, or a coloured tile with the category initial when there is none (or it fails to load). */
export function CourseImage({ src, category, className = '' }: { src: string | null; category: string; className?: string }) {
  const [failed, setFailed] = useState(false)
  useEffect(() => setFailed(false), [src])

  if (src && !failed) {
    return <img src={src} alt="" loading="lazy" onError={() => setFailed(true)} className={`object-cover ${className}`} />
  }
  const hash = [...category].reduce((sum, ch) => sum + ch.charCodeAt(0), 0)
  return (
    <div
      aria-hidden="true"
      className={`flex items-center justify-center bg-linear-to-br text-3xl font-semibold text-white/90 ${
        fallbackGradients[hash % fallbackGradients.length]
      } ${className}`}
    >
      {category.charAt(0).toUpperCase()}
    </div>
  )
}

/* ---------- Seat meter ---------- */

export function SeatBar({ approved, pending = 0, capacity }: { approved: number; pending?: number; capacity: number }) {
  const full = approved >= capacity
  const approvedPct = Math.min(100, (approved / capacity) * 100)
  const pendingPct = Math.min(100 - approvedPct, (pending / capacity) * 100)
  return (
    <div>
      <div
        role="meter"
        aria-label="Seats taken"
        aria-valuemin={0}
        aria-valuemax={capacity}
        aria-valuenow={approved}
        className="flex h-2 overflow-hidden rounded-full bg-slate-200"
      >
        <div className={full ? 'bg-rose-500' : 'bg-brand-600'} style={{ width: `${approvedPct}%` }} />
        <div className="bg-amber-300" style={{ width: `${pendingPct}%` }} />
      </div>
      <p className="mt-1.5 text-xs text-slate-500">
        {full ? 'Full' : `${capacity - approved} of ${capacity} seats left`}
        {pending > 0 && ` · ${pending} pending`}
      </p>
    </div>
  )
}

/* ---------- Form field ---------- */

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  hint?: string
}

export function Field({ label, error, hint, className = '', ...rest }: FieldProps) {
  const id = useId()
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-slate-700">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        {...rest}
        className={`w-full rounded-lg border bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 ${
          error ? 'border-rose-400' : 'border-slate-300'
        } ${className}`}
      />
      {error ? (
        <p id={`${id}-error`} className="mt-1 text-xs text-rose-700">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="mt-1 text-xs text-slate-500">
          {hint}
        </p>
      ) : null}
    </div>
  )
}

/* ---------- Dialogs ---------- */

interface ModalProps {
  title: string
  onClose: () => void
  children: ReactNode
}

export function Modal({ title, onClose, children }: ModalProps) {
  const titleId = useId()
  const panel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    panel.current?.querySelector<HTMLElement>('input, select, textarea, button')?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      previous?.focus()
    }
  }, [onClose])

  return (
    <div
      className="fixed inset-0 z-40 flex items-end justify-center bg-slate-900/50 p-4 sm:items-center"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-6 shadow-xl"
      >
        <h2 id={titleId} className="text-lg font-semibold text-slate-900">
          {title}
        </h2>
        <div className="mt-4">{children}</div>
      </div>
    </div>
  )
}

interface ConfirmProps {
  title: string
  message: string
  confirmLabel: string
  danger?: boolean
  loading?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({ title, message, confirmLabel, danger, loading, onConfirm, onCancel }: ConfirmProps) {
  return (
    <Modal title={title} onClose={onCancel}>
      <p className="text-sm text-slate-600">{message}</p>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel} disabled={loading}>
          Keep it
        </Button>
        <Button variant={danger ? 'danger' : 'primary'} onClick={onConfirm} loading={loading}>
          {confirmLabel}
        </Button>
      </div>
    </Modal>
  )
}

/* ---------- Page chrome ---------- */

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {actions}
    </div>
  )
}

export function formatDate(iso: string | null): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}
