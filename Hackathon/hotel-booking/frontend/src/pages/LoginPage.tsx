import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { homePathFor } from '../auth/RequireRole'
import { AuthShell } from '../components/AuthShell'
import { config } from '../config'
import { Button, Field } from '../components/ui'

export function LoginPage() {
  const { user, login, expired } = useAuth()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) {
    const from = (location.state as { from?: string } | null)?.from
    const allowed = from && config.nav[user.role].some((n) => from === n.to || from.startsWith(n.to + '/'))
    return <Navigate to={allowed ? from : homePathFor(user.role)} replace />
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(username, password)
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Welcome back" subtitle={config.loginSubtitle}>
      <form onSubmit={submit} className="mt-8 space-y-4">
        {expired && !error && (
          <p role="status" className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">
            Your session has ended. Please sign in again.
          </p>
        )}
        {error && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {error}
          </p>
        )}
        <Field
          label="Username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          autoFocus
          required
        />
        <Field
          label="Password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          required
        />
        <Button type="submit" loading={busy} className="w-full py-2.5 shadow-sm transition-transform hover:-translate-y-0.5 active:translate-y-0">
          Sign in
        </Button>
      </form>

      <p className="mt-5 text-sm text-slate-600">
        New employee?{' '}
        <Link to="/register" className="font-medium text-brand-700 hover:underline">
          Create an account
        </Link>
      </p>

      <div className="mt-8 rounded-xl border border-dashed border-slate-300 p-4">
        <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Demo accounts</p>
        <div className="mt-2 flex gap-2">
          {config.demoAccounts.map((account) => (
            <Button
              key={account.username}
              variant="secondary"
              small
              onClick={() => {
                setUsername(account.username)
                setPassword(account.password)
              }}
            >
              Fill {account.label}
            </Button>
          ))}
        </div>
      </div>
    </AuthShell>
  )
}
