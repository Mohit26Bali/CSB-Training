import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { homePathFor } from '../../auth/homePath'
import { AuthShell } from '../../components/AuthShell'
import { PasswordField } from '../../components/PasswordField'
import { Button, Field, Notice } from '../../components/ui'

/**
 * Sign-in screen (route /login). Calls auth/AuthContext#login, which stores the JWT session; once the user exists
 * this page redirects to the page they tried to open (state.from, set by auth/RequireRole) or to their home page.
 * The demo-account buttons only fill the form; they match the data seeded by the backend's DataInitializer.
 */
const demoAccounts = [
  { label: 'Customer · Ravi', username: 'ravi', password: 'customer123' },
  { label: 'Customer · Priya', username: 'priya', password: 'customer123' },
  { label: 'Administrator', username: 'admin', password: 'admin12345' },
]

export function LoginPage() {
  const { user, login, expired } = useAuth()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) {
    const from = (location.state as { from?: string } | null)?.from
    const allowed = from && (user.role === 'ADMIN' ? from.startsWith('/admin') : !from.startsWith('/admin'))
    return <Navigate to={allowed ? from : homePathFor(user.role)} replace />
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(username.trim(), password)
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Welcome back" subtitle="Sign in to manage your accounts and move money securely.">
      <form onSubmit={submit} className="mt-8 space-y-4" noValidate>
        {expired && !error && <Notice tone="warning">Your session has ended. Please sign in again.</Notice>}
        {error && <Notice tone="error">{error}</Notice>}
        <Field label="Username" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" autoFocus required />
        <PasswordField label="Password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
        <Button type="submit" loading={busy} disabled={!username || !password} className="w-full py-3">
          Sign in
        </Button>
      </form>

      <p className="mt-6 text-sm text-muted">
        New to Crestline Bank?{' '}
        <Link to="/register" className="font-semibold text-brand-600 hover:underline dark:text-brand-300">
          Open your account
        </Link>
      </p>

      <div className="mt-8 rounded-2xl border border-dashed border-line bg-surface-2 p-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-faint">Demo accounts</p>
        <div className="mt-3 flex flex-wrap gap-2">
          {demoAccounts.map((account) => (
            <Button
              key={account.username}
              variant="secondary"
              small
              onClick={() => {
                setUsername(account.username)
                setPassword(account.password)
                setError(null)
              }}
            >
              {account.label}
            </Button>
          ))}
        </div>
      </div>
    </AuthShell>
  )
}
