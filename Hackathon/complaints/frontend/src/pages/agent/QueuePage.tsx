import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { agentApi, complaintApi } from '../../api/endpoints'
import type { Complaint, ComplaintStatus, PriorityName } from '../../api/types'
import { PriorityChip } from '../../components/Chips'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, formatDate, LoadingState, PageHeader, StatusBadge, statusLabel } from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = 'ACTIVE' | 'ALL' | ComplaintStatus
const tabs: Tab[] = ['ACTIVE', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'ALL']

export function QueuePage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('ACTIVE')
  const [priority, setPriority] = useState('')
  const [category, setCategory] = useState('')
  const [assignee, setAssignee] = useState('')
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounced(search)
  const agents = useQuery({ queryKey: ['agents'], queryFn: agentApi.agents })
  const meta = useQuery({ queryKey: ['meta'], queryFn: complaintApi.meta })

  const list = useQuery({
    queryKey: ['agent', 'queue', tab, priority, category, assignee, debouncedSearch],
    queryFn: () =>
      agentApi.queue({
        status: tab === 'ACTIVE' || tab === 'ALL' ? undefined : tab,
        priority: (priority || undefined) as PriorityName | undefined,
        category: category || undefined,
        assignee: assignee || undefined,
        search: debouncedSearch || undefined,
      }),
    placeholderData: (previous) => previous,
  })
  const rows = (list.data ?? []).filter((c) => tab !== 'ACTIVE' || c.status === 'OPEN' || c.status === 'IN_PROGRESS')

  const start = useMutation({
    mutationFn: (c: Complaint) => agentApi.setStatus(c.id, 'IN_PROGRESS'),
    onSuccess: (c) => toast.success(`You took ${c.number}`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(),
  })
  const busyId = start.isPending ? start.variables?.id : undefined
  const filtered = priority !== '' || category !== '' || assignee !== '' || search !== ''

  return (
    <>
      <PageHeader title="Complaint queue" subtitle="Most urgent first; within a priority, the longest waiting is on top." />
      <div role="tablist" aria-label="Filter by status" className="mb-3 flex gap-1 overflow-x-auto">
        {tabs.map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${tab === t ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
          >
            {t === 'ALL' ? 'Everything' : statusLabel(t)}
          </button>
        ))}
      </div>
      <div className="mb-5 flex flex-wrap gap-3">
        <input
          type="search"
          aria-label="Search complaints"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search subject, CMP-2042 or customer"
          className="min-w-56 flex-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
        />
        <select aria-label="Filter by priority" value={priority} onChange={(e) => setPriority(e.target.value)} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
          <option value="">Any priority</option>
          {(meta.data?.priorities ?? []).map((p) => (
            <option key={p} value={p}>
              {statusLabel(p)}
            </option>
          ))}
        </select>
        <select aria-label="Filter by category" value={category} onChange={(e) => setCategory(e.target.value)} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
          <option value="">Any category</option>
          {(meta.data?.categories ?? []).map((c) => (
            <option key={c} value={c}>
              {statusLabel(c)}
            </option>
          ))}
        </select>
        <select aria-label="Filter by assignee" value={assignee} onChange={(e) => setAssignee(e.target.value)} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
          <option value="">Anyone</option>
          <option value="me">Assigned to me</option>
          <option value="unassigned">Unassigned</option>
          {(agents.data ?? []).map((a) => (
            <option key={a.username} value={a.username}>
              {a.fullname}
            </option>
          ))}
        </select>
        {filtered && (
          <Button
            variant="ghost"
            onClick={() => {
              setPriority('')
              setCategory('')
              setAssignee('')
              setSearch('')
            }}
          >
            Clear filters
          </Button>
        )}
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState title={filtered ? 'No complaints match your filters' : 'Nothing here. The queue is clear.'} />
      ) : (
        <ul className="space-y-3">
          {rows.map((c) => (
            <li key={c.id} className={`rounded-xl border bg-white p-4 shadow-sm ${c.ageDays >= 3 ? 'border-amber-300' : 'border-slate-200'}`}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-xs font-semibold text-slate-500">{c.number}</span>
                    <StatusBadge status={c.status} />
                    <PriorityChip priority={c.priority} />
                    {c.ageDays >= 3 && <span className="text-xs font-semibold text-amber-700">Waiting {c.ageDays} days</span>}
                  </div>
                  <Link to={`/complaints/${c.id}`} className="mt-1 block font-semibold text-slate-900 hover:text-brand-700 hover:underline">
                    {c.subject}
                  </Link>
                  <p className="text-sm text-slate-600">
                    {c.customerName} · {statusLabel(c.category)} · filed {formatDate(c.createdAt)}
                  </p>
                  <p className="text-xs text-slate-500">{c.assigneeName ? `Assigned to ${c.assigneeName}` : 'Unassigned'}</p>
                </div>
                <div className="flex gap-2">
                  {c.status === 'OPEN' && (
                    <Button small loading={busyId === c.id} disabled={start.isPending} onClick={() => start.mutate(c)}>
                      Start work
                    </Button>
                  )}
                  <Link to={`/complaints/${c.id}`}>
                    <Button variant="secondary" small>
                      Open
                    </Button>
                  </Link>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
