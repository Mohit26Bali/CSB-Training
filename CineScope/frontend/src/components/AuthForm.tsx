import { useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import { AuthScreen } from './AuthScreen'
import { Brand } from './Brand'
import { Button } from './ui'

/**
 * The card shared by pages/LoginPage and pages/RegisterPage, placed on the picture screen of components/AuthScreen: logo,
 * heading, a form, an error banner and a link to the other page. The page supplies the fields and an async {@code onSubmit}; this component runs it, shows its failure (the
 * backend's own message, plus per-field messages when validation failed) and keeps the button busy meanwhile.
 */
export function AuthForm({
  title,
  subtitle,
  submitLabel,
  onSubmit,
  footer,
  children,
}: {
  title: string
  subtitle: string
  submitLabel: string
  onSubmit: () => Promise<void>
  footer: ReactNode
  children: ReactNode
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handle(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await onSubmit()
    } catch (err) {
      setError(err instanceof ApiError || err instanceof Error ? err.message : 'Something went wrong. Please try again.')
      setBusy(false)
    }
  }

  return (
    <AuthScreen>
      <Link to="/login" aria-label="CineScope sign in" className="mx-auto mb-6 flex w-fit rounded-lg bg-black/30 px-4 py-2 backdrop-blur lg:bg-transparent lg:px-0 lg:py-0 lg:backdrop-blur-none">
        <Brand />
      </Link>
      <div className="rounded-2xl border border-line bg-surface/95 p-6 shadow-2xl backdrop-blur sm:p-8">
        <h1 className="text-2xl font-extrabold tracking-tight text-ink">{title}</h1>
        <p className="mt-1 text-sm text-muted">{subtitle}</p>
        <form onSubmit={handle} noValidate className="mt-6 space-y-4">
          {children}
          {error && (
            <p role="alert" className="rounded-xl border border-ember-500/40 bg-ember-500/10 px-4 py-3 text-sm text-ink">
              {error}
            </p>
          )}
          <Button type="submit" loading={busy} className="w-full py-3">
            {submitLabel}
          </Button>
        </form>
      </div>
      <p className="mt-6 text-center text-sm text-muted">{footer}</p>
    </AuthScreen>
  )
}

/** One labelled input; {@code hint} explains the rule before the user trips over it, {@code error} says what the server refused. */
export function Field({
  label,
  hint,
  error,
  ...input
}: { label: string; hint?: string; error?: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-semibold text-ink">{label}</span>
      <input
        {...input}
        aria-invalid={error ? true : undefined}
        className={`h-11 w-full rounded-xl border bg-bg px-4 text-sm text-ink placeholder:text-faint focus:border-ember-400 focus:outline-none disabled:opacity-60 ${error ? 'border-ember-500' : 'border-line'}`}
      />
      {error && <span className="mt-1 block text-xs font-semibold text-ember-400">{error}</span>}
      {hint && !error && <span className="mt-1 block text-xs text-faint">{hint}</span>}
    </label>
  )
}
