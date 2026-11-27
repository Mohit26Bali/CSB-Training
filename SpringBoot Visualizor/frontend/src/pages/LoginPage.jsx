import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { describeError } from '../api/errors'
import { useAuth } from '../auth/useAuth'
import AuthLayout from '../components/AuthLayout'
import ApiStatus from '../components/ApiStatus'
import FormField from '../components/FormField'

export default function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ username: '', password: '' })
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  const destination = location.state?.from?.pathname ?? '/'
  const notice = location.state?.registered ? 'Account created. You can log in now.' : null

  if (user) {
    return <Navigate to={destination} replace />
  }

  const update = (field) => (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }))

  async function handleSubmit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      await login(form.username, form.password)
      navigate(destination, { replace: true })
    } catch (err) {
      const { message, fields } = describeError(err)
      setError(message)
      setFieldErrors(fields)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      <form onSubmit={handleSubmit} className="w-full max-w-sm space-y-4 rounded-xl border border-line bg-surface p-6">
        <h1 className="text-2xl font-bold text-fg">Log in to SpringLab</h1>
        {notice && <p className="rounded bg-success-soft p-2 text-sm text-success">{notice}</p>}
        {error && (
          <p role="alert" className="rounded-md border border-error-line bg-error-soft px-3 py-2 text-sm text-error">
            {error}
          </p>
        )}
        <FormField
          label="Username"
          value={form.username}
          onChange={update('username')}
          error={fieldErrors.username}
          autoComplete="username"
          required
        />
        <FormField
          label="Password"
          type="password"
          value={form.password}
          onChange={update('password')}
          error={fieldErrors.password}
          autoComplete="current-password"
          required
        />
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded bg-accent py-2 font-medium text-on-accent hover:bg-accent-hover disabled:opacity-60"
        >
          {submitting ? 'Logging in…' : 'Log in'}
        </button>
        <p className="text-sm text-fg-2">
          No account?{' '}
          <Link to="/register" className="text-accent hover:underline">
            Register
          </Link>
        </p>
        <ApiStatus />
      </form>
    </AuthLayout>
  )
}
