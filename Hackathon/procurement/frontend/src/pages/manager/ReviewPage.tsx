import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { managerApi, requestApi } from '../../api/endpoints'
import type { Purchase, RequestStatus } from '../../api/types'
import { RequestDetails } from '../../components/RequestDetails'
import { useToast } from '../../components/Toast'
import {
  Button,
  EmptyState,
  ErrorState,
  Field,
  formatMoney,
  LoadingState,
  Modal,
  PageHeader,
  StatusBadge,
  statusLabel,
  TextAreaField,
} from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = RequestStatus | 'ALL'

const tabs: Tab[] = ['PENDING', 'APPROVED', 'ORDERED', 'DELIVERED', 'REJECTED', 'ALL']

export function ReviewPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('PENDING')
  const [category, setCategory] = useState('')
  const [search, setSearch] = useState('')
  const [toReject, setToReject] = useState<Purchase | null>(null)
  const [toOrder, setToOrder] = useState<Purchase | null>(null)
  const debouncedSearch = useDebounced(search)

  const status = tab === 'ALL' ? undefined : tab
  const list = useQuery({
    queryKey: ['manager', 'requests', status, category, debouncedSearch],
    queryFn: () => managerApi.list({ status, category: category || undefined, search: debouncedSearch || undefined }),
    placeholderData: (previous) => previous,
  })
  const dashboard = useQuery({ queryKey: ['manager', 'dashboard'], queryFn: managerApi.dashboard })
  const categories = useQuery({ queryKey: ['categories'], queryFn: requestApi.categories, staleTime: Infinity })

  const counts = new Map((dashboard.data?.byStatus ?? []).map((s) => [s.status, s.count]))

  const act = useMutation({
    mutationFn: async ({ kind, request }: { kind: 'approve' | 'deliver'; request: Purchase }) =>
      kind === 'approve' ? managerApi.approve(request.id) : managerApi.deliver(request.id),
    onSuccess: (r) => toast.success(r.status === 'APPROVED' ? `Approved ${r.itemName}` : `${r.itemName} marked as delivered`),
    onError: (err) => toast.error(errorMessage(err)), // includes "would exceed this month's budget"
    onSettled: () => queryClient.invalidateQueries(),
  })

  const busyId = act.isPending ? act.variables?.request.id : undefined
  const filtered = category !== '' || search !== ''

  return (
    <>
      <PageHeader
        title="Review purchase requests"
        subtitle={
          dashboard.data
            ? `${formatMoney(dashboard.data.remainingThisMonth)} of this month’s budget is still available.`
            : 'Approve, order and deliver.'
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex gap-1 overflow-x-auto">
          {tabs.map((t) => (
            <button
              key={t}
              role="tab"
              aria-selected={tab === t}
              onClick={() => setTab(t)}
              className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${
                tab === t ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              {t === 'ALL' ? 'All' : statusLabel(t)}
              {t !== 'ALL' && counts.has(t) && <span className="ml-1 opacity-70">({counts.get(t)})</span>}
            </button>
          ))}
        </div>
        <div className="ml-auto flex flex-wrap gap-3">
          <label htmlFor="search" className="sr-only">
            Search requests
          </label>
          <input
            id="search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search item or employee"
            className="w-56 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          />
          <label htmlFor="category" className="sr-only">
            Filter by category
          </label>
          <select
            id="category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          >
            <option value="">All categories</option>
            {(categories.data ?? []).map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState
          title={filtered ? 'No requests match your filters' : tab === 'PENDING' ? 'Nothing waiting for approval' : 'Nothing to show'}
        />
      ) : (
        <ul className="space-y-3">
          {list.data.map((r) => (
            <li key={r.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <p className="mb-1 text-xs text-slate-500">
                    {r.employeeName} · {r.employeeUsername}
                  </p>
                  <RequestDetails r={r} />
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <StatusBadge status={r.status} />
                  {r.status === 'PENDING' && (
                    <>
                      <Button
                        small
                        loading={busyId === r.id && act.variables?.kind === 'approve'}
                        disabled={act.isPending}
                        onClick={() => act.mutate({ kind: 'approve', request: r })}
                      >
                        Approve
                      </Button>
                      <Button variant="secondary" small disabled={act.isPending} onClick={() => setToReject(r)}>
                        Reject
                      </Button>
                    </>
                  )}
                  {r.status === 'APPROVED' && (
                    <Button small disabled={act.isPending} onClick={() => setToOrder(r)}>
                      Mark as ordered
                    </Button>
                  )}
                  {r.status === 'ORDERED' && (
                    <Button
                      small
                      loading={busyId === r.id && act.variables?.kind === 'deliver'}
                      disabled={act.isPending}
                      onClick={() => act.mutate({ kind: 'deliver', request: r })}
                    >
                      Mark as delivered
                    </Button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {toReject && <RejectModal request={toReject} onClose={() => setToReject(null)} />}
      {toOrder && <OrderModal request={toOrder} onClose={() => setToOrder(null)} />}
    </>
  )
}

function RejectModal({ request, onClose }: { request: Purchase; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const reject = useMutation({
    mutationFn: () => managerApi.reject(request.id, reason),
    onSuccess: () => {
      toast.success(`Rejected ${request.itemName}`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(err instanceof ApiError ? (err.fieldErrors.reason ?? err.message) : errorMessage(err))
      if (err instanceof ApiError && err.status === 409) queryClient.invalidateQueries()
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    reject.mutate()
  }

  return (
    <Modal title="Reject this request?" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="text-sm text-slate-600">
          {request.employeeName} asked for {request.quantity} × {request.itemName} ({formatMoney(request.estimatedTotal)}). They will see your reason.
        </p>
        <TextAreaField
          label="Reason"
          maxLength={255}
          placeholder="For example: please get two quotes first"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          error={error ?? undefined}
          hint={`${reason.length}/255`}
        />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={reject.isPending}>
            Cancel
          </Button>
          <Button type="submit" variant="danger" loading={reject.isPending}>
            Reject request
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function OrderModal({ request, onClose }: { request: Purchase; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [reference, setReference] = useState('')
  const [error, setError] = useState<string | null>(null)

  const order = useMutation({
    mutationFn: () => managerApi.order(request.id, reference),
    onSuccess: () => {
      toast.success(`${request.itemName} marked as ordered`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => setError(err instanceof ApiError ? (err.fieldErrors.orderReference ?? err.message) : errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    order.mutate()
  }

  return (
    <Modal title="Mark as ordered" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="text-sm text-slate-600">
          {request.quantity} × {request.itemName} for {request.employeeName}.
        </p>
        <Field
          label="Purchase order reference (optional)"
          maxLength={40}
          placeholder="PO-12345"
          value={reference}
          onChange={(e) => setReference(e.target.value)}
          error={error ?? undefined}
        />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={order.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={order.isPending}>
            Mark as ordered
          </Button>
        </div>
      </form>
    </Modal>
  )
}
