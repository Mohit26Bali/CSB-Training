import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../api/client'
import { useCreateAdminUser, useUpdateAdminUser } from '../api/queries'
import type { AdminUser, Role } from '../api/types'
import { Field } from './AuthForm'
import { Modal } from './Modal'
import { PasswordStrength } from './PasswordStrength'
import { Button } from './ui'
import { MAX_LENGTH, passwordProblem } from '../lib/password'
import { REGIONS, regionName } from '../lib/region'

/**
 * The create / edit dialog of pages/AdminUsersPage. {@code user} null means "new account": username, email, password (with
 * the live strength checklist of components/PasswordStrength) and role are asked for. With a user it edits one: display name,
 * country, role and "enabled" can change (username and email are shown but fixed, and there is no password here - people change
 * their own). When the account being edited is the administrator's own, role and enabled are locked, matching the server rule
 * (backend AdminUserService), so the form never offers a change that would be refused. Whatever the server still refuses is
 * shown in the dialog; field messages (for example about the password) appear under their field.
 */
export function AdminUserForm({ user, isSelf, onClose, onDone }: { user: AdminUser | null; isSelf: boolean; onClose: () => void; onDone: (message: string) => void }) {
  const creating = user === null
  const create = useCreateAdminUser()
  const update = useUpdateAdminUser()
  const mutation = creating ? create : update
  const [form, setForm] = useState({
    username: '',
    email: '',
    password: '',
    displayName: user?.displayName ?? '',
    region: user?.region ?? 'IN',
    role: (user?.role ?? 'USER') as Role,
    enabled: user?.enabled ?? true,
  })
  const [localError, setLocalError] = useState<string | null>(null)
  const fieldErrors = mutation.error instanceof ApiError ? mutation.error.fieldErrors : {}
  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) => setForm((f) => ({ ...f, [key]: value }))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setLocalError(null)
    if (!form.displayName.trim()) return setLocalError('Enter a display name')
    try {
      if (creating) {
        if (!/^[A-Za-z0-9._-]{3,50}$/.test(form.username.trim())) return setLocalError('Username must be 3 to 50 letters, digits, dots, dashes or underscores')
        if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) return setLocalError('Enter a valid email address')
        const weak = passwordProblem(form.password, { username: form.username.trim(), email: form.email.trim() })
        if (weak) return setLocalError(weak)
        await create.mutateAsync({
          username: form.username.trim(),
          email: form.email.trim(),
          displayName: form.displayName.trim(),
          password: form.password,
          region: form.region,
          role: form.role,
        })
        onDone(`Account ${form.username.trim().toLowerCase()} was created.`)
      } else {
        await update.mutateAsync({ id: user.id, changes: { displayName: form.displayName.trim(), region: form.region, role: form.role, enabled: form.enabled } })
        onDone(`Changes to ${user.username} were saved.`)
      }
    } catch {
      /* shown below from mutation.error */
    }
  }

  const regions = REGIONS.includes(form.region as (typeof REGIONS)[number]) ? REGIONS : [form.region, ...REGIONS]
  const select = 'h-11 w-full rounded-xl border border-line bg-bg px-3 text-sm text-ink focus:border-ember-400 focus:outline-none disabled:opacity-60'

  return (
    <Modal open title={creating ? 'New account' : `Edit ${user.username}`} onClose={onClose} variant="form">
      <form onSubmit={submit} noValidate className="max-h-[80vh] space-y-4 overflow-y-auto bg-surface p-5">
        {creating ? (
          <>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Username" name="username" autoComplete="off" autoFocus value={form.username} error={fieldErrors.username} onChange={(e) => set('username', e.target.value)} />
              <Field label="Email" name="email" type="email" autoComplete="off" value={form.email} error={fieldErrors.email} onChange={(e) => set('email', e.target.value)} />
            </div>
          </>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Username" value={user.username} readOnly disabled />
            <Field label="Email" value={user.email} readOnly disabled />
          </div>
        )}
        <Field label="Display name" name="displayName" maxLength={60} value={form.displayName} error={fieldErrors.displayName} onChange={(e) => set('displayName', e.target.value)} />
        {creating && (
          <div>
            <Field label="Password" name="password" type="text" autoComplete="off" maxLength={MAX_LENGTH} value={form.password} error={fieldErrors.password} onChange={(e) => set('password', e.target.value)} />
            <PasswordStrength password={form.password} username={form.username} email={form.email} />
          </div>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block">
            <span className="mb-1.5 block text-sm font-semibold text-ink">Country</span>
            <select value={form.region} onChange={(e) => set('region', e.target.value)} className={select}>
              {regions.map((code) => (
                <option key={code} value={code}>
                  {regionName(code)}
                </option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="mb-1.5 block text-sm font-semibold text-ink">Role</span>
            <select value={form.role} onChange={(e) => set('role', e.target.value as Role)} disabled={isSelf} className={select}>
              <option value="USER">User</option>
              <option value="ADMIN">Administrator</option>
            </select>
          </label>
        </div>
        {!creating && (
          <label className="flex items-center gap-2.5 text-sm font-semibold text-ink">
            <input type="checkbox" checked={form.enabled} disabled={isSelf} onChange={(e) => set('enabled', e.target.checked)} className="size-5 accent-ember-500" />
            Account enabled
          </label>
        )}
        {isSelf && <p className="text-xs text-faint">This is your own account, so you cannot change your role or disable it.</p>}
        {(localError || mutation.isError) && (
          <p role="alert" className="rounded-xl border border-ember-500/40 bg-ember-500/10 px-4 py-3 text-sm text-ink">
            {localError ?? errorMessage(mutation.error)}
          </p>
        )}
        <div className="flex flex-wrap justify-end gap-3">
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending}>
            {creating ? 'Create account' : 'Save changes'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
