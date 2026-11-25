import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { adminCancelOrder, getAdminOrder, listOrders, moveOrder } from '../api/admin'
import type { OrderStatus } from '../api/types'
import { AddressText } from '../components/AddressForm'
import { useToast } from '../components/Toast'
import { Button, Card, ErrorState, PageSpinner } from '../components/ui'
import { errorMessage } from '../hooks'
import { formatDateTime, formatPrice, variantText } from '../lib/format'
import { OrderTotals, StatusBadge, Tracker } from '../pages/OrderPages'
import { PageHeader, Pager, td, th } from './AdminLayout'

const TABS: (OrderStatus | '')[] = ['', 'PLACED', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED']
const NEXT: Partial<Record<OrderStatus, OrderStatus>> = { PLACED: 'CONFIRMED', CONFIRMED: 'SHIPPED', SHIPPED: 'DELIVERED' }
const NEXT_LABEL: Partial<Record<OrderStatus, string>> = { CONFIRMED: 'Confirm order', SHIPPED: 'Mark as shipped', DELIVERED: 'Mark as delivered' }

export function OrdersAdminPage() {
  const [params, setParams] = useSearchParams()
  const status = params.get('status') ?? ''
  const q = params.get('q') ?? ''
  const page = Number(params.get('page') ?? 0)
  const [search, setSearch] = useState(q)
  const orders = useQuery({
    queryKey: ['admin', 'orders', status, q, page],
    queryFn: () => listOrders({ status, q, page }),
    placeholderData: keepPreviousData,
  })
  const set = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)))
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }
  const submit = (e: FormEvent) => {
    e.preventDefault()
    set({ q: search.trim() })
  }

  return (
    <div>
      <PageHeader title="Orders">
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <div className="flex flex-wrap gap-1 rounded-lg bg-white p-1 shadow-sm" role="tablist" aria-label="Filter by status">
            {TABS.map((t) => (
              <button key={t || 'ALL'} role="tab" aria-selected={status === t} onClick={() => set({ status: t })}
                className={`rounded-md px-3 py-1.5 text-sm font-medium ${status === t ? 'bg-brand-600 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
                {t ? t[0] + t.slice(1).toLowerCase() : 'All'}
              </button>
            ))}
          </div>
          <form onSubmit={submit} className="flex flex-1 gap-2 sm:max-w-sm">
            <label htmlFor="order-search" className="sr-only">Search orders</label>
            <input id="order-search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Order number, customer name or email"
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm" />
            <Button type="submit" variant="outline">Search</Button>
          </form>
        </div>
      </PageHeader>

      {orders.isPending ? <PageSpinner /> : orders.isError ? <ErrorState message={errorMessage(orders.error)} /> : (
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
            <table className={`w-full min-w-[820px] text-sm ${orders.isFetching ? 'opacity-60' : ''}`}>
              <thead className="bg-slate-50">
                <tr><th className={th}>Order</th><th className={th}>Placed</th><th className={th}>Customer</th><th className={th}>Items</th><th className={th}>Payment</th><th className={th}>Status</th><th className={`${th} text-right`}>Total</th></tr>
              </thead>
              <tbody>
                {orders.data.items.length === 0 && (
                  <tr><td colSpan={7} className="px-4 py-10 text-center text-slate-500">No orders match.</td></tr>
                )}
                {orders.data.items.map((o) => (
                  <tr key={o.orderNumber} className="border-t border-slate-100 hover:bg-slate-50">
                    <td className={td}><Link to={`/admin/orders/${o.orderNumber}`} className="font-medium text-brand-700 hover:underline">{o.orderNumber}</Link></td>
                    <td className={td}>{formatDateTime(o.placedAt)}</td>
                    <td className={td}>{o.customerName}<span className="block text-xs text-slate-500">{o.customerEmail} · {o.city}</span></td>
                    <td className={td}>{o.itemCount}</td>
                    <td className={td}>{o.paymentMethod}<span className="block text-xs text-slate-500">{o.paymentStatus.toLowerCase()}</span></td>
                    <td className={td}><StatusBadge status={o.status} /></td>
                    <td className={`${td} text-right font-medium`}>{formatPrice(o.grandTotal)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager page={orders.data.page} totalPages={orders.data.totalPages} totalItems={orders.data.totalItems} onPage={(p) => set({ page: String(p) })} />
        </Card>
      )}
    </div>
  )
}

export function OrderAdminPage() {
  const { number = '' } = useParams()
  const queryClient = useQueryClient()
  const toast = useToast()
  const [note, setNote] = useState('')
  const [reason, setReason] = useState('')
  const [cancelling, setCancelling] = useState(false)
  const order = useQuery({ queryKey: ['admin', 'order', number], queryFn: () => getAdminOrder(number) })

  const onDone = (message: string) => (updated: Awaited<ReturnType<typeof getAdminOrder>>) => {
    queryClient.setQueryData(['admin', 'order', number], updated)
    queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] })
    setNote('')
    setCancelling(false)
    toast(message)
  }
  const advance = useMutation({
    mutationFn: (status: OrderStatus) => moveOrder(number, status, note),
    onSuccess: (updated) => onDone(`Order is now ${updated.status.toLowerCase()}`)(updated),
    onError: (e) => toast(errorMessage(e), 'error'),
  })
  const cancel = useMutation({
    mutationFn: () => adminCancelOrder(number, reason),
    onSuccess: onDone('Order cancelled'),
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  if (order.isPending) return <PageSpinner />
  if (order.isError) return <ErrorState message={errorMessage(order.error)} />
  const o = order.data
  const next = NEXT[o.status]

  return (
    <div className="space-y-4">
      <nav className="text-xs text-slate-500"><Link to="/admin/orders" className="hover:text-brand-700">Orders</Link> › {o.orderNumber}</nav>
      <Card>
        <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-lg font-semibold">Order {o.orderNumber}</h1>
            <p className="text-xs text-slate-500">Placed {formatDateTime(o.placedAt)} by {o.customerName} ({o.customerEmail})</p>
          </div>
          <StatusBadge status={o.status} />
        </div>
        <Tracker order={o} />

        {(next || o.cancellable) && (
          <div className="mt-6 flex flex-wrap items-end gap-3 border-t border-slate-100 pt-4">
            {next && (
              <>
                <div className="min-w-60 flex-1">
                  <label htmlFor="note" className="mb-1 block text-sm font-medium text-slate-700">Note for the timeline (optional)</label>
                  <input id="note" value={note} maxLength={200} onChange={(e) => setNote(e.target.value)}
                    placeholder={next === 'SHIPPED' ? 'e.g. Blue Dart AWB 1234567' : ''} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
                </div>
                <Button busy={advance.isPending} onClick={() => advance.mutate(next)}>{NEXT_LABEL[next]}</Button>
              </>
            )}
            {o.cancellable && !cancelling && <Button variant="danger" onClick={() => setCancelling(true)}>Cancel order</Button>}
          </div>
        )}
        {cancelling && (
          <div className="mt-3 flex flex-wrap items-end gap-3 rounded-md bg-red-50 p-3">
            <div className="min-w-60 flex-1">
              <label htmlFor="reason" className="mb-1 block text-sm font-medium text-red-800">Reason (shown to the customer)</label>
              <input id="reason" value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Item damaged in the warehouse"
                className="w-full rounded-md border border-red-200 px-3 py-2 text-sm" />
            </div>
            <Button variant="danger" busy={cancel.isPending} onClick={() => cancel.mutate()}>Cancel and refund</Button>
            <Button variant="outline" onClick={() => setCancelling(false)}>Keep order</Button>
          </div>
        )}
      </Card>

      <div className="grid gap-4 lg:grid-cols-[1fr_320px]">
        <Card>
          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wider text-slate-500">Items</h2>
          <ul className="divide-y divide-slate-100">
            {o.items.map((i) => (
              <li key={i.productSlug + i.variantLabel} className="flex items-center gap-3 py-3">
                <img src={i.thumbnailUrl} alt="" className="h-14 w-14 rounded bg-slate-50 object-contain p-1" />
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium">{i.title}</p>
                  <p className="text-xs text-slate-500">{[variantText(i.variantLabel), `Qty ${i.quantity}`, `${formatPrice(i.unitPrice)} each`].filter(Boolean).join(' · ')}</p>
                </div>
                <p className="text-sm font-semibold">{formatPrice(i.lineTotal)}</p>
              </li>
            ))}
          </ul>
          <h2 className="mb-2 mt-6 text-sm font-semibold uppercase tracking-wider text-slate-500">Timeline</h2>
          <ol className="space-y-2 text-sm">
            {o.timeline.map((t, i) => (
              <li key={i} className="flex gap-3">
                <span className="w-36 shrink-0 text-slate-500">{formatDateTime(t.at)}</span>
                <span><StatusBadge status={t.status} /> {t.note && <span className="ml-1 text-slate-700">{t.note}</span>}</span>
              </li>
            ))}
          </ol>
        </Card>
        <div className="space-y-4">
          <Card>
            <h2 className="mb-2 text-sm font-semibold uppercase tracking-wider text-slate-500">Ship to</h2>
            <AddressText address={o.shippingAddress} />
          </Card>
          <Card>
            <h2 className="mb-2 text-sm font-semibold uppercase tracking-wider text-slate-500">Payment</h2>
            <p className="mb-3 text-sm">
              {o.paymentMethod === 'COD' ? 'Cash on delivery' : o.paymentMethod} · <b>{o.paymentStatus.toLowerCase()}</b>
              {o.paymentReference && <span className="block text-xs text-slate-500">Ref {o.paymentReference}</span>}
            </p>
            <OrderTotals order={o} />
          </Card>
        </div>
      </div>
    </div>
  )
}
