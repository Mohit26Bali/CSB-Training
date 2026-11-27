import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { createCoupon, getAdminCoupons, setCouponActive, updateCoupon, type AdminCoupon, type CouponInput } from '../api/admin'
import { useToast } from '../components/Toast'
import { Button, Card, ErrorState, Field, FormError, PageSpinner, SelectField } from '../components/ui'
import { errorMessage } from '../hooks'
import { formatDate, formatPrice } from '../lib/format'
import { PageHeader, td, th, Toggle } from './AdminLayout'

const STATE_STYLE: Record<AdminCoupon['state'], string> = {
  ACTIVE: 'bg-green-100 text-green-800',
  SCHEDULED: 'bg-blue-100 text-blue-800',
  EXPIRED: 'bg-slate-200 text-slate-600',
  USED_UP: 'bg-amber-100 text-amber-800',
  DISABLED: 'bg-red-100 text-red-700',
}

const today = new Date().toISOString().slice(0, 10)
const EMPTY: CouponInput = {
  code: '', description: '', discountType: 'FLAT', discountValue: 100, maxDiscount: null, minOrderAmount: 0,
  validFrom: `${today}T00:00:00`, validUntil: `${today.slice(0, 4)}-12-31T23:59:59`, usageLimit: null, perUserLimit: 1, active: true,
}

function CouponForm({ coupon, onDone }: { coupon?: AdminCoupon; onDone: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [form, setForm] = useState<CouponInput>(coupon ?? EMPTY)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => (coupon ? updateCoupon(coupon.id, form) : createCoupon(form)),
    onSuccess: (c) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'coupons'] })
      toast(coupon ? `${c.code} saved` : `${c.code} created`)
      onDone()
    },
    onError: (e) => {
      if (e instanceof ApiError) {
        setErrors(e.fieldErrors)
        setError(Object.keys(e.fieldErrors).length ? null : e.message)
      }
    },
  })
  const num = (key: keyof CouponInput) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [key]: e.target.value === '' ? null : Number(e.target.value.replace(/[^\d]/g, '')) })

  return (
    <form onSubmit={(e: FormEvent) => { e.preventDefault(); setErrors({}); setError(null); save.mutate() }} noValidate className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <Field label="Code" name="code" value={form.code} disabled={!!coupon} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} error={errors.code}
        hint={coupon ? 'The code cannot be changed' : '4-20 letters or digits'} />
      <Field label="Description" name="description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} error={errors.description} className="sm:col-span-1 lg:col-span-3" />
      <SelectField label="Type" name="discountType" value={form.discountType} onChange={(e) => setForm({ ...form, discountType: e.target.value as CouponInput['discountType'] })}>
        <option value="FLAT">₹ off</option>
        <option value="PERCENT">% off</option>
      </SelectField>
      <Field label={form.discountType === 'PERCENT' ? 'Percent off' : 'Rupees off'} name="discountValue" inputMode="numeric" value={form.discountValue ?? ''} onChange={num('discountValue')} error={errors.discountValue} />
      {form.discountType === 'PERCENT' && (
        <Field label="Maximum discount ₹ (optional)" name="maxDiscount" inputMode="numeric" value={form.maxDiscount ?? ''} onChange={num('maxDiscount')} error={errors.maxDiscount} />
      )}
      <Field label="Minimum order ₹" name="minOrderAmount" inputMode="numeric" value={form.minOrderAmount ?? ''} onChange={num('minOrderAmount')} error={errors.minOrderAmount} />
      <Field label="Valid from" name="validFrom" type="date" value={form.validFrom.slice(0, 10)} onChange={(e) => setForm({ ...form, validFrom: `${e.target.value}T00:00:00` })} error={errors.validFrom} />
      <Field label="Valid until" name="validUntil" type="date" value={form.validUntil.slice(0, 10)} onChange={(e) => setForm({ ...form, validUntil: `${e.target.value}T23:59:59` })} error={errors.validUntil} />
      <Field label="Total uses (optional)" name="usageLimit" inputMode="numeric" value={form.usageLimit ?? ''} onChange={num('usageLimit')} error={errors.usageLimit} hint="Empty means unlimited" />
      <Field label="Uses per customer" name="perUserLimit" inputMode="numeric" value={form.perUserLimit ?? ''} onChange={num('perUserLimit')} error={errors.perUserLimit} />
      <div className="sm:col-span-2 lg:col-span-4"><FormError message={error} /></div>
      <div className="flex gap-3 sm:col-span-2 lg:col-span-4">
        <Button type="submit" variant="accent" busy={save.isPending}>{coupon ? 'Save coupon' : 'Create coupon'}</Button>
        <Button type="button" variant="outline" onClick={onDone}>Cancel</Button>
      </div>
    </form>
  )
}

export function CouponsAdminPage() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const coupons = useQuery({ queryKey: ['admin', 'coupons'], queryFn: getAdminCoupons })
  const [editing, setEditing] = useState<AdminCoupon | 'new' | null>(null)
  const toggle = useMutation({
    mutationFn: ({ id, on }: { id: number; on: boolean }) => setCouponActive(id, on),
    onSuccess: (c) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'coupons'] })
      toast(c.active ? `${c.code} switched on` : `${c.code} switched off`)
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  if (coupons.isPending) return <PageSpinner />
  if (coupons.isError) return <ErrorState message={errorMessage(coupons.error)} />

  return (
    <div className="space-y-4">
      <PageHeader title="Coupons" actions={!editing && <Button onClick={() => setEditing('new')}>+ New coupon</Button>} />
      {editing && (
        <Card>
          <h2 className="mb-4 font-semibold">{editing === 'new' ? 'New coupon' : `Edit ${editing.code}`}</h2>
          <CouponForm key={editing === 'new' ? 'new' : editing.id} coupon={editing === 'new' ? undefined : editing} onDone={() => setEditing(null)} />
        </Card>
      )}
      <Card className="overflow-hidden p-0">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[860px] text-sm">
            <thead className="bg-slate-50">
              <tr><th className={th}>Code</th><th className={th}>Offer</th><th className={th}>Valid</th><th className={th}>Used</th><th className={th}>State</th><th className={th}>On</th><th className={th} /></tr>
            </thead>
            <tbody>
              {coupons.data.map((c) => (
                <tr key={c.id} className="border-t border-slate-100">
                  <td className={`${td} font-mono font-semibold text-brand-700`}>{c.code}</td>
                  <td className={td}>
                    {c.discountType === 'PERCENT' ? `${c.discountValue}% off${c.maxDiscount ? ` (max ${formatPrice(c.maxDiscount)})` : ''}` : `${formatPrice(c.discountValue)} off`}
                    <span className="block text-xs text-slate-500">{c.minOrderAmount > 0 ? `on ${formatPrice(c.minOrderAmount)}+` : 'no minimum'} · {c.perUserLimit}× per customer</span>
                  </td>
                  <td className={td}>{formatDate(c.validFrom)} – {formatDate(c.validUntil)}</td>
                  <td className={td}>{c.usedCount}{c.usageLimit !== null && ` / ${c.usageLimit}`}</td>
                  <td className={td}><span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${STATE_STYLE[c.state]}`}>{c.state.replace('_', ' ').toLowerCase()}</span></td>
                  <td className={td}><Toggle checked={c.active} label={`Coupon ${c.code} on`} onChange={(on) => toggle.mutate({ id: c.id, on })} /></td>
                  <td className={td}><Button variant="ghost" className="px-2 py-1" onClick={() => setEditing(c)}>Edit</Button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}
