import { useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, errorMessage } from '../api/client'
import { useChangePassword, useDeleteAccount, useProfile, useUpdateProfile } from '../api/queries'
import type { UserProfile } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { Field } from '../components/AuthForm'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { PasswordStrength } from '../components/PasswordStrength'
import { Button, ErrorState, Skeleton } from '../components/ui'
import { useDocumentTitle } from '../hooks'
import { MAX_LENGTH, passwordProblem } from '../lib/password'
import { REGIONS, regionName } from '../lib/region'

/**
 * "My profile" (route /profile): the signed-in person's own account, in three cards. (1) Details: change the display name and
 * the country; the username and email are shown but fixed. (2) Password: change it, proving the current one; the new one is
 * judged by lib/password.ts exactly as at sign-up, with the live checklist of components/PasswordStrength. The server answers
 * with a fresh login token because changing the password ends every old session (backend ProfileService), and
 * auth/AuthContext swaps it in so this browser stays signed in. (3) Delete the account for good, confirmed with the password.
 * Data comes from GET /api/profile; the changes go to PUT /api/profile, PUT /api/profile/password and DELETE /api/profile.
 */
export function ProfilePage() {
  useDocumentTitle('My profile')
  const profile = useProfile()

  if (profile.isLoading) {
    return (
      <div aria-busy="true" className="mx-auto max-w-3xl space-y-4 px-4 pt-28 sm:px-8">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-56 w-full" />
        <Skeleton className="h-72 w-full" />
      </div>
    )
  }
  if (profile.isError) {
    return (
      <div className="px-4 pt-28">
        <ErrorState message={errorMessage(profile.error)} onRetry={() => profile.refetch()} />
      </div>
    )
  }
  const p = profile.data!
  return (
    <div className="mx-auto max-w-3xl px-4 pb-10 pt-28 sm:px-8">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">My profile</h1>
        {p.role === 'ADMIN' && <span className="rounded-full bg-ember-500/15 px-3 py-1 text-xs font-bold uppercase tracking-wide text-ember-400">Administrator</span>}
      </div>
      <div className="mt-8 space-y-6">
        <DetailsCard p={p} />
        <PasswordCard p={p} />
        <DangerCard />
      </div>
    </div>
  )
}

function Card({ title, hint, children }: { title: string; hint?: string; children: ReactNode }) {
  return (
    <section aria-label={title} className="rounded-2xl border border-line bg-surface p-5 sm:p-6">
      <h2 className="text-lg font-bold text-ink">{title}</h2>
      {hint && <p className="mt-1 text-sm text-muted">{hint}</p>}
      <div className="mt-5">{children}</div>
    </section>
  )
}

function Notice({ tone, children }: { tone: 'good' | 'bad'; children: ReactNode }) {
  return (
    <p
      role={tone === 'bad' ? 'alert' : 'status'}
      className={`rounded-xl border px-4 py-3 text-sm text-ink ${tone === 'bad' ? 'border-ember-500/40 bg-ember-500/10' : 'border-emerald-500/40 bg-emerald-500/10'}`}
    >
      {children}
    </p>
  )
}

// ---------------------------------------------------------------- details

function DetailsCard({ p }: { p: UserProfile }) {
  const { updateSession } = useAuth()
  const save = useUpdateProfile()
  const [displayName, setDisplayName] = useState(p.displayName)
  const [region, setRegion] = useState(p.region)
  const [saved, setSaved] = useState(false)
  const [localError, setLocalError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSaved(false)
    setLocalError(null)
    if (!displayName.trim()) {
      setLocalError('Enter your name')
      return
    }
    try {
      const updated = await save.mutateAsync({ displayName: displayName.trim(), region })
      updateSession({ displayName: updated.displayName, region: updated.region })
      setDisplayName(updated.displayName)
      setSaved(true)
    } catch {
      /* shown below from save.error */
    }
  }

  return (
    <Card title="Your details" hint="Your username and email cannot be changed.">
      <form onSubmit={submit} noValidate className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Username" value={p.username} readOnly disabled />
          <Field label="Email" value={p.email} readOnly disabled />
        </div>
        <Field label="Display name" name="displayName" value={displayName} maxLength={60} autoComplete="name" onChange={(e) => setDisplayName(e.target.value)} />
        <label className="block">
          <span className="mb-1.5 block text-sm font-semibold text-ink">Country</span>
          <select
            value={region}
            onChange={(e) => setRegion(e.target.value)}
            className="h-11 w-full rounded-xl border border-line bg-bg px-3 text-sm text-ink focus:border-ember-400 focus:outline-none sm:w-72"
          >
            {(REGIONS.includes(region as (typeof REGIONS)[number]) ? REGIONS : [region, ...REGIONS]).map((code) => (
              <option key={code} value={code}>
                {regionName(code)}
              </option>
            ))}
          </select>
          <span className="mt-1 block text-xs text-faint">Streaming services are shown for this country first.</span>
        </label>
        {saved && <Notice tone="good">Your details were saved.</Notice>}
        {(localError || save.isError) && <Notice tone="bad">{localError ?? errorMessage(save.error)}</Notice>}
        <Button type="submit" loading={save.isPending}>
          Save details
        </Button>
      </form>
    </Card>
  )
}

