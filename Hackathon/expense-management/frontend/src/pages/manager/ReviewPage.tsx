import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { expenseApi, managerApi } from '../../api/endpoints'
import type { Expense, ExpenseStatus } from '../../api/types'
import { useToast } from '../../components/Toast'
import {
  Button,
  EmptyState,
  ErrorState,
  formatDate,
  formatDay,
  formatMoney,
  LoadingState,
  Modal,
  PageHeader,
  StatusBadge,
  Tag,
  TextAreaField,
} from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = ExpenseStatus | 'ALL'

export function ReviewPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('PENDING')
  const [category, setCategory] = useState('')
  const [search, setSearch] = useState('')
  const [toReject, setToReject] = useState<Expense | null>(null)
  const debouncedSearch = useDebounced(search)

  const status = tab === 'ALL' ? undefined : tab
  const expenses = useQuery({
    queryKey: ['manager', 'expenses', status, category, debouncedSearch],
    queryFn: () => managerApi.expenses({ status, category: category || undefined, search: debouncedSearch || undefined }),
    placeholderData: (previous) => previous,
  })
  const dashboard = useQuery({ queryKey: ['manager', 'dashboard'], queryFn: managerApi.dashboard })
  const categories = useQuery({ queryKey: ['categories'], queryFn: expenseApi.categories, staleTime: Infinity })

  const approve = useMutation({
    mutationFn: (id: number) => managerApi.approve(id),
    onSuccess: (e) => toast.success(`Approved ${formatMoney(e.amount)} for ${e.employeeName}`),
    onError: (err) => toast.error(errorMessage(err)),
    // Refresh even on failure: a conflict usually means someone else already decided it.
    onSettled: () => queryClient.invalidateQueries(),
  })

  const s = dashboard.data?.summary
  const tabs: { value: Tab; label: string; count?: number }[] = [
    { value: 'PENDING', label: 'Pending', count: s?.pendingCount },
    { value: 'APPROVED', label: 'Approved', count: s?.approvedCount },
    { value: 'REJECTED', label: 'Rejected', count: s?.rejectedCount },
    { value: 'ALL', label: 'All' },
  ]
  const filtered = category !== '' || search !== ''

  return (
    <>
      <PageHeader title="Review expenses" subtitle="Approve or reject expenses submitted by your team." />

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
              {t.count !== undefined && <span className="ml-1 opacity-70">({t.count})</span>}
            </button>
          ))}
        </div>
        <div className="ml-auto flex flex-wrap gap-3">
          <label htmlFor="search" className="sr-only">
            Search expenses
          </label>
          <input
            id="search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search employee or description"
            className="w-60 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
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

      {expenses.isPending ? (
        <LoadingState />
      ) : expenses.isError ? (
        <ErrorState message={errorMessage(expenses.error)} onRetry={() => expenses.refetch()} />
      ) : expenses.data.length === 0 ? (
        <EmptyState
          title={filtered ? 'No expenses match your filters' : tab === 'PENDING' ? 'Nothing waiting for review' : 'Nothing to show'}
          hint={tab === 'PENDING' && !filtered ? 'New expenses from your team will appear here.' : undefined}
        />
      ) : (
        <ul className="space-y-3">
          {expenses.data.map((e) => (
            <li key={e.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <p className="font-medium text-slate-900">
                    {e.employeeName} <span className="font-normal text-slate-500">· {e.employeeUsername}</span>
                  </p>
                  <p className="mt-0.5 text-sm text-slate-700">{e.description}</p>
                  <div className="mt-1.5 flex flex-wrap items-center gap-2 text-xs text-slate-500">
                    <Tag tone="green">{e.categoryLabel}</Tag>
                    <span>Spent on {formatDay(e.expenseDate)}</span>
                    <span aria-hidden="true">·</span>
                    <span>Submitted {formatDate(e.submittedAt)}</span>
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <p className="text-lg font-semibold tabular-nums text-slate-900">{formatMoney(e.amount)}</p>
                  <StatusBadge status={e.status} />
                  {e.status === 'PENDING' && (
                    <>
                      <Button
                        small
                        loading={approve.isPending && approve.variables === e.id}
                        disabled={approve.isPending}
                        onClick={() => approve.mutate(e.id)}
                      >
                        Approve
                      </Button>
                      <Button variant="secondary" small disabled={approve.isPending} onClick={() => setToReject(e)}>
                        Reject
                      </Button>
                    </>
                  )}
                </div>
              </div>
              {e.status !== 'PENDING' && (
                <p className="mt-3 text-xs text-slate-500">
                  {e.status === 'APPROVED' ? 'Approved' : 'Rejected'} by {e.decidedBy} on {formatDate(e.decidedAt)}
                  {e.rejectionReason && (
                    <span className="mt-2 block rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
                      {e.rejectionReason}
                    </span>
                  )}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}

      {toReject && <RejectModal expense={toReject} onClose={() => setToReject(null)} />}
    </>
  )
}

function RejectModal({ expense, onClose }: { expense: Expense; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const reject = useMutation({
    mutationFn: () => managerApi.reject(expense.id, reason),
    onSuccess: () => {
      toast.success(`Rejected ${expense.employeeName}’s expense`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(err instanceof ApiError ? (err.fieldErrors.reason ?? err.message) : errorMessage(err))
      // A conflict means it was already decided elsewhere, so refresh the list behind this dialog.
      if (err instanceof ApiError && err.status === 409) queryClient.invalidateQueries()
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    reject.mutate()
  }

  return (
    <Modal title="Reject this expense?" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="text-sm text-slate-600">
          {expense.employeeName} claimed <strong>{formatMoney(expense.amount)}</strong> for “{expense.description}”.
          The employee will see your reason.
        </p>
        <TextAreaField
          label="Reason"
          maxLength={255}
          placeholder="For example: receipt is missing"
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
            Reject expense
          </Button>
        </div>
      </form>
    </Modal>
  )
}
