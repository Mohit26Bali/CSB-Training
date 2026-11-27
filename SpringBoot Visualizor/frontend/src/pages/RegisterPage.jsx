import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { registerRequest } from '../api/authApi'
import { describeError } from '../api/errors'
import { useAuth } from '../auth/useAuth'
import AuthLayout from '../components/AuthLayout'
import FormField from '../components/FormField'

export default function RegisterPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ username: '', email: '', password: '' })
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  if (user) {
    return <Navigate to="/" replace />
  }

  const update = (field) => (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }))

  async function handleSubmit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      // Only these three fields are sent; the server always assigns the STUDENT role.
      await registerRequest(form)
      navigate('/login', { replace: true, state: { registered: true } })
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
        <h1 className="text-2xl font-bold text-fg">Create a student account</h1>
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
          minLength={3}
          maxLength={50}
          required
        />
        <FormField
          label="Email"
          type="email"
          value={form.email}
          onChange={update('email')}
          error={fieldErrors.email}
          autoComplete="email"
          maxLength={120}
          required
        />
        <FormField
          label="Password (8-72 characters)"
          type="password"
          value={form.password}
          onChange={update('password')}
          error={fieldErrors.password || fieldErrors.passwordWithinBcryptLimit}
          autoComplete="new-password"
          minLength={8}
          maxLength={72}
          required
        />
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded bg-accent py-2 font-medium text-on-accent hover:bg-accent-hover disabled:opacity-60"
        >
          {submitting ? 'Creating account…' : 'Register'}
        </button>
        <p className="text-sm text-fg-2">
          Already registered?{' '}
          <Link to="/login" className="text-accent hover:underline">
            Log in
          </Link>
        </p>
      </form>
    </AuthLayout>
  )
}
