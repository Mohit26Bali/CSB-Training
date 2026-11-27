import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { customerApi } from '../../api/endpoints'
import type { Order } from '../../api/types'
import { OrderProgress } from '../../components/OrderProgress'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, formatDate, formatMoney, LoadingState, Modal, PageHeader, StatusBadge, TextAreaField } from '../../components/ui'

const scopes = [
  { key: 'active', label: 'On its way' },
  { key: 'past', label: 'Past orders' },
] as const

export function OrdersPage() {
  const [scope, setScope] = useState<'active' | 'past'>('active')
  const list = useQuery({
    queryKey: ['orders', 'mine', scope],
    queryFn: () => customerApi.mine(scope),
    placeholderData: (previous) => previous,
    refetchInterval: scope === 'active' ? 15_000 : false,
  })
  const [cancelling, setCancelling] = useState<Order | null>(null)

  return (
    <>
      <PageHeader
        title="My orders"
        subtitle="Active orders refresh by themselves."
        actions={
          <Link to="/menu">
            <Button>Order food</Button>
          </Link>
        }
      />
      <div role="tablist" aria-label="Active or past" className="mb-4 flex gap-1">
        {scopes.map((s) => (
          <button key={s.key} role="tab" aria-selected={scope === s.key} onClick={() => setScope(s.key)} className={`rounded-lg px-3 py-1.5 text-sm font-medium ${scope === s.key ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
            {s.label}
          </button>
        ))}
      </div>
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState
          title={scope === 'active' ? 'No order on its way' : 'No past orders yet'}
          action={<Link to="/menu" className="font-medium text-brand-700 hover:underline">Browse the menu</Link>}
        />
      ) : (
        <ul className="space-y-4">
          {list.data.map((o) => (
            <li key={o.id} className={`rounded-xl border border-slate-200 bg-white p-5 shadow-sm ${o.status === 'CANCELLED' ? 'opacity-80' : ''}`}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-medium text-slate-500">
                    {o.number} · {formatDate(o.createdAt)}
                  </p>
                  <h2 className="mt-0.5 flex items-center gap-2 font-semibold text-slate-900">
                    {formatMoney(o.total)} <StatusBadge status={o.status} />
                  </h2>
                </div>
                {o.canCancel && (
                  <Button variant="secondary" small onClick={() => setCancelling(o)}>
                    Cancel order
                  </Button>
                )}
              </div>
              <div className="my-4">
                <OrderProgress order={o} />
              </div>
              <ul className="divide-y divide-slate-100 text-sm">
                {o.lines.map((l) => (
                  <li key={l.menuItemId} className="flex justify-between py-1.5">
                    <span className="text-slate-800">
                      {l.quantity} × {l.name}
                    </span>
                    <span className="text-slate-600">{formatMoney(l.lineTotal)}</span>
                  </li>
                ))}
              </ul>
              <dl className="mt-2 space-y-0.5 text-xs text-slate-500">
                <div className="flex justify-between">
                  <dt>Tax</dt>
                  <dd>{formatMoney(o.tax)}</dd>
                </div>
                <div className="flex justify-between">
                  <dt>Delivery</dt>
                  <dd>{o.deliveryFee === 0 ? 'Free' : formatMoney(o.deliveryFee)}</dd>
                </div>
              </dl>
              <p className="mt-3 text-xs text-slate-500">To: {o.address}</p>
              {o.notes && <p className="text-xs text-slate-500">“{o.notes}”</p>}
            </li>
          ))}
        </ul>
      )}
      {cancelling && <CancelModal order={cancelling} onClose={() => setCancelling(null)} />}
    </>
  )
}

function CancelModal({ order, onClose }: { order: Order; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const cancel = useMutation({
    mutationFn: () => customerApi.cancel(order.id, reason),
    onSuccess: (o) => {
      toast.success(`${o.number} is cancelled`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(errorMessage(err))
      queryClient.invalidateQueries()
    },
  })

  return (
    <Modal title={`Cancel ${order.number}?`} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">You can cancel until the kitchen accepts the order. {formatMoney(order.total)} will not be charged.</p>
        <TextAreaField label="Reason (optional)" rows={2} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} error={error ?? undefined} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={cancel.isPending}>
            Keep order
          </Button>
          <Button variant="danger" loading={cancel.isPending} onClick={() => cancel.mutate()}>
            Cancel order
          </Button>
        </div>
      </div>
    </Modal>
  )
}
