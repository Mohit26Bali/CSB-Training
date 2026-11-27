import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { leaveApi, managerApi } from '../../api/endpoints'
import type { Leave, LeaveStatus } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import {
  Button,
  EmptyState,
  ErrorState,
  formatDate,
  LoadingState,
  Modal,
  PageHeader,
  StatusBadge,
  Tag,
  TextAreaField,
} from '../../components/ui'
import { formatRange } from '../../dates'
import { useDebounced } from '../../hooks'

type Tab = LeaveStatus | 'ALL'

const tabs: { value: Tab; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'ALL', label: 'All' },
]

export function ReviewPage() {
  const { user } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('PENDING')
  const [type, setType] = useState('')
  const [search, setSearch] = useState('')
  const [toReject, setToReject] = useState<Leave | null>(null)
  const debouncedSearch = useDebounced(search)

  const status = tab === 'ALL' ? undefined : tab
  const list = useQuery({
    queryKey: ['manager', 'leave', status, type, debouncedSearch],
    queryFn: () => managerApi.list({ status, type: type || undefined, search: debouncedSearch || undefined }),
    placeholderData: (previous) => previous,
  })
  const types = useQuery({ queryKey: ['leave', 'types'], queryFn: leaveApi.types, staleTime: Infinity })

  const approve = useMutation({
    mutationFn: (id: number) => managerApi.approve(id),
    onSuccess: (l) => toast.success(`Approved ${l.days} day(s) for ${l.employeeName}`),
    onError: (err) => toast.error(errorMessage(err)),
    // refresh even on failure: a conflict usually means something changed since the list loaded
    onSettled: () => queryClient.invalidateQueries(),
  })

  const filtered = type !== '' || search !== ''

  return (
    <>
      <PageHeader title="Review leave requests" subtitle="Approving a request deducts the days from the employee’s balance." />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex gap-1 overflow-x-auto">
          {tabs.map((t) => (
            <button
              key={t.value}
              role="tab"
              aria-selected={tab === t.value}
              onClick={() => setTab(t.value)}
              className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${
                tab === t.value ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              {t.label}
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
            placeholder="Search employee or reason"
            className="w-56 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          />
          <label htmlFor="type" className="sr-only">
            Filter by leave type
          </label>
          <select
            id="type"
            value={type}
            onChange={(e) => setType(e.target.value)}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          >
            <option value="">All leave types</option>
            {(types.data ?? []).map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
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
          title={filtered ? 'No requests match your filters' : tab === 'PENDING' ? 'No requests waiting' : 'Nothing to show'}
          hint={tab === 'PENDING' && !filtered ? 'New leave requests will appear here.' : undefined}
        />
      ) : (
        <ul className="space-y-3">
          {list.data.map((l) => {
            const own = l.employeeUsername === user?.username
            return (
              <li key={l.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-slate-900">
                      {l.employeeName} <span className="font-normal text-slate-500">· {l.employeeUsername}</span>
                      {own && <span className="ml-2 rounded bg-slate-100 px-1.5 py-0.5 text-xs font-medium text-slate-600">You</span>}
                    </p>
                    <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-slate-700">
                      <span>{formatRange(l.startDate, l.endDate)}</span>
                      <Tag tone="green">{l.leaveTypeLabel}</Tag>
                      <span className="text-xs text-slate-500">
                        {l.days} working day{l.days === 1 ? '' : 's'}
                      </span>
                    </div>
                    <p className="mt-1 text-sm text-slate-600">{l.reason}</p>
                    <p className="mt-1 text-xs text-slate-500">Requested {formatDate(l.submittedAt)}</p>
                  </div>
                  <div className="flex flex-wrap items-center gap-3">
                    <StatusBadge status={l.status} />
                    {l.status === 'PENDING' &&
                      (own ? (
                        <span className="text-xs text-slate-500">Another manager must decide your own request</span>
                      ) : (
                        <>
                          <Button
                            small
                            loading={approve.isPending && approve.variables === l.id}
                            disabled={approve.isPending}
                            onClick={() => approve.mutate(l.id)}
                          >
                            Approve
                          </Button>
                          <Button variant="secondary" small disabled={approve.isPending} onClick={() => setToReject(l)}>
                            Reject
                          </Button>
                        </>
                      ))}
                  </div>
                </div>
                {l.decidedBy && (l.status === 'APPROVED' || l.status === 'REJECTED') && (
                  <p className="mt-3 text-xs text-slate-500">
                    {l.status === 'APPROVED' ? 'Approved' : 'Rejected'} by {l.decidedBy} on {formatDate(l.decidedAt)}
                    {l.decisionNote && (
                      <span className="mt-2 block rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-700">“{l.decisionNote}”</span>
                    )}
                  </p>
                )}
              </li>
            )
          })}
        </ul>
      )}

      {toReject && <RejectModal leave={toReject} onClose={() => setToReject(null)} />}
    </>
  )
}

function RejectModal({ leave, onClose }: { leave: Leave; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)

  const reject = useMutation({
    mutationFn: () => managerApi.reject(leave.id, note),
    onSuccess: () => {
      toast.success(`Rejected ${leave.employeeName}’s request`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(err instanceof ApiError ? (err.fieldErrors.note ?? err.message) : errorMessage(err))
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
          {leave.employeeName} asked for {leave.days} day(s) of {leave.leaveTypeLabel.toLowerCase()} ({formatRange(leave.startDate, leave.endDate)}).
        </p>
        <TextAreaField
          label="Message to the employee (optional)"
          maxLength={255}
          placeholder="For example: please pick dates after the release"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          error={error ?? undefined}
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
