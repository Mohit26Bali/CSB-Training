import type { Order } from '../api/types'
import { formatDate } from './ui'

const steps = [
  { key: 'PLACED', label: 'Placed', at: (o: Order) => o.createdAt },
  { key: 'ACCEPTED', label: 'Accepted', at: (o: Order) => o.acceptedAt },
  { key: 'PREPARING', label: 'Preparing', at: (o: Order) => o.preparingAt },
  { key: 'READY', label: 'Ready', at: (o: Order) => o.readyAt },
  { key: 'DELIVERED', label: 'Delivered', at: (o: Order) => o.deliveredAt },
] as const

/** Five dots from placed to delivered, filled up to the current step, with the time each step happened. */
export function OrderProgress({ order }: { order: Order }) {
  if (order.status === 'CANCELLED') {
    return (
      <p className="text-sm font-medium text-rose-700">
        Cancelled by {order.cancelledBy === order.customerUsername ? 'you' : 'the restaurant'}
        {order.cancelReason && `: ${order.cancelReason}`}
      </p>
    )
  }
  const current = steps.findIndex((s) => s.key === order.status)
  return (
    <ol className="grid grid-cols-5 gap-1" aria-label="Order progress">
      {steps.map((s, i) => {
        const done = i <= current
        const at = s.at(order)
        return (
          <li key={s.key} className="min-w-0 text-center">
            <div className="relative flex items-center justify-center">
              {i > 0 && <span className={`absolute right-1/2 top-1/2 h-0.5 w-full -translate-y-1/2 ${done ? 'bg-brand-500' : 'bg-slate-200'}`} />}
              <span className={`relative grid size-5 place-items-center rounded-full text-[10px] font-bold ${done ? 'bg-brand-600 text-white' : 'bg-slate-200 text-slate-500'} ${i === current && order.status !== 'DELIVERED' ? 'ring-4 ring-brand-200' : ''}`}>
                {done ? '✓' : i + 1}
              </span>
            </div>
            <p className={`mt-1 text-[11px] ${i === current ? 'font-semibold text-slate-900' : 'text-slate-500'}`}>{s.label}</p>
            {at && done && <p className="truncate text-[10px] text-slate-400">{formatDate(at).split(',').pop()?.trim()}</p>}
          </li>
        )
      })}
    </ol>
  )
}
