import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { cancelOrder, getOrder, getOrders } from '../api/endpoints'
import type { OrderDetail, OrderStatus } from '../api/types'
import { AddressText } from '../components/AddressForm'
import { useToast } from '../components/Toast'
import { Button, Card, EmptyState, ErrorState, PageSpinner } from '../components/ui'
import { errorMessage } from '../hooks'
import { formatDate, formatDateTime, formatPrice, variantText } from '../lib/format'

const STATUS_STYLE: Record<OrderStatus, string> = {
  PLACED: 'bg-blue-100 text-blue-800',
  CONFIRMED: 'bg-indigo-100 text-indigo-800',
  SHIPPED: 'bg-amber-100 text-amber-800',
  DELIVERED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-700',
}

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${STATUS_STYLE[status]}`}>{status[0] + status.slice(1).toLowerCase()}</span>
}

export function OrdersPage() {
  const [page, setPage] = useState(0)
  const orders = useQuery({ queryKey: ['orders', page], queryFn: () => getOrders(page) })
  if (orders.isPending) return <PageSpinner />
  if (orders.isError) return <ErrorState message={errorMessage(orders.error)} onRetry={() => orders.refetch()} />
  if (orders.data.totalItems === 0) {
    return <EmptyState title="No orders yet" text="When you place an order it will show up here." action={<Link to="/"><Button>Start shopping</Button></Link>} />
  }
  return (
    <div className="space-y-3">
      <h1 className="text-lg font-semibold text-slate-900">My orders</h1>
      {orders.data.items.map((o) => (
        <Link key={o.orderNumber} to={`/orders/${o.orderNumber}`}
          className="flex flex-wrap items-center gap-4 rounded-lg bg-white p-4 shadow-sm ring-1 ring-transparent hover:ring-brand-200">
          <div className="flex -space-x-3">
            {o.thumbnails.map((t) => (
              <img key={t} src={t} alt="" className="h-14 w-14 rounded-md border-2 border-white bg-slate-50 object-contain p-1" />
            ))}
          </div>
          <div className="min-w-40 flex-1">
            <p className="text-sm font-semibold text-slate-900">Order {o.orderNumber}</p>
            <p className="text-xs text-slate-500">
              Placed {formatDate(o.placedAt)} · {o.itemCount} {o.itemCount === 1 ? 'item' : 'items'} · {o.paymentMethod}
            </p>
          </div>
          <p className="font-semibold">{formatPrice(o.grandTotal)}</p>
          <StatusBadge status={o.status} />
        </Link>
      ))}
      {orders.data.totalPages > 1 && (
        <div className="flex justify-center gap-2 pt-2">
          <Button variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</Button>
          <Button variant="outline" disabled={page + 1 >= orders.data.totalPages} onClick={() => setPage(page + 1)}>Older</Button>
        </div>
      )}
    </div>
  )
}

const STEPS: OrderStatus[] = ['PLACED', 'CONFIRMED', 'SHIPPED', 'DELIVERED']

/** The Placed → Confirmed → Shipped → Delivered tracker, with the time and note of each step reached. */
export function Tracker({ order }: { order: OrderDetail }) {
  if (order.status === 'CANCELLED') {
    const cancelled = order.timeline.find((t) => t.status === 'CANCELLED')
    return (
      <div className="rounded-md bg-red-50 p-4 text-sm text-red-800">
        <p className="font-semibold">Cancelled {cancelled ? formatDateTime(cancelled.at) : ''}</p>
        {order.cancelReason && <p className="mt-1">Reason: {order.cancelReason}</p>}
      </div>
    )
  }
  const reached = STEPS.indexOf(order.status)
  return (
    <ol className="grid grid-cols-4">
      {STEPS.map((step, i) => {
        const event = order.timeline.find((t) => t.status === step)
        const done = i <= reached
        return (
          <li key={step} className="relative flex flex-col items-center text-center">
            {i > 0 && <span className={`absolute right-1/2 top-3 h-1 w-full ${i <= reached ? 'bg-green-600' : 'bg-slate-200'}`} aria-hidden />}
            <span className={`relative z-10 flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold ${done ? 'bg-green-600 text-white' : 'bg-slate-200 text-slate-500'}`}>
              {done ? '✓' : i + 1}
            </span>
            <span className={`mt-2 text-xs font-semibold ${done ? 'text-slate-900' : 'text-slate-400'}`}>{step[0] + step.slice(1).toLowerCase()}</span>
            {event && <span className="text-[11px] text-slate-500">{formatDateTime(event.at)}</span>}
            {event?.note && step !== 'PLACED' && <span className="mt-0.5 hidden text-[11px] text-slate-500 sm:block">{event.note}</span>}
          </li>
        )
      })}
    </ol>
  )
}

export function OrderTotals({ order }: { order: OrderDetail }) {
  const rows: [string, string][] = [
    ['Total MRP', formatPrice(order.totalMrp)],
    ['Discount', `− ${formatPrice(order.productDiscount)}`],
  ]
  if (order.couponCode) rows.push([`Coupon ${order.couponCode}`, `− ${formatPrice(order.couponDiscount)}`])
  rows.push(['Delivery', order.deliveryFee > 0 ? formatPrice(order.deliveryFee) : 'Free'])
  return (
    <dl className="space-y-2 text-sm">
      {rows.map(([k, v]) => (
        <div key={k} className="flex justify-between"><dt className="text-slate-600">{k}</dt><dd>{v}</dd></div>
      ))}
      <div className="flex justify-between border-t border-slate-100 pt-2 font-semibold"><dt>Total</dt><dd>{formatPrice(order.grandTotal)}</dd></div>
    </dl>
  )
}

export function OrderDetailPage() {
  const { number = '' } = useParams()
  const [params] = useSearchParams()
  const queryClient = useQueryClient()
  const toast = useToast()
  const [confirming, setConfirming] = useState(false)
  const [reason, setReason] = useState('')
  const order = useQuery({ queryKey: ['order', number], queryFn: () => getOrder(number) })
  const cancel = useMutation({
    mutationFn: () => cancelOrder(number, reason),
    onSuccess: (updated) => {
      queryClient.setQueryData(['order', number], updated)
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      setConfirming(false)
      toast('Your order has been cancelled')
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  if (order.isPending) return <PageSpinner />
  if (order.isError) return <ErrorState message={errorMessage(order.error)} />
  const o = order.data

  return (
    <div className="space-y-4">
      {params.get('placed') && (
        <div role="status" className="rounded-lg bg-green-600 p-5 text-white shadow-sm">
          <p className="text-lg font-semibold">Order placed, thank you!</p>
          <p className="text-sm text-green-50">
            Order {o.orderNumber} · {o.paymentStatus === 'PAID' ? `Paid ${formatPrice(o.grandTotal)}` : `Pay ${formatPrice(o.grandTotal)} on delivery`}
          </p>
        </div>
      )}
      <nav className="text-xs text-slate-500"><Link to="/orders" className="hover:text-brand-700">My orders</Link> › {o.orderNumber}</nav>

      <Card>
        <div className="mb-5 flex flex-wrap items-center justify-between gap-2">
          <div>
            <h1 className="text-lg font-semibold">Order {o.orderNumber}</h1>
            <p className="text-xs text-slate-500">Placed on {formatDateTime(o.placedAt)}</p>
          </div>
          <StatusBadge status={o.status} />
        </div>
        <Tracker order={o} />
      </Card>

      <div className="grid gap-4 lg:grid-cols-[1fr_320px]">
        <Card>
          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wider text-slate-500">Items</h2>
          <ul className="divide-y divide-slate-100">
            {o.items.map((i) => (
              <li key={i.productSlug + i.variantLabel} className="flex items-center gap-3 py-3">
                <img src={i.thumbnailUrl} alt="" className="h-16 w-16 rounded bg-slate-50 object-contain p-1" />
                <div className="min-w-0 flex-1">
                  <Link to={`/p/${i.productSlug}`} className="text-sm font-medium hover:text-brand-700">{i.title}</Link>
                  <p className="text-xs text-slate-500">{[variantText(i.variantLabel), `Qty ${i.quantity}`, `${formatPrice(i.unitPrice)} each`].filter(Boolean).join(' · ')}</p>
                  {o.status === 'DELIVERED' && (
                    <Link to={`/p/${i.productSlug}#write-review`} className="mt-1 inline-block text-xs font-semibold text-brand-700 hover:underline">★ Rate & review</Link>
                  )}
                </div>
                <p className="text-sm font-semibold">{formatPrice(i.lineTotal)}</p>
              </li>
            ))}
          </ul>
          {o.cancellable && (
            <div className="mt-4 border-t border-slate-100 pt-4">
              {confirming ? (
                <div className="space-y-3">
                  <label htmlFor="reason" className="block text-sm font-medium">Why are you cancelling? (optional)</label>
                  <input id="reason" value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" placeholder="e.g. Ordered by mistake" />
                  <div className="flex gap-2">
                    <Button variant="danger" busy={cancel.isPending} onClick={() => cancel.mutate()}>Yes, cancel order</Button>
                    <Button variant="outline" onClick={() => setConfirming(false)}>Keep order</Button>
                  </div>
                </div>
              ) : (
                <Button variant="danger" onClick={() => setConfirming(true)}>Cancel order</Button>
              )}
            </div>
          )}
        </Card>
        <div className="space-y-4">
          <Card>
            <h2 className="mb-2 text-sm font-semibold uppercase tracking-wider text-slate-500">Delivery address</h2>
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
