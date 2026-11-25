import { useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { AuthForm, Field } from '../components/AuthForm'
import { PasswordStrength } from '../components/PasswordStrength'
import { useDocumentTitle } from '../hooks'
import { passwordProblem } from '../lib/password'
import { safeNext } from '../lib/watchlist'

/**
 * Sign-up page (route /register). The same rules are checked first here (so the user gets instant feedback) and again
 * by the backend (dto/RegisterRequest), whose message is shown if it still refuses (the password is judged by lib/password.ts here,
 * shown live by components/PasswordStrength, and by security/PasswordPolicy there), for example "Username 'asha' is
 * already taken". On success the new account is signed in immediately.
 */
export function RegisterPage() {
  useDocumentTitle('Create account')
  const { session, register } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const [form, setForm] = useState({ displayName: '', username: '', email: '', password: '' })
  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm((f) => ({ ...f, [key]: e.target.value }))

  if (session) return <Navigate to={next} replace />

  async function submit() {
    const username = form.username.trim()
    if (!form.displayName.trim()) throw new Error('Enter your name')
    if (!/^[A-Za-z0-9_.-]{3,50}$/.test(username)) throw new Error('Username must be 3 to 50 letters, digits, dots, dashes or underscores')
    if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) throw new Error('Enter a valid email address')
    const weak = passwordProblem(form.password, { username, email: form.email.trim() })
    if (weak) throw new Error(weak)
    await register({ displayName: form.displayName.trim(), username, email: form.email.trim(), password: form.password })
    navigate(next, { replace: true })
  }

  return (
    <AuthForm
      title="Create your account"
      subtitle="Keep a watchlist and rate what you have seen."
      submitLabel="Create account"
      onSubmit={submit}
      footer={
        <>
          Already have an account?{' '}
          <Link to={`/login${params.get('next') ? `?next=${encodeURIComponent(next)}` : ''}`} className="font-semibold text-ember-400 hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <Field label="Name" name="displayName" autoComplete="name" value={form.displayName} onChange={set('displayName')} />
      <Field label="Username" name="username" autoComplete="username" hint="3–50 letters, digits, dots, dashes or underscores" value={form.username} onChange={set('username')} />
      <Field label="Email" name="email" type="email" autoComplete="email" value={form.email} onChange={set('email')} />
      <div>
        <Field label="Password" name="password" type="password" autoComplete="new-password" maxLength={20} value={form.password} onChange={set('password')} />
        <PasswordStrength password={form.password} username={form.username} email={form.email} />
      </div>
    </AuthForm>
  )
}
