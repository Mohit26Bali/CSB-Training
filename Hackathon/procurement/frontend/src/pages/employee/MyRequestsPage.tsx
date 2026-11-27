import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { requestApi } from '../../api/endpoints'
import type { RequestStatus } from '../../api/types'
import { RequestDetails } from '../../components/RequestDetails'
import { Button, EmptyState, ErrorState, formatMoney, LoadingState, PageHeader, StatusBadge, statusLabel } from '../../components/ui'
import { useDebounced } from '../../hooks'
import { RequestFormModal } from './RequestFormModal'

type Tab = 'ALL' | RequestStatus

const statuses: RequestStatus[] = ['PENDING', 'APPROVED', 'ORDERED', 'DELIVERED', 'REJECTED']
const tabs: Tab[] = ['ALL', ...statuses]

export function MyRequestsPage() {
  const [tab, setTab] = useState<Tab>('ALL')
  const [category, setCategory] = useState('')
  const [search, setSearch] = useState('')
  const [creating, setCreating] = useState(false)
  const debouncedSearch = useDebounced(search)

  const status = tab === 'ALL' ? undefined : tab
  const requests = useQuery({
    queryKey: ['requests', 'mine', status, category, debouncedSearch],
    queryFn: () => requestApi.mine({ status, category: category || undefined, search: debouncedSearch || undefined }),
    placeholderData: (previous) => previous,
  })
  const summary = useQuery({ queryKey: ['requests', 'summary'], queryFn: requestApi.summary })
  const categories = useQuery({ queryKey: ['categories'], queryFn: requestApi.categories, staleTime: Infinity })

  const filtered = tab !== 'ALL' || category !== '' || search !== ''

  return (
    <>
      <PageHeader
        title="My purchase requests"
        subtitle="Request equipment and software, and follow each request to delivery."
        actions={<Button onClick={() => setCreating(true)}>New request</Button>}
      />

      {summary.data && (
        <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {summary.data.map((s) => (
            <div key={s.status} className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
              <StatusBadge status={s.status} />
              <p className="mt-2 text-2xl font-semibold tabular-nums text-slate-900">{s.count}</p>
              <p className="text-xs text-slate-500">{formatMoney(s.amount)}</p>
            </div>
          ))}
        </div>
      )}

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
            placeholder="Search item or justification"
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

      {requests.isPending ? (
        <LoadingState />
      ) : requests.isError ? (
        <ErrorState message={errorMessage(requests.error)} onRetry={() => requests.refetch()} />
      ) : requests.data.length === 0 ? (
        <EmptyState
          title={filtered ? 'No requests match your filters' : 'You haven’t requested anything yet'}
          action={!filtered && <Button onClick={() => setCreating(true)}>New request</Button>}
        />
      ) : (
        <ul className="space-y-3">
          {requests.data.map((r) => (
            <li key={r.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <RequestDetails r={r} />
                </div>
                <StatusBadge status={r.status} />
              </div>
            </li>
          ))}
        </ul>
      )}

      {creating && <RequestFormModal onClose={() => setCreating(false)} />}
    </>
  )
}
