import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { managerApi, requestApi } from '../../api/endpoints'
import type { Aging, MaintenanceRequest, PriorityName, RequestStatus } from '../../api/types'
import { AgingChip, PriorityChip } from '../../components/Chips'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, formatDate, LoadingState, Modal, PageHeader, SelectField, StatusBadge, statusLabel, TextAreaField } from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = 'ACTIVE' | 'ALL' | RequestStatus
const tabs: Tab[] = ['ACTIVE', 'OPEN', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'ALL']

export function RequestsPage() {
  const [tab, setTab] = useState<Tab>('ACTIVE')
  const [priority, setPriority] = useState('')
  const [category, setCategory] = useState('')
  const [aging, setAging] = useState('')
  const [search, setSearch] = useState('')
  const [assigning, setAssigning] = useState<MaintenanceRequest | null>(null)
  const [moving, setMoving] = useState<{ request: MaintenanceRequest; to: RequestStatus } | null>(null)
  const debouncedSearch = useDebounced(search)
  const meta = useQuery({ queryKey: ['meta'], queryFn: requestApi.meta })

  const list = useQuery({
    queryKey: ['manager', 'requests', tab, priority, category, aging, debouncedSearch],
    queryFn: () =>
      managerApi.queue({
        status: tab === 'ACTIVE' || tab === 'ALL' ? undefined : tab,
        priority: (priority || undefined) as PriorityName | undefined,
        category: category || undefined,
        aging: (aging || undefined) as Aging | undefined,
        search: debouncedSearch || undefined,
      }),
    placeholderData: (previous) => previous,
  })
  const rows = (list.data ?? []).filter((r) => tab !== 'ACTIVE' || r.aging !== null)
  const filtered = priority !== '' || category !== '' || aging !== '' || search !== ''

  return (
    <>
      <PageHeader title="Requests" subtitle="Emergencies first, then the longest waiting." />
      <div role="tablist" aria-label="Filter by status" className="mb-3 flex gap-1 overflow-x-auto">
        {tabs.map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${tab === t ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
          >
            {t === 'ALL' ? 'Everything' : t === 'ACTIVE' ? 'Needs attention' : statusLabel(t)}
          </button>
        ))}
      </div>
      <div className="mb-5 flex flex-wrap gap-3">
        <input type="search" aria-label="Search requests" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search place, problem, MR-3042 or resident" className="min-w-56 flex-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" />
        <select aria-label="Filter by category" value={category} onChange={(e) => setCategory(e.target.value)} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
          <option value="">Any category</option>
          {(meta.data?.categories ?? []).map((c) => (
            <option key={c} value={c}>
              {statusLabel(c)}
            </option>
          ))}
        </select>
        <select aria-label="Filter by priority" value={priority} onChange={(e) => setPriority(e.target.value)} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
          <option value="">Any priority</option>
          {(meta.data?.priorities ?? []).map((p) => (
            <option key={p} value={p}>
              {statusLabel(p)}
            </option>
          ))}
        </select>
        <select aria-label="Filter by age" value={aging} onChange={(e) => setAging(e.target.value)} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
          <option value="">Any age</option>
          <option value="FRESH">Fresh</option>
          <option value="AGING">Ageing</option>
          <option value="STALE">Stale</option>
        </select>
        {filtered && (
          <Button
            variant="ghost"
            onClick={() => {
              setPriority('')
              setCategory('')
              setAging('')
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
        <EmptyState title={filtered ? 'No requests match your filters' : 'Nothing here. All caught up.'} />
      ) : (
        <ul className="space-y-3">
          {rows.map((r) => (
            <li key={r.id} className={`rounded-xl border bg-white p-4 shadow-sm ${r.aging === 'STALE' ? 'border-rose-300' : r.aging === 'AGING' ? 'border-amber-300' : 'border-slate-200'}`}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-xs font-semibold text-slate-500">{r.number}</span>
                    <StatusBadge status={r.status} />
                    <PriorityChip priority={r.priority} />
                    <AgingChip aging={r.aging} days={r.ageDays} />
                  </div>
                  <h2 className="mt-1 font-semibold text-slate-900">
                    {statusLabel(r.category)} · {r.location}
                  </h2>
                  <p className="text-sm text-slate-700">{r.description}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    {r.residentName} · reported {formatDate(r.createdAt)}
                    {r.technicianName && ` · with ${r.technicianName}`}
                  </p>
                  {r.resolutionNotes && <p className="mt-2 rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-900">{r.resolutionNotes}</p>}
                </div>
                <div className="flex flex-wrap gap-2">
                  {(r.status === 'OPEN' || r.status === 'ASSIGNED' || r.status === 'IN_PROGRESS') && (
                    <Button small variant={r.status === 'OPEN' ? 'primary' : 'secondary'} onClick={() => setAssigning(r)}>
                      {r.technicianId ? 'Reassign' : 'Assign'}
                    </Button>
                  )}
                  {r.allowedNext
                    .filter((s) => s !== 'ASSIGNED')
                    .map((s) => (
                      <Button key={s} small variant={s === 'IN_PROGRESS' && r.status === 'RESOLVED' ? 'secondary' : 'primary'} onClick={() => setMoving({ request: r, to: s })}>
                        {r.status === 'RESOLVED' && s === 'IN_PROGRESS' ? 'Redo work' : s === 'IN_PROGRESS' ? 'Start work' : s === 'RESOLVED' ? 'Resolve' : 'Close'}
                      </Button>
                    ))}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {assigning && <AssignModal request={assigning} onClose={() => setAssigning(null)} />}
      {moving && <StatusModal request={moving.request} to={moving.to} onClose={() => setMoving(null)} />}
    </>
  )
}

function AssignModal({ request, onClose }: { request: MaintenanceRequest; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const technicians = useQuery({ queryKey: ['manager', 'technicians'], queryFn: managerApi.technicians })
  // technicians of the right trade first, then the least busy
  const available = (technicians.data ?? [])
    .filter((t) => t.active)
    .sort((a, b) => Number(b.trade === request.category) - Number(a.trade === request.category) || a.activeJobs - b.activeJobs)
  const [technicianId, setTechnicianId] = useState(request.technicianId ? String(request.technicianId) : '')
  const [error, setError] = useState<string | null>(null)

  const assign = useMutation({
    mutationFn: () => managerApi.assign(request.id, Number(technicianId)),
    onSuccess: (r) => {
      toast.success(`${r.number} assigned to ${r.technicianName}`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(err instanceof ApiError ? (err.fieldErrors.technicianId ?? err.message) : errorMessage(err))
      queryClient.invalidateQueries()
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    assign.mutate()
  }

  return (
    <Modal title={`${request.number}: assign a technician`} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="text-sm text-slate-600">
          {statusLabel(request.category)} at {request.location}. Matching trades are listed first.
        </p>
        <SelectField label="Technician" value={technicianId} onChange={(e) => setTechnicianId(e.target.value)} error={error ?? undefined}>
          <option value="">{technicians.isPending ? 'Loading…' : 'Choose…'}</option>
          {available.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name} · {statusLabel(t.trade).toLowerCase()} · {t.activeJobs} active job{t.activeJobs === 1 ? '' : 's'}
            </option>
          ))}
        </SelectField>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={assign.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={assign.isPending} disabled={!technicianId}>
            Assign
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function StatusModal({ request, to, onClose }: { request: MaintenanceRequest; to: RequestStatus; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)
  const resolving = to === 'RESOLVED'

  const move = useMutation({
    mutationFn: () => managerApi.setStatus(request.id, to, notes),
    onSuccess: (r) => {
      toast.success(`${r.number} is now ${statusLabel(r.status).toLowerCase()}`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(err instanceof ApiError ? (err.fieldErrors.notes ?? err.message) : errorMessage(err))
      queryClient.invalidateQueries() // someone else may have just changed it
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    move.mutate()
  }

  return (
    <Modal title={`${request.number}: ${statusLabel(to).toLowerCase()}`} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {to === 'CLOSED' && <p className="text-sm text-slate-600">Closed requests cannot be changed.</p>}
        {resolving || to === 'IN_PROGRESS' ? (
          <TextAreaField label={resolving ? 'What was done? (the resident will read this)' : 'Remark (optional)'} rows={4} maxLength={1000} value={notes} onChange={(e) => setNotes(e.target.value)} error={error ?? undefined} />
        ) : (
          error && (
            <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {error}
            </p>
          )
        )}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={move.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={move.isPending}>
            Move to {statusLabel(to).toLowerCase()}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
