import { useState, type FormEvent } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { errorMessage, fieldErrorsOf } from '../../api/client'
import type { RegisterInput } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { homePathFor } from '../../auth/homePath'
import { AuthShell } from '../../components/AuthShell'
import { PasswordField } from '../../components/PasswordField'
import { PasswordStrength } from '../../components/PasswordStrength'
import { Button, Field, Notice } from '../../components/ui'
import { passwordProblem } from '../../lib/password'
import { validateEmail, validateMobile, validateUsername, type Errors } from '../../lib/validation'

/**
 * Customer self-registration (route /register). Validates like the backend (lib/validation.ts mirrors
 * dto/RegisterRequest; the password rules are lib/password.ts, shown live by components/PasswordStrength), sends the form through auth/AuthContext#register - which creates the user and customer
 * profile and signs the new customer in - and shows any server-side field messages under the inputs.
 */
type Form = RegisterInput & { confirm: string }
const empty: Form = { fullName: '', username: '', email: '', mobile: '', address: '', password: '', confirm: '' }

function validate(form: Form): Errors<keyof Form> {
  const errors: Errors<keyof Form> = {}
  if (!form.fullName.trim()) errors.fullName = 'Enter your full name'
  errors.username = validateUsername(form.username.trim())
  errors.email = validateEmail(form.email.trim())
  errors.mobile = validateMobile(form.mobile.trim())
  if (!form.address.trim()) errors.address = 'Enter your address'
  errors.password = passwordProblem(form.password, { username: form.username.trim(), email: form.email.trim() })
  if (form.confirm !== form.password) errors.confirm = 'Passwords do not match'
  return Object.fromEntries(Object.entries(errors).filter(([, v]) => v)) as Errors<keyof Form>
}

export function RegisterPage() {
  const { user, register } = useAuth()
  const [form, setForm] = useState<Form>(empty)
  const [errors, setErrors] = useState<Errors<keyof Form>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={homePathFor(user.role)} replace />

  const set = (key: keyof Form) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((f) => ({ ...f, [key]: e.target.value }))
    if (errors[key]) setErrors((all) => ({ ...all, [key]: undefined }))
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setFormError(null)
    const found = validate(form)
    setErrors(found)
    if (Object.keys(found).length > 0) return

    setBusy(true)
    try {
      await register({
        fullName: form.fullName.trim(),
        username: form.username.trim(),
        email: form.email.trim(),
        mobile: form.mobile.trim(),
        address: form.address.trim(),
        password: form.password,
      })
    } catch (err) {
      const server = fieldErrorsOf(err)
      if (Object.keys(server).length > 0) setErrors(server as Errors<keyof Form>)
      else setFormError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Open your account" subtitle="It takes about two minutes. You can open a savings or current account right after.">
      <form onSubmit={submit} className="mt-7 space-y-4" noValidate>
        {formError && <Notice tone="error">{formError}</Notice>}

        <Field label="Full name" value={form.fullName} onChange={set('fullName')} error={errors.fullName} autoComplete="name" autoFocus />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Email" type="email" value={form.email} onChange={set('email')} error={errors.email} autoComplete="email" />
          <Field label="Mobile number" inputMode="numeric" maxLength={10} value={form.mobile} onChange={set('mobile')} error={errors.mobile} autoComplete="tel-national" prefix="+91" className="[&_input]:pl-11" />
        </div>
        <Field label="Address" value={form.address} onChange={set('address')} error={errors.address} autoComplete="street-address" />
        <Field label="Choose a username" value={form.username} onChange={set('username')} error={errors.username} autoComplete="username" hint="Letters, digits, dot, dash or underscore" />

        <div>
          <PasswordField label="Password" value={form.password} onChange={set('password')} error={errors.password} autoComplete="new-password" maxLength={20} />
          <PasswordStrength password={form.password} username={form.username} email={form.email} />
        </div>
        <PasswordField label="Confirm password" value={form.confirm} onChange={set('confirm')} error={errors.confirm} autoComplete="new-password" />

        <Button type="submit" loading={busy} className="w-full py-3">
          Create account
        </Button>
      </form>

      <p className="mt-6 text-sm text-muted">
        Already a customer?{' '}
        <Link to="/login" className="font-semibold text-brand-600 hover:underline dark:text-brand-300">
          Sign in
        </Link>
      </p>
    </AuthShell>
  )
}
