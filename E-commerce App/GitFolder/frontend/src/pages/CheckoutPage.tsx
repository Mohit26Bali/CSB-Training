import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { getAddresses, getCoupons, placeOrder, previewCheckout } from '../api/endpoints'
import type { PaymentMethod } from '../api/types'
import { AddressForm, AddressText } from '../components/AddressForm'
import { useToast } from '../components/Toast'
import { Button, Card, ErrorState, Field, FormError, PageSpinner } from '../components/ui'
import { errorMessage } from '../hooks'
import { formatPrice, variantText } from '../lib/format'
import { PriceDetails } from './CartPage'

function Step({ number, title, done, children }: { number: number; title: string; done?: ReactNode; children: ReactNode }) {
  return (
    <Card className="p-0">
      <div className="flex items-center gap-3 border-b border-slate-100 px-5 py-3">
        <span className="flex h-6 w-6 items-center justify-center rounded-sm bg-brand-600 text-xs font-bold text-white">{number}</span>
        <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-600">{title}</h2>
        {done && <span className="ml-auto text-sm text-slate-500">{done}</span>}
      </div>
      <div className="p-5">{children}</div>
    </Card>
  )
}

const PAYMENT_OPTIONS: { value: PaymentMethod; title: string; text: string }[] = [
  { value: 'UPI', title: 'UPI', text: 'Pay with any UPI app. Demo: an id ending in @fail is declined.' },
  { value: 'CARD', title: 'Credit / debit card', text: 'Simulated payment: no card details are needed.' },
  { value: 'COD', title: 'Cash on delivery', text: 'Pay when your order arrives (up to ₹50,000).' },
]

