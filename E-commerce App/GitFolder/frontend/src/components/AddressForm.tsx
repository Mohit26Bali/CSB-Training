import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { createAddress, getStates, updateAddress } from '../api/endpoints'
import type { Address, AddressInput, AddressType } from '../api/types'
import { Button, Field, FormError, SelectField } from './ui'

const EMPTY: AddressInput = { fullName: '', phone: '', line1: '', line2: '', landmark: '', city: '', state: '', pincode: '', type: 'HOME' }

export function AddressForm({ address, onSaved, onCancel }: { address?: Address; onSaved: (a: Address) => void; onCancel?: () => void }) {
  const queryClient = useQueryClient()
  const states = useQuery({ queryKey: ['states'], queryFn: getStates, staleTime: Infinity })
  const [form, setForm] = useState<AddressInput>(address ? { ...address, line2: address.line2 ?? '', landmark: address.landmark ?? '' } : EMPTY)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: (body: AddressInput) => (address ? updateAddress(address.id, body) : createAddress(body)),
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['addresses'] })
      onSaved(saved)
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors)
        setError(Object.keys(err.fieldErrors).length ? null : err.message)
      }
    },
  })

  const set = (key: keyof AddressInput) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm({ ...form, [key]: e.target.value })
  const digits = (key: 'phone' | 'pincode', max: number) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [key]: e.target.value.replace(/\D/g, '').slice(0, max) })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setFieldErrors({})
    setError(null)
    save.mutate(form)
  }

  return (
    <form onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
      <Field label="Full name" name="fullName" autoComplete="name" value={form.fullName} onChange={set('fullName')} error={fieldErrors.fullName} />
      <Field label="10-digit mobile number" name="phone" inputMode="numeric" autoComplete="tel-national" value={form.phone} onChange={digits('phone', 10)} error={fieldErrors.phone} />
      <Field label="Flat, house no., building" name="line1" autoComplete="address-line1" value={form.line1} onChange={set('line1')} error={fieldErrors.line1} className="sm:col-span-2" />
      <Field label="Area, street, village (optional)" name="line2" autoComplete="address-line2" value={form.line2 ?? ''} onChange={set('line2')} error={fieldErrors.line2} className="sm:col-span-2" />
      <Field label="Landmark (optional)" name="landmark" value={form.landmark ?? ''} onChange={set('landmark')} error={fieldErrors.landmark} />
      <Field label="Pincode" name="pincode" inputMode="numeric" autoComplete="postal-code" value={form.pincode} onChange={digits('pincode', 6)} error={fieldErrors.pincode} />
      <Field label="City" name="city" autoComplete="address-level2" value={form.city} onChange={set('city')} error={fieldErrors.city} />
      <SelectField label="State" name="state" value={form.state} onChange={set('state')} error={fieldErrors.state}>
        <option value="">Select a state</option>
        {(states.data ?? []).map((s) => <option key={s}>{s}</option>)}
      </SelectField>
      <fieldset className="sm:col-span-2">
        <legend className="mb-1 text-sm font-medium text-slate-700">Address type</legend>
        <div className="flex gap-4">
          {(['HOME', 'WORK', 'OTHER'] as AddressType[]).map((t) => (
            <label key={t} className="flex items-center gap-2 text-sm">
              <input type="radio" name="type" checked={form.type === t} onChange={() => setForm({ ...form, type: t })} className="accent-brand-600" />
              {t[0] + t.slice(1).toLowerCase()}
            </label>
          ))}
        </div>
      </fieldset>
      {!address?.isDefault && (
        <label className="flex items-center gap-2 text-sm sm:col-span-2">
          <input type="checkbox" checked={!!form.makeDefault} onChange={(e) => setForm({ ...form, makeDefault: e.target.checked })} className="accent-brand-600" />
          Make this my default address
        </label>
      )}
      <div className="sm:col-span-2">
        <FormError message={error} />
      </div>
      <div className="flex gap-3 sm:col-span-2">
        <Button type="submit" variant="accent" busy={save.isPending}>Save address</Button>
        {onCancel && <Button type="button" variant="outline" onClick={onCancel}>Cancel</Button>}
      </div>
    </form>
  )
}

export function AddressText({ address }: { address: Omit<Address, 'id' | 'type' | 'isDefault'> }) {
  return (
    <div className="text-sm text-slate-700">
      <p className="font-semibold text-slate-900">
        {address.fullName} <span className="ml-2 font-normal text-slate-500">{address.phone}</span>
      </p>
      <p>
        {[address.line1, address.line2, address.landmark].filter(Boolean).join(', ')}
      </p>
      <p>
        {address.city}, {address.state} - <b>{address.pincode}</b>
      </p>
    </div>
  )
}
