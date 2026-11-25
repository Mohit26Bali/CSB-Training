import { useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { AuthForm, Field } from '../components/AuthForm'
import { useDocumentTitle } from '../hooks'
import { safeNext } from '../lib/watchlist'

/**
 * Sign-in page (route /login). Uses auth/AuthContext.login, which stores the session; afterwards the visitor is sent
 * back to where they were going. Already signed in? Goes straight there.
 */
export function LoginPage() {
  useDocumentTitle('Sign in')
  const { session, login } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  if (session) return <Navigate to={next} replace />

  async function submit() {
    if (!username.trim() || !password) throw new Error('Enter your username and password')
    await login(username.trim(), password)
    navigate(next, { replace: true })
  }

  return (
    <AuthForm
      title="Welcome back"
      subtitle="Sign in to see your watchlist."
      submitLabel="Sign in"
      onSubmit={submit}
      footer={
        <>
          New to CineScope?{' '}
          <Link to={`/register${params.get('next') ? `?next=${encodeURIComponent(next)}` : ''}`} className="font-semibold text-ember-400 hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <Field label="Username" name="username" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} />
      <Field label="Password" name="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
    </AuthForm>
  )
}
