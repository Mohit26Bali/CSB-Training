import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type FormEvent } from 'react'
import { useLocation } from 'react-router-dom'
import { ApiError } from '../api/client'
import { changePassword, deleteAddress, getAddresses, makeDefaultAddress, updateProfile } from '../api/endpoints'
import type { Address } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { AddressForm, AddressText } from '../components/AddressForm'
import { useToast } from '../components/Toast'
import { Button, Card, Field, FormError } from '../components/ui'
import { errorMessage } from '../hooks'

function ProfileForm() {
  const { user, setUser } = useAuth()
  const toast = useToast()
  const [fullName, setFullName] = useState(user!.fullName)
  const [phone, setPhone] = useState(user!.phone ?? '')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const save = useMutation({
    mutationFn: () => updateProfile({ fullName, phone: phone || null }),
    onSuccess: (u) => {
      setUser(u)
      setErrors({})
      toast('Profile saved')
    },
    onError: (e) => (e instanceof ApiError ? setErrors(e.fieldErrors) : toast(errorMessage(e), 'error')),
  })
  return (
    <form onSubmit={(e) => { e.preventDefault(); save.mutate() }} className="grid gap-4 sm:grid-cols-2" noValidate>
      <Field label="Full name" name="fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} error={errors.fullName} />
      <Field label="Mobile number" name="phone" inputMode="numeric" value={phone} onChange={(e) => setPhone(e.target.value.replace(/\D/g, '').slice(0, 10))} error={errors.phone} />
      <Field label="Email" name="email" value={user!.email} disabled hint="The email identifies your account and cannot be changed" className="sm:col-span-2" />
      <div><Button type="submit" busy={save.isPending}>Save changes</Button></div>
    </form>
  )
}

function PasswordForm() {
  const toast = useToast()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => changePassword({ currentPassword: current, newPassword: next }),
    onSuccess: () => {
      setCurrent('')
      setNext('')
      setErrors({})
      setError(null)
      toast('Password changed')
    },
    onError: (e) => {
      if (e instanceof ApiError) {
        setErrors(e.fieldErrors)
        setError(Object.keys(e.fieldErrors).length ? null : e.message)
      }
    },
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    save.mutate()
  }
  return (
    <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2" noValidate>
      <Field label="Current password" name="currentPassword" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} error={errors.currentPassword} />
      <Field label="New password" name="newPassword" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} error={errors.newPassword}
        hint="At least 8 characters, with a letter and a digit" />
      <div className="sm:col-span-2"><FormError message={error} /></div>
      <div><Button type="submit" busy={save.isPending} disabled={!current || !next}>Change password</Button></div>
    </form>
  )
}

function Addresses() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const addresses = useQuery({ queryKey: ['addresses'], queryFn: getAddresses })
  const [editing, setEditing] = useState<Address | 'new' | null>(null)
  const act = useMutation({
    mutationFn: (fn: () => Promise<unknown>) => fn(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['addresses'] }),
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  if (editing) {
    return <AddressForm address={editing === 'new' ? undefined : editing} onSaved={() => setEditing(null)} onCancel={() => setEditing(null)} />
  }
  return (
    <div className="space-y-3">
      {(addresses.data ?? []).map((a) => (
        <div key={a.id} className="flex flex-wrap items-start gap-3 rounded-md border border-slate-200 p-4">
          <div className="flex-1">
            <div className="mb-1 flex gap-2">
              <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-semibold text-slate-600">{a.type}</span>
              {a.isDefault && <span className="rounded bg-green-100 px-1.5 py-0.5 text-[10px] font-semibold text-green-800">DEFAULT</span>}
            </div>
            <AddressText address={a} />
          </div>
          <div className="flex gap-1">
            {!a.isDefault && <Button variant="ghost" onClick={() => act.mutate(() => makeDefaultAddress(a.id))}>Make default</Button>}
            <Button variant="ghost" onClick={() => setEditing(a)}>Edit</Button>
            <Button variant="ghost" className="text-red-600" onClick={() => window.confirm('Delete this address?') && act.mutate(() => deleteAddress(a.id))}>Delete</Button>
          </div>
        </div>
      ))}
      {(addresses.data ?? []).length < 10 && <Button variant="outline" onClick={() => setEditing('new')}>+ Add a new address</Button>}
    </div>
  )
}

export function AccountPage() {
  const { hash } = useLocation()
  useEffect(() => {
    if (hash) document.querySelector(hash)?.scrollIntoView({ behavior: 'smooth' })
  }, [hash])
  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <h1 className="text-lg font-semibold text-slate-900">My account</h1>
      <Card><h2 className="mb-4 font-semibold">Personal information</h2><ProfileForm /></Card>
      <Card><h2 id="addresses" className="mb-4 scroll-mt-40 font-semibold">Saved addresses</h2><Addresses /></Card>
      <Card><h2 className="mb-4 font-semibold">Change password</h2><PasswordForm /></Card>
    </div>
  )
}
