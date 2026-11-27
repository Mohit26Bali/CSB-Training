import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { agentApi } from '../../api/endpoints'
import type { PriorityName, Ticket, TicketStatus } from '../../api/types'
import { PriorityChip, SlaChip } from '../../components/Chips'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, formatDate, LoadingState, PageHeader, StatusBadge, statusLabel } from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = 'ACTIVE' | 'ALL' | TicketStatus
const tabs: Tab[] = ['ACTIVE', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'ALL']
const priorities: PriorityName[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

export function QueuePage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('ACTIVE')
  const [priority, setPriority] = useState('')
  const [assignee, setAssignee] = useState('')
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounced(search)
  const agents = useQuery({ queryKey: ['agents'], queryFn: agentApi.agents })

  const list = useQuery({
    queryKey: ['agent', 'queue', tab, priority, assignee, debouncedSearch],
    queryFn: () =>
      agentApi.queue({
        status: tab === 'ACTIVE' || tab === 'ALL' ? undefined : tab,
        priority: (priority || undefined) as PriorityName | undefined,
        assignee: assignee || undefined,
        search: debouncedSearch || undefined,
      }),
    placeholderData: (previous) => previous,
  })
  const rows = (list.data ?? []).filter((t) => tab !== 'ACTIVE' || t.status === 'OPEN' || t.status === 'IN_PROGRESS')

  const start = useMutation({
    mutationFn: (t: Ticket) => agentApi.setStatus(t.id, 'IN_PROGRESS'),
    onSuccess: (t) => toast.success(`You took ${t.number}`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(),
  })
  const busyId = start.isPending ? start.variables?.id : undefined
  const filtered = priority !== '' || assignee !== '' || search !== ''

  return (
    <>
      <PageHeader title="Ticket queue" subtitle="Most urgent first: the ticket closest to its deadline is at the top." />
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex gap-1 overflow-x-auto">
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
      </div>
      <div className="mb-5 flex flex-wrap gap-3">
        <input
          type="search"
          aria-label="Search tickets"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search title, HD-1042 or requester"
          className="min-w-56 flex-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
        />
        <select aria-label="Filter by priority" value={priority} onChange={(e) => setPriority(e.target.value)} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
          <option value="">Any priority</option>
          {priorities.map((p) => (
            <option key={p} value={p}>
              {statusLabel(p)}
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
        <EmptyState title={filtered ? 'No tickets match your filters' : 'Nothing here. The queue is clear.'} />
      ) : (
        <ul className="space-y-3">
          {rows.map((t) => (
            <li key={t.id} className={`rounded-xl border bg-white p-4 shadow-sm ${t.slaState === 'BREACHED' ? 'border-rose-300' : 'border-slate-200'}`}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-xs font-semibold text-slate-500">{t.number}</span>
                    <StatusBadge status={t.status} />
                    <PriorityChip priority={t.priority} />
                    <SlaChip ticket={t} />
                  </div>
                  <Link to={`/tickets/${t.id}`} className="mt-1 block font-semibold text-slate-900 hover:text-brand-700 hover:underline">
                    {t.title}
                  </Link>
                  <p className="text-sm text-slate-600">
                    {t.requesterName} · {statusLabel(t.category)} · opened {formatDate(t.createdAt)}
                  </p>
                  <p className="text-xs text-slate-500">
                    {t.assigneeName ? `Assigned to ${t.assigneeName}` : 'Unassigned'}
                    {t.commentCount > 0 && ` · ${t.commentCount} message${t.commentCount === 1 ? '' : 's'}`}
                  </p>
                </div>
                <div className="flex gap-2">
                  {t.status === 'OPEN' && (
                    <Button small loading={busyId === t.id} disabled={start.isPending} onClick={() => start.mutate(t)}>
                      Start work
                    </Button>
                  )}
                  <Link to={`/tickets/${t.id}`}>
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
