import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi, PAGE_SIZE } from '../../api/endpoints'
import type { Order, OrderStatus } from '../../api/types'
import { useToast } from '../../components/Toast'
import { useDebounced } from '../../hooks'
import { Button, EmptyState, ErrorState, Field, formatDate, formatMoney, LoadingState, Modal, PageHeader, StatusBadge, TextAreaField } from '../../components/ui'

const columns: { status: OrderStatus; title: string; action: string }[] = [
  { status: 'PLACED', title: 'New', action: 'Accept' },
  { status: 'ACCEPTED', title: 'Accepted', action: 'Start preparing' },
  { status: 'PREPARING', title: 'Preparing', action: 'Mark ready' },
  { status: 'READY', title: 'Ready', action: 'Delivered' },
]

const tabs = [
  { key: 'live', label: 'Live board' },
  { key: 'DELIVERED', label: 'Delivered' },
  { key: 'CANCELLED', label: 'Cancelled' },
] as const

function minutesAgo(iso: string): string {
  const m = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000))
  return m < 60 ? `${m} min ago` : `${Math.floor(m / 60)} h ${m % 60} min ago`
}

export function BoardPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<'live' | 'DELIVERED' | 'CANCELLED'>('live')
  const [search, setSearch] = useState('')
  const q = useDebounced(search)
  const [cancelling, setCancelling] = useState<Order | null>(null)
  // The live board asks the server for open orders only (a few dozen at most), so each refresh stays small however many orders were ever placed.
  const live = useQuery({
    queryKey: ['admin', 'orders', 'live', q],
    queryFn: () => adminApi.orders({ scope: 'open', search: q }),
    placeholderData: (previous) => previous,
    refetchInterval: 10_000,
    enabled: tab === 'live',
  })
  // Delivered and cancelled orders are history: a page at a time, on request.
  const history = useInfiniteQuery({
    queryKey: ['admin', 'orders', 'history', tab, q],
    queryFn: ({ pageParam }) => adminApi.orders({ status: tab === 'live' ? undefined : tab, search: q, page: pageParam, size: PAGE_SIZE }),
    initialPageParam: 0,
    getNextPageParam: (last, pages) => (last.length === PAGE_SIZE ? pages.length : undefined),
    placeholderData: (previous) => previous,
    enabled: tab !== 'live',
  })
  const list = tab === 'live' ? live : history
  const orders = (tab === 'live' ? live.data : history.data?.pages.flat()) ?? []

  const advance = useMutation({
    mutationFn: (o: Order) => adminApi.setStatus(o.id, o.nextStatus as OrderStatus),
    onSuccess: (o) => {
      toast.success(`${o.number} is now ${o.status.toLowerCase()}`)
      queryClient.invalidateQueries()
    },
    onError: (err) => {
      toast.error(errorMessage(err))
      queryClient.invalidateQueries()
    },
  })

  return (
    <>
      <PageHeader title="Orders" subtitle="Move each order one step at a time. The board refreshes every few seconds." />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div role="tablist" aria-label="Board or history" className="flex gap-1">
          {tabs.map((t) => (
            <button key={t.key} role="tab" aria-selected={tab === t.key} onClick={() => setTab(t.key)} className={`rounded-lg px-3 py-1.5 text-sm font-medium ${tab === t.key ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
              {t.label}
            </button>
          ))}
        </div>
        <div className="ml-auto w-60">
          <Field label="Search" placeholder="FD-1042, customer, phone…" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : tab === 'live' ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {columns.map((col) => {
            const inColumn = orders.filter((o) => o.status === col.status)
            return (
              <section key={col.status} className="rounded-xl bg-slate-100/70 p-3">
                <h2 className="mb-3 flex items-center justify-between px-1 text-sm font-semibold text-slate-800">
                  {col.title}
                  <span className="rounded-full bg-white px-2 py-0.5 text-xs text-slate-600">{inColumn.length}</span>
                </h2>
                <ul className="space-y-3">
                  {inColumn.length === 0 && <li className="px-1 py-4 text-center text-xs text-slate-400">Nothing here</li>}
                  {inColumn.map((o) => (
                    <li key={o.id} className="rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
                      <div className="flex items-start justify-between gap-2">
                        <p className="font-semibold text-slate-900">{o.number}</p>
                        <span className="text-xs text-slate-500">{minutesAgo(o.createdAt)}</span>
                      </div>
                      <p className="text-sm text-slate-700">{o.customerName}</p>
                      <ul className="my-2 space-y-0.5 text-xs text-slate-700">
                        {o.lines.map((l) => (
                          <li key={l.menuItemId}>
                            <span className="font-semibold">{l.quantity} ×</span> {l.name}
                          </li>
                        ))}
                      </ul>
                      {o.notes && <p className="mb-2 rounded bg-amber-50 px-2 py-1 text-xs text-amber-800">“{o.notes}”</p>}
                      <p className="mb-2 text-xs text-slate-500">
                        {formatMoney(o.total)} · {o.phone}
                      </p>
                      <div className="flex gap-2">
                        <Button small className="flex-1" loading={advance.isPending && advance.variables?.id === o.id} onClick={() => advance.mutate(o)}>
                          {col.action}
                        </Button>
                        {col.status !== 'READY' && (
                          <Button small variant="secondary" onClick={() => setCancelling(o)}>
                            Reject
                          </Button>
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
              </section>
            )
          })}
        </div>
      ) : orders.length === 0 ? (
        <EmptyState title={tab === 'DELIVERED' ? 'Nothing delivered yet' : 'No cancelled orders'} />
      ) : (
        <ul className="space-y-3">
          {orders.map((o) => (
            <li key={o.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-semibold text-slate-900">
                  {o.number} <StatusBadge status={o.status} />
                </p>
                <p className="text-sm font-medium text-slate-900">{formatMoney(o.total)}</p>
              </div>
              <p className="text-sm text-slate-600">
                {o.customerName} · {formatDate(o.createdAt)} · {o.itemCount} item{o.itemCount === 1 ? '' : 's'}
              </p>
              <p className="text-xs text-slate-500">{o.lines.map((l) => `${l.quantity} × ${l.name}`).join(', ')}</p>
              {o.status === 'CANCELLED' && (
                <p className="mt-1 text-sm text-rose-700">
                  Cancelled by {o.cancelledBy === o.customerUsername ? 'the customer' : o.cancelledBy}
                  {o.cancelReason && `: ${o.cancelReason}`}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
      {tab !== 'live' && history.hasNextPage && (
        <div className="mt-4 text-center">
          <Button variant="secondary" loading={history.isFetchingNextPage} onClick={() => history.fetchNextPage()}>
            Show older orders
          </Button>
        </div>
      )}
      {cancelling && <RejectModal order={cancelling} onClose={() => setCancelling(null)} />}
    </>
  )
}

function RejectModal({ order, onClose }: { order: Order; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const reject = useMutation({
    mutationFn: () => adminApi.cancel(order.id, reason),
    onSuccess: (o) => {
      toast.success(`${o.number} is cancelled`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(err instanceof ApiError && err.fieldErrors.reason ? err.fieldErrors.reason : errorMessage(err))
      queryClient.invalidateQueries()
    },
  })

  return (
    <Modal title={`Reject ${order.number}?`} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          {order.customerName} will see your reason. {order.lines.length} dish{order.lines.length === 1 ? '' : 'es'}, {formatMoney(order.total)}.
        </p>
        <TextAreaField label="Reason" rows={2} maxLength={300} placeholder="Out of paneer, closing early…" value={reason} onChange={(e) => setReason(e.target.value)} error={error ?? undefined} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={reject.isPending}>
            Keep order
          </Button>
          <Button variant="danger" loading={reject.isPending} onClick={() => reject.mutate()}>
            Reject order
          </Button>
        </div>
      </div>
    </Modal>
  )
}
