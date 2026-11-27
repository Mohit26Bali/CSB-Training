import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { expenseApi } from '../../api/endpoints'
import type { Expense, ExpenseStatus } from '../../api/types'
import {
  Button,
  EmptyState,
  ErrorState,
  formatDate,
  formatDay,
  formatMoney,
  LoadingState,
  PageHeader,
  StatusBadge,
  Tag,
} from '../../components/ui'
import { useDebounced } from '../../hooks'
import { ExpenseFormModal } from './ExpenseFormModal'

type Tab = 'ALL' | ExpenseStatus

const tabs: { value: Tab; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
]

function SummaryCard({ label, count, amount, tone }: { label: string; count: number; amount: number; tone: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-semibold tabular-nums ${tone}`}>{formatMoney(amount)}</p>
      <p className="mt-1 text-xs text-slate-500">
        {count} expense{count === 1 ? '' : 's'}
      </p>
    </div>
  )
}

export function MyExpensesPage() {
  const [tab, setTab] = useState<Tab>('ALL')
  const [category, setCategory] = useState('')
  const [search, setSearch] = useState('')
  const [creating, setCreating] = useState(false)
  const debouncedSearch = useDebounced(search)

  const status = tab === 'ALL' ? undefined : tab
  const expenses = useQuery({
    queryKey: ['expenses', 'mine', status, category, debouncedSearch],
    queryFn: () => expenseApi.mine({ status, category: category || undefined, search: debouncedSearch || undefined }),
    placeholderData: (previous) => previous,
  })
  const summary = useQuery({ queryKey: ['expenses', 'summary'], queryFn: expenseApi.mySummary })
  const categories = useQuery({ queryKey: ['categories'], queryFn: expenseApi.categories, staleTime: Infinity })

  const filtered = tab !== 'ALL' || category !== '' || search !== ''

  return (
    <>
      <PageHeader
        title="My expenses"
        subtitle="Submit business expenses and track their status."
        actions={<Button onClick={() => setCreating(true)}>New expense</Button>}
      />

      {summary.data && (
        <div className="mb-6 grid gap-4 sm:grid-cols-3">
          <SummaryCard label="Pending" count={summary.data.pendingCount} amount={summary.data.pendingAmount} tone="text-amber-600" />
          <SummaryCard label="Approved" count={summary.data.approvedCount} amount={summary.data.approvedAmount} tone="text-emerald-600" />
          <SummaryCard label="Rejected" count={summary.data.rejectedCount} amount={summary.data.rejectedAmount} tone="text-rose-600" />
        </div>
      )}

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
            Search expenses
          </label>
          <input
            id="search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search description"
            className="w-52 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
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
          title={filtered ? 'No expenses match your filters' : 'You haven’t submitted any expenses yet'}
          hint={filtered ? 'Try a different status, category or search.' : 'Submit your first expense to get started.'}
          action={!filtered && <Button onClick={() => setCreating(true)}>New expense</Button>}
        />
      ) : (
        <ul className="space-y-3">
          {expenses.data.map((e) => (
            <ExpenseRow key={e.id} expense={e} />
          ))}
        </ul>
      )}

      {creating && <ExpenseFormModal onClose={() => setCreating(false)} />}
    </>
  )
}

function ExpenseRow({ expense: e }: { expense: Expense }) {
  return (
    <li className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <p className="font-medium text-slate-900">{e.description}</p>
          <div className="mt-1.5 flex flex-wrap items-center gap-2 text-xs text-slate-500">
            <Tag tone="green">{e.categoryLabel}</Tag>
            <span>Spent on {formatDay(e.expenseDate)}</span>
            <span aria-hidden="true">·</span>
            <span>Submitted {formatDate(e.submittedAt)}</span>
          </div>
        </div>
        <div className="flex items-center gap-4">
          <p className="text-lg font-semibold tabular-nums text-slate-900">{formatMoney(e.amount)}</p>
          <StatusBadge status={e.status} />
        </div>
      </div>
      {e.status === 'REJECTED' && e.rejectionReason && (
        <p className="mt-3 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
          <span className="font-medium">Rejected by {e.decidedBy}:</span> {e.rejectionReason}
        </p>
      )}
      {e.status === 'APPROVED' && (
        <p className="mt-3 text-xs text-slate-500">
          Approved by {e.decidedBy} on {formatDate(e.decidedAt)}
        </p>
      )}
    </li>
  )
}
