import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Logo } from '../components/Header'
import { Button, Field, FormError } from '../components/ui'

function safeNext(next: string | null, fallback: string) {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : fallback
}

function AuthFrame({ title, text, children }: { title: string; text: string; children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-10">
      <div className="grid w-full max-w-3xl overflow-hidden rounded-xl bg-white shadow-lg md:grid-cols-[2fr_3fr]">
        <div className="flex flex-col justify-between bg-brand-700 p-8 text-white">
          <Logo />
          <div className="mt-10">
            <h1 className="text-2xl font-bold">{title}</h1>
            <p className="mt-2 text-sm text-brand-100">{text}</p>
          </div>
        </div>
        <div className="p-8">{children}</div>
      </div>
    </div>
  )
}

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={user.role === 'ADMIN' ? '/admin' : safeNext(params.get('next'), '/')} replace />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const loggedIn = await login(email, password)
      navigate(loggedIn.role === 'ADMIN' ? '/admin' : safeNext(params.get('next'), '/'), { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Login failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthFrame title="Login" text="Get access to your orders, wishlist and recommendations.">
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Field label="Email" name="email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <Field label="Password" name="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        <FormError message={error} />
        <Button type="submit" variant="accent" className="w-full py-3" busy={busy} disabled={!email || !password}>
          Login
        </Button>
        <p className="text-center text-sm text-slate-600">
          New to CartNest?{' '}
          <Link to={`/register${params.get('next') ? `?next=${encodeURIComponent(params.get('next')!)}` : ''}`} className="font-semibold text-brand-700">
            Create an account
          </Link>
        </p>
        <p className="rounded-md bg-slate-50 p-3 text-xs text-slate-500">
          Demo: <b>riya@example.com</b> / Customer@123 · admin: <b>admin@cartnest.in</b> / Admin@123
        </p>
      </form>
    </AuthFrame>
  )
}

export function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={safeNext(params.get('next'), '/')} replace />
  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [key]: e.target.value })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setFieldErrors({})
    try {
      await register({ ...form, phone: form.phone || undefined })
      navigate(safeNext(params.get('next'), '/'), { replace: true })
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors)
        if (Object.keys(err.fieldErrors).length === 0) setError(err.message)
      } else {
        setError('Registration failed')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthFrame title="Looks like you're new here!" text="Sign up with your email to start shopping.">
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Field label="Full name" name="fullName" autoComplete="name" value={form.fullName} onChange={set('fullName')} error={fieldErrors.fullName} />
        <Field label="Email" name="email" type="email" autoComplete="email" value={form.email} onChange={set('email')} error={fieldErrors.email} />
        <Field label="Mobile number (optional)" name="phone" inputMode="numeric" maxLength={10} autoComplete="tel-national" value={form.phone}
          onChange={(e) => setForm({ ...form, phone: e.target.value.replace(/\D/g, '') })} error={fieldErrors.phone} />
        <Field label="Password" name="password" type="password" autoComplete="new-password" value={form.password} onChange={set('password')}
          error={fieldErrors.password} hint="At least 8 characters, with a letter and a digit" />
        <FormError message={error} />
        <Button type="submit" variant="accent" className="w-full py-3" busy={busy}>
          Create account
        </Button>
        <p className="text-center text-sm text-slate-600">
          Already have an account? <Link to={`/login${params.get('next') ? `?next=${encodeURIComponent(params.get('next')!)}` : ''}`} className="font-semibold text-brand-700">Login</Link>
        </p>
      </form>
    </AuthFrame>
  )
}