// ---------------------------------------------------------------- password

function PasswordCard({ p }: { p: UserProfile }) {
  const { adoptSession } = useAuth()
  const change = useChangePassword()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [done, setDone] = useState(false)
  const [localError, setLocalError] = useState<string | null>(null)
  const fieldError = change.error instanceof ApiError ? change.error.fieldErrors.newPassword : undefined

  async function submit(e: FormEvent) {
    e.preventDefault()
    setDone(false)
    setLocalError(null)
    if (!current) return setLocalError('Enter your current password')
    const weak = passwordProblem(next, { username: p.username, email: p.email })
    if (weak) return setLocalError(weak)
    if (confirm !== next) return setLocalError('The new passwords do not match')
    try {
      const fresh = await change.mutateAsync({ currentPassword: current, newPassword: next })
      adoptSession(fresh) // the old token no longer works; this one keeps the person signed in
      setCurrent('')
      setNext('')
      setConfirm('')
      setDone(true)
    } catch {
      /* shown below from change.error */
    }
  }

  return (
    <Card title="Change password" hint="You will stay signed in here; every other device is signed out.">
      <form onSubmit={submit} noValidate className="space-y-4">
        <Field label="Current password" name="currentPassword" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
        <div>
          <Field
            label="New password"
            name="newPassword"
            type="password"
            autoComplete="new-password"
            maxLength={MAX_LENGTH}
            value={next}
            error={fieldError}
            onChange={(e) => setNext(e.target.value)}
          />
          <PasswordStrength password={next} username={p.username} email={p.email} />
        </div>
        <Field label="Confirm new password" name="confirmPassword" type="password" autoComplete="new-password" maxLength={MAX_LENGTH} value={confirm} onChange={(e) => setConfirm(e.target.value)} />
        {done && <Notice tone="good">Password changed. Your other sessions were signed out.</Notice>}
        {(localError || change.isError) && <Notice tone="bad">{localError ?? errorMessage(change.error)}</Notice>}
        <Button type="submit" loading={change.isPending}>
          Change password
        </Button>
      </form>
    </Card>
  )
}

// ---------------------------------------------------------------- delete

function DangerCard() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const remove = useDeleteAccount()
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')

  function close() {
    setOpen(false)
    setPassword('')
    remove.reset()
  }

  async function confirm() {
    if (!password) return
    try {
      await remove.mutateAsync({ password })
      logout()
      navigate('/login', { replace: true })
    } catch {
      /* shown in the dialog from remove.error */
    }
  }

  return (
    <section aria-label="Delete account" className="rounded-2xl border border-rose-500/40 bg-surface p-5 sm:p-6">
      <h2 className="text-lg font-bold text-ink">Delete account</h2>
      <p className="mt-1 text-sm text-muted">This permanently removes your account and your whole watchlist. It cannot be undone.</p>
      <Button variant="danger" className="mt-5" onClick={() => setOpen(true)}>
        Delete my account…
      </Button>
      <ConfirmDialog
        open={open}
        title="Delete your account?"
        confirmLabel="Delete my account"
        busy={remove.isPending}
        error={remove.isError ? errorMessage(remove.error) : null}
        onConfirm={confirm}
        onCancel={close}
      >
        <p className="text-sm text-ink">Your account and watchlist will be deleted for good. Type your password to confirm.</p>
        <Field label="Your password" name="deletePassword" type="password" autoComplete="current-password" autoFocus value={password} onChange={(e) => setPassword(e.target.value)} />
      </ConfirmDialog>
    </section>
  )
}
