import { useState, type FormEvent } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { ApiError, errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { homePathFor } from '../auth/RequireRole'
import { AuthShell } from '../components/AuthShell'
import { Button, Field } from '../components/ui'

export function RegisterPage() {
  const { user, register } = useAuth()
  const [form, setForm] = useState({ fullname: '', username: '', password: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={homePathFor(user.role)} replace />

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setBusy(true)
    try {
      await register(form)
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) setFieldErrors(err.fieldErrors)
      else setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Create your account" subtitle="Employee accounts only. Admins are set up by IT.">
      <form onSubmit={submit} noValidate className="mt-8 space-y-4">
        {error && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {error}
          </p>
        )}
        <Field label="Full name" value={form.fullname} onChange={set('fullname')} error={fieldErrors.fullname} autoComplete="name" autoFocus />
        <Field
          label="Username"
          value={form.username}
          onChange={set('username')}
          error={fieldErrors.username}
          hint="3–30 characters: letters, digits, . _ -"
          autoComplete="username"
        />
        <Field
          label="Password"
          type="password"
          value={form.password}
          onChange={set('password')}
          error={fieldErrors.password}
          hint="At least 8 characters"
          autoComplete="new-password"
        />
        <Button type="submit" loading={busy} className="w-full py-2.5 shadow-sm transition-transform hover:-translate-y-0.5 active:translate-y-0">
          Create account
        </Button>
      </form>

      <p className="mt-5 text-sm text-slate-600">
        Already registered?{' '}
        <Link to="/login" className="font-medium text-brand-700 hover:underline">
          Sign in
        </Link>
      </p>
    </AuthShell>
  )
}
