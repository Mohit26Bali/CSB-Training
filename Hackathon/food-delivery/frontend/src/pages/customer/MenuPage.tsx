import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, errorMessage } from '../../api/client'
import { customerApi } from '../../api/endpoints'
import type { CheckoutInput } from '../../api/types'
import { useCart } from '../../cart/cart'
import { DishPhoto, VegMark } from '../../components/DishPhoto'
import { useToast } from '../../components/Toast'
import { useDebounced } from '../../hooks'
import { DELIVERY_FEE, FREE_DELIVERY_FROM, MIN_ORDER, totals } from '../../pricing'
import { Button, EmptyState, ErrorState, Field, formatMoney, LoadingState, Modal, PageHeader, statusLabel, TextAreaField } from '../../components/ui'

export function MenuPage() {
  const [category, setCategory] = useState('')
  const [vegOnly, setVegOnly] = useState(false)
  const [search, setSearch] = useState('')
  const q = useDebounced(search)
  const cart = useCart()
  const categories = useQuery({ queryKey: ['menu', 'categories'], queryFn: customerApi.categories })
  const menu = useQuery({ queryKey: ['menu', category, vegOnly, q], queryFn: () => customerApi.menu(category, vegOnly, q), placeholderData: (previous) => previous })
  const [checkout, setCheckout] = useState(false)

  return (
    <>
      <PageHeader title="Menu" subtitle="Add dishes to your cart. The total includes 5% tax and delivery." />
      <div className="grid gap-6 lg:grid-cols-[1fr_21rem]">
        <div>
          <div className="mb-4 flex flex-wrap items-end gap-3">
            <div className="w-56">
              <Field label="Search" placeholder="Biryani, paneer, chai…" value={search} onChange={(e) => setSearch(e.target.value)} />
            </div>
            <label className="mb-2 flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={vegOnly} onChange={(e) => setVegOnly(e.target.checked)} />
              <VegMark vegetarian /> Vegetarian only
            </label>
          </div>
          <div role="tablist" aria-label="Category" className="mb-4 flex gap-1 overflow-x-auto">
            {['', ...(categories.data ?? [])].map((c) => (
              <button key={c || 'all'} role="tab" aria-selected={category === c} onClick={() => setCategory(c)} className={`whitespace-nowrap rounded-full px-3.5 py-1.5 text-sm font-medium ${category === c ? 'bg-brand-700 text-white' : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50'}`}>
                {c === '' ? 'All' : statusLabel(c).replace(' and ', ' & ')}
              </button>
            ))}
          </div>

          {menu.isPending ? (
            <LoadingState />
          ) : menu.isError ? (
            <ErrorState message={errorMessage(menu.error)} onRetry={() => menu.refetch()} />
          ) : menu.data.length === 0 ? (
            <EmptyState title="No dish matches" hint="Try another word or clear the filters." />
          ) : (
            <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {menu.data.map((m) => {
                const inCart = cart.lines.find((l) => l.id === m.id)
                return (
                  <li key={m.id} className="flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                    <DishPhoto imageKey={m.imageKey} category={m.category} className="h-36 w-full" />
                    <div className="flex flex-1 flex-col gap-1 p-4">
                      <div className="flex items-start justify-between gap-2">
                        <h2 className="flex items-center gap-2 font-semibold text-slate-900">
                          <VegMark vegetarian={m.vegetarian} /> {m.name}
                        </h2>
                        <span className="font-semibold text-slate-900">{formatMoney(m.price)}</span>
                      </div>
                      <p className="flex-1 text-sm text-slate-600">{m.description}</p>
                      <div className="pt-2">
                        {inCart ? (
                          <Stepper quantity={inCart.quantity} onChange={(n) => cart.setQuantity(m.id, n)} />
                        ) : (
                          <Button small className="w-full" onClick={() => cart.add(m)}>
                            Add to cart
                          </Button>
                        )}
                      </div>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </div>

        <aside className="h-fit rounded-xl border border-slate-200 bg-white p-5 shadow-sm lg:sticky lg:top-20">
          <h2 className="mb-3 flex items-center justify-between font-semibold text-slate-900">
            Your cart
            {cart.count > 0 && (
              <button className="text-xs font-medium text-slate-500 hover:text-rose-700" onClick={() => cart.clear()}>
                Empty cart
              </button>
            )}
          </h2>
          {cart.lines.length === 0 ? (
            <p className="text-sm text-slate-600">Nothing yet. Add a dish to start an order.</p>
          ) : (
            <>
              <ul className="space-y-3">
                {cart.lines.map((l) => (
                  <li key={l.id} className="flex items-center gap-3 text-sm">
                    <div className="min-w-0 flex-1">
                      <p className="flex items-center gap-1.5 font-medium text-slate-900">
                        <VegMark vegetarian={l.vegetarian} />
                        <span className="truncate">{l.name}</span>
                      </p>
                      <p className="text-xs text-slate-500">{formatMoney(l.price)} each</p>
                    </div>
                    <Stepper quantity={l.quantity} onChange={(n) => cart.setQuantity(l.id, n)} compact />
                    <span className="w-16 text-right font-medium text-slate-900">{formatMoney(l.price * l.quantity)}</span>
                  </li>
                ))}
              </ul>
              <Summary subtotal={cart.subtotal} />
              <Button className="mt-4 w-full" disabled={cart.subtotal < MIN_ORDER} onClick={() => setCheckout(true)}>
                Checkout
              </Button>
              {cart.subtotal < MIN_ORDER && <p className="mt-2 text-xs text-amber-700">The minimum order is {formatMoney(MIN_ORDER)}. Add {formatMoney(MIN_ORDER - cart.subtotal)} more.</p>}
            </>
          )}
        </aside>
      </div>
      {checkout && <CheckoutModal onClose={() => setCheckout(false)} />}
    </>
  )
}

function Stepper({ quantity, onChange, compact }: { quantity: number; onChange: (n: number) => void; compact?: boolean }) {
  const size = compact ? 'size-7' : 'size-9'
  return (
    <div className="flex items-center justify-center gap-2" role="group" aria-label="Quantity">
      <button type="button" aria-label="Fewer" onClick={() => onChange(quantity - 1)} className={`${size} rounded-lg border border-slate-300 text-lg leading-none hover:bg-slate-50`}>
        −
      </button>
      <span className={`text-center text-sm font-semibold ${compact ? 'w-5' : 'w-8'}`}>{quantity}</span>
      <button type="button" aria-label="More" onClick={() => onChange(quantity + 1)} className={`${size} rounded-lg border border-slate-300 text-lg leading-none hover:bg-slate-50`}>
        +
      </button>
    </div>
  )
}

function Summary({ subtotal }: { subtotal: number }) {
  const t = totals(subtotal)
  return (
    <dl className="mt-4 space-y-1 border-t border-slate-100 pt-3 text-sm">
      <div className="flex justify-between">
        <dt className="text-slate-600">Items</dt>
        <dd>{formatMoney(t.subtotal)}</dd>
      </div>
      <div className="flex justify-between">
        <dt className="text-slate-600">Tax (5%)</dt>
        <dd>{formatMoney(t.tax)}</dd>
      </div>
      <div className="flex justify-between">
        <dt className="text-slate-600">Delivery</dt>
        <dd>{t.deliveryFee === 0 ? 'Free' : formatMoney(t.deliveryFee)}</dd>
      </div>
      {t.deliveryFee > 0 && <p className="text-xs text-emerald-700">Free delivery from {formatMoney(FREE_DELIVERY_FROM)}. Delivery is {formatMoney(DELIVERY_FEE)} below that.</p>}
      <div className="flex justify-between border-t border-slate-100 pt-2 text-base font-semibold text-slate-900">
        <dt>Total</dt>
        <dd>{formatMoney(t.total)}</dd>
      </div>
    </dl>
  )
}

function CheckoutModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const cart = useCart()
  const [form, setForm] = useState<CheckoutInput>({ address: '', phone: '', notes: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const place = useMutation({
    mutationFn: () => customerApi.place(cart.lines.map((l) => ({ menuItemId: l.id, quantity: l.quantity })), form),
    onSuccess: (o) => {
      toast.success(`${o.number} placed: ${formatMoney(o.total)}`)
      cart.clear()
      queryClient.invalidateQueries()
      navigate('/orders')
    },
    onError: (err) => {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(err.fieldErrors)
        const itemProblem = Object.entries(err.fieldErrors).filter(([k]) => k.startsWith('items')).map(([, v]) => v)
        setFormError(itemProblem.length > 0 ? itemProblem.join('. ') : null)
      } else {
        setFieldErrors({})
        setFormError(errorMessage(err))
      }
      queryClient.invalidateQueries({ queryKey: ['menu'] })
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    place.mutate()
  }

  return (
    <Modal title="Checkout" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <TextAreaField label="Delivery address" rows={2} maxLength={200} placeholder="Flat, building, area, city" value={form.address} onChange={(e) => setForm((f) => ({ ...f, address: e.target.value }))} error={fieldErrors.address} />
        <Field label="Mobile number" inputMode="numeric" maxLength={10} placeholder="10 digits" value={form.phone} onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value.replace(/\D/g, '') }))} error={fieldErrors.phone} />
        <Field label="Note for the kitchen (optional)" maxLength={200} placeholder="Less spicy, no onions…" value={form.notes} onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))} error={fieldErrors.notes} />
        <p className="text-sm text-slate-600">
          {cart.count} item{cart.count === 1 ? '' : 's'} · pay on delivery
        </p>
        <Summary subtotal={cart.subtotal} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={place.isPending}>
            Back
          </Button>
          <Button type="submit" loading={place.isPending}>
            Place order
          </Button>
        </div>
      </form>
    </Modal>
  )
}