export function CheckoutPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [addressId, setAddressId] = useState<number | undefined>()
  const [addingAddress, setAddingAddress] = useState(false)
  const [couponInput, setCouponInput] = useState('')
  const [coupon, setCoupon] = useState<string | undefined>()
  const [payment, setPayment] = useState<PaymentMethod>('UPI')
  const [upiId, setUpiId] = useState('')
  const [error, setError] = useState<string | null>(null)

  const addresses = useQuery({ queryKey: ['addresses'], queryFn: getAddresses })
  const coupons = useQuery({ queryKey: ['coupons'], queryFn: getCoupons })
  const preview = useQuery({
    queryKey: ['checkout', addressId, coupon],
    queryFn: () => previewCheckout({ addressId, couponCode: coupon }),
  })

  const order = useMutation({
    mutationFn: () =>
      placeOrder({
        addressId: preview.data!.address!.id,
        paymentMethod: payment,
        couponCode: preview.data?.couponCode ?? undefined,
        upiId: payment === 'UPI' ? upiId : undefined,
      }),
    onSuccess: (placed) => {
      queryClient.invalidateQueries({ queryKey: ['cart'] })
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      queryClient.invalidateQueries({ queryKey: ['coupons'] })
      navigate(`/orders/${placed.orderNumber}?placed=1`, { replace: true })
    },
    onError: (err) => {
      setError(errorMessage(err))
      toast(errorMessage(err), 'error')
      preview.refetch()
    },
  })

  if (preview.isPending || addresses.isPending) return <PageSpinner />
  if (preview.isError) return <ErrorState message={errorMessage(preview.error)} onRetry={() => preview.refetch()} />
  const p = preview.data
  if (p.items.length === 0 && !order.isPending && !order.isSuccess) return <Navigate to="/cart" replace />

  const applyCoupon = (e: FormEvent) => {
    e.preventDefault()
    setCoupon(couponInput.trim() || undefined)
  }
  const codBlocked = payment === 'COD' && !p.codAvailable
  const upiInvalid = payment === 'UPI' && !/^[a-zA-Z0-9.\-_]{2,64}@[a-zA-Z]{2,32}$/.test(upiId)
  const canPlace = p.checkoutReady && !!p.address && !codBlocked && !upiInvalid

  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_340px]">
      <div className="space-y-4">
        <Step number={1} title="Delivery address" done={p.address && !addingAddress ? `${p.address.city} ${p.address.pincode}` : undefined}>
          {addingAddress || (addresses.data ?? []).length === 0 ? (
            <AddressForm
              onSaved={(a) => {
                setAddressId(a.id)
                setAddingAddress(false)
              }}
              onCancel={(addresses.data ?? []).length ? () => setAddingAddress(false) : undefined}
            />
          ) : (
            <div className="space-y-3">
              {(addresses.data ?? []).map((a) => (
                <label key={a.id} className={`flex cursor-pointer gap-3 rounded-md border p-3 ${p.address?.id === a.id ? 'border-brand-500 bg-brand-50' : 'border-slate-200'}`}>
                  <input type="radio" name="address" className="mt-1 accent-brand-600" checked={p.address?.id === a.id} onChange={() => setAddressId(a.id)} />
                  <div className="flex-1">
                    <span className="mb-1 inline-block rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-semibold text-slate-600">{a.type}</span>
                    <AddressText address={a} />
                  </div>
                </label>
              ))}
              <Button variant="ghost" onClick={() => setAddingAddress(true)}>+ Add a new address</Button>
            </div>
          )}
        </Step>

        <Step number={2} title="Order summary" done={`${p.itemCount} ${p.itemCount === 1 ? 'item' : 'items'}`}>
          <ul className="divide-y divide-slate-100">
            {p.items.map((i) => (
              <li key={i.id} className="flex items-center gap-3 py-3">
                <img src={i.thumbnailUrl} alt="" className="h-14 w-14 rounded bg-slate-50 object-contain p-1" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{i.title}</p>
                  <p className="text-xs text-slate-500">{[variantText(i.variantLabel), `Qty ${i.quantity}`].filter(Boolean).join(' · ')}</p>
                  {i.problem && <p className="text-xs font-medium text-red-600">{i.problem}</p>}
                </div>
                <p className="text-sm font-semibold">{formatPrice(i.lineTotal)}</p>
              </li>
            ))}
          </ul>
          {!p.checkoutReady && p.address && (
            <p className="mt-2 text-sm text-red-600">Some items need attention. <Link to="/cart" className="font-semibold underline">Go to cart</Link></p>
          )}
        </Step>

        <Step number={3} title="Offers">
          <form onSubmit={applyCoupon} className="flex gap-2">
            <label htmlFor="coupon" className="sr-only">Coupon code</label>
            <input id="coupon" value={couponInput} onChange={(e) => setCouponInput(e.target.value.toUpperCase())} placeholder="Enter coupon code"
              className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm uppercase" />
            <Button type="submit" variant="outline">Apply</Button>
            {p.couponCode && (
              <Button type="button" variant="ghost" onClick={() => { setCoupon(undefined); setCouponInput('') }}>Remove</Button>
            )}
          </form>
          {p.couponMessage && (
            <p role="status" className={`mt-2 text-sm ${p.couponCode ? 'text-green-700' : 'text-red-600'}`}>{p.couponMessage}</p>
          )}
          {(coupons.data ?? []).length > 0 && (
            <div className="mt-4 grid gap-2 sm:grid-cols-2">
              {coupons.data!.map((c) => (
                <button key={c.code} type="button" onClick={() => { setCouponInput(c.code); setCoupon(c.code) }}
                  className={`rounded-md border border-dashed p-3 text-left ${p.couponCode === c.code ? 'border-green-600 bg-green-50' : 'border-slate-300 hover:border-brand-500'}`}>
                  <span className="font-mono text-sm font-bold text-brand-700">{c.code}</span>
                  <span className="mt-0.5 block text-xs text-slate-600">{c.description}</span>
                </button>
              ))}
            </div>
          )}
        </Step>

        <Step number={4} title="Payment">
          <div className="space-y-3">
            {PAYMENT_OPTIONS.map((o) => {
              const disabled = o.value === 'COD' && !p.codAvailable
              return (
                <label key={o.value} className={`flex gap-3 rounded-md border p-3 ${disabled ? 'opacity-50' : 'cursor-pointer'} ${payment === o.value ? 'border-brand-500 bg-brand-50' : 'border-slate-200'}`}>
                  <input type="radio" name="payment" className="mt-1 accent-brand-600" disabled={disabled} checked={payment === o.value} onChange={() => setPayment(o.value)} />
                  <div className="flex-1">
                    <p className="text-sm font-semibold text-slate-900">{o.title}</p>
                    <p className="text-xs text-slate-500">{disabled ? 'Not available above ₹50,000' : o.text}</p>
                    {o.value === 'UPI' && payment === 'UPI' && (
                      <Field label="UPI id" name="upiId" className="mt-3 max-w-xs" placeholder="name@okbank" value={upiId} onChange={(e) => setUpiId(e.target.value.trim())}
                        error={upiId && upiInvalid ? 'Enter a valid UPI id, e.g. name@okbank' : undefined} />
                    )}
                  </div>
                </label>
              )
            })}
          </div>
          <div className="mt-5 space-y-3">
            <FormError message={error} />
            <Button variant="accent" className="w-full py-3 sm:w-auto sm:px-12" disabled={!canPlace} busy={order.isPending}
              onClick={() => {
                setError(null)
                order.mutate()
              }}>
              {payment === 'COD' ? 'Confirm order' : `Pay ${formatPrice(p.grandTotal)}`}
            </Button>
          </div>
        </Step>
      </div>

      <div>
        <PriceDetails
          rows={[
            [`Price (${p.itemCount} ${p.itemCount === 1 ? 'item' : 'items'})`, p.totalMrp],
            ['Discount', -p.productDiscount],
            ...(p.couponDiscount > 0 ? [[`Coupon ${p.couponCode}`, -p.couponDiscount] as [string, number]] : []),
            ['Delivery charges', p.deliveryFee, p.deliveryFee === 0 ? 'Free' : undefined],
          ]}
          total={p.grandTotal}
          saving={p.productDiscount + p.couponDiscount}
        />
      </div>
    </div>
  )
}
