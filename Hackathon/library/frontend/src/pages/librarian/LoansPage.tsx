import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { librarianApi } from '../../api/endpoints'
import type { Loan, LoanStatus } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Button, ConfirmDialog, EmptyState, ErrorState, formatDate, formatDay, formatMoney, LoadingState, PageHeader, StatusBadge, statusLabel } from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = 'ALL' | LoanStatus
const tabs: Tab[] = ['RESERVED', 'ISSUED', 'RETURNED', 'CANCELLED', 'ALL']

export function LoansPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('RESERVED')
  const [search, setSearch] = useState('')
  const [returning, setReturning] = useState<Loan | null>(null)
  const debouncedSearch = useDebounced(search)

  const list = useQuery({
    queryKey: ['librarian', 'loans', tab, debouncedSearch],
    queryFn: () => librarianApi.loans(tab === 'ALL' ? undefined : tab, debouncedSearch || undefined),
    placeholderData: (previous) => previous,
  })

  const issue = useMutation({
    mutationFn: (l: Loan) => librarianApi.issue(l.id),
    onSuccess: (l) => toast.success(`Issued “${l.bookTitle}” to ${l.memberName}, due ${formatDay(l.dueDate!)}`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(),
  })
  const giveBack = useMutation({
    mutationFn: (l: Loan) => librarianApi.giveBack(l.id),
    onSuccess: (l) => toast.success(l.fine > 0 ? `Returned. Collect a fine of ${formatMoney(l.fine)}` : `“${l.bookTitle}” is back on the shelf`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => {
      setReturning(null)
      queryClient.invalidateQueries()
    },
  })
  const busyId = issue.isPending ? issue.variables?.id : undefined

  return (
    <>
      <PageHeader title="Reservations and loans" subtitle="Hand over reserved books, take returns and collect fines." />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex gap-1 overflow-x-auto">
          {tabs.map((t) => (
            <button
              key={t}
              role="tab"
              aria-selected={tab === t}
              onClick={() => setTab(t)}
              className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${tab === t ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
            >
              {t === 'ALL' ? 'All' : statusLabel(t)}
            </button>
          ))}
        </div>
        <div className="ml-auto">
          <label htmlFor="search" className="sr-only">
            Search by member or book
          </label>
          <input
            id="search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search member or book"
            className="w-60 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          />
        </div>
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title={search ? 'Nothing matches your search' : tab === 'RESERVED' ? 'No reservations waiting' : 'Nothing to show'} />
      ) : (
        <ul className="space-y-3">
          {list.data.map((l) => (
            <li key={l.id} className={`rounded-xl border bg-white p-4 shadow-sm ${l.overdue ? 'border-rose-300' : 'border-slate-200'}`}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="font-semibold text-slate-900">{l.bookTitle}</h2>
                    <StatusBadge status={l.overdue ? 'OVERDUE' : l.status} />
                  </div>
                  <p className="text-sm text-slate-600">
                    {l.memberName} <span className="text-slate-400">({l.memberUsername})</span>
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    Reserved {formatDate(l.reservedAt)}
                    {l.issuedAt && ` · issued ${formatDate(l.issuedAt)} by ${l.issuedBy}`}
                    {l.dueDate && l.status === 'ISSUED' && ` · due ${formatDay(l.dueDate)}`}
                    {l.returnedAt && ` · returned ${formatDate(l.returnedAt)}`}
                    {l.status === 'RETURNED' && l.fine > 0 && ` · fine ${formatMoney(l.fine)}`}
                  </p>
                  {l.overdue && (
                    <p className="mt-1 text-sm font-medium text-rose-700">
                      {l.daysOverdue} day{l.daysOverdue === 1 ? '' : 's'} late · fine {formatMoney(l.fine)}
                    </p>
                  )}
                </div>
                <div className="flex gap-2">
                  {l.status === 'RESERVED' && (
                    <Button small loading={busyId === l.id} disabled={issue.isPending} onClick={() => issue.mutate(l)}>
                      Issue
                    </Button>
                  )}
                  {l.status === 'ISSUED' && (
                    <Button small variant={l.overdue ? 'danger' : 'primary'} onClick={() => setReturning(l)}>
                      Take return
                    </Button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {returning && (
        <ConfirmDialog
          title="Take this book back?"
          message={`“${returning.bookTitle}” from ${returning.memberName}.${returning.fine > 0 ? ` It is ${returning.daysOverdue} days late: collect a fine of ${formatMoney(returning.fine)}.` : ''}`}
          confirmLabel="Take return"
          loading={giveBack.isPending}
          onConfirm={() => giveBack.mutate(returning)}
          onCancel={() => setReturning(null)}
        />
      )}
    </>
  )
}
