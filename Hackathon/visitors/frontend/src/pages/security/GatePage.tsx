import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client'
import { securityApi } from '../../api/endpoints'
import type { Visit, VisitStatus } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, formatDate, formatDay, LoadingState, PageHeader, StatusBadge } from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = 'ALL' | VisitStatus
const tabs: Tab[] = ['ALL', 'EXPECTED', 'CHECKED_IN', 'CHECKED_OUT']
const label: Record<Tab, string> = { ALL: 'Everyone', EXPECTED: 'Still expected', CHECKED_IN: 'Inside now', CHECKED_OUT: 'Left', CANCELLED: 'Cancelled' }

function Stat({ title, value, tone }: { title: string; value: number; tone?: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{title}</p>
      <p className={`mt-1 text-3xl font-semibold tabular-nums ${tone ?? 'text-slate-900'}`}>{value}</p>
    </div>
  )
}

export function GatePage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('ALL')
  const [search, setSearch] = useState('')
  const [pass, setPass] = useState('')
  const [date, setDate] = useState('')
  const debouncedSearch = useDebounced(search)
  const stats = useQuery({ queryKey: ['security', 'stats'], queryFn: securityApi.stats })
  const list = useQuery({
    queryKey: ['security', 'list', date, tab, debouncedSearch],
    queryFn: () => securityApi.list(date || undefined, tab === 'ALL' ? undefined : tab, debouncedSearch || undefined),
    placeholderData: (previous) => previous,
    refetchInterval: 30000, // the gate screen stays live
  })

  const checkIn = useMutation({
    mutationFn: (v: Visit) => securityApi.checkIn(v.id),
    onSuccess: (v) => toast.success(`${v.visitorName} is checked in. Host: ${v.hostName}`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(),
  })
  const checkOut = useMutation({
    mutationFn: (v: Visit) => securityApi.checkOut(v.id),
    onSuccess: (v) => toast.success(`${v.visitorName} is checked out`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(),
  })
  const lookup = useMutation({
    mutationFn: (code: string) => securityApi.byPass(code),
    onSuccess: (v) => {
      setSearch(v.passCode)
      setDate('')
      setTab('ALL')
      toast.success(`Found ${v.visitorName}`)
    },
    onError: (err) => toast.error(errorMessage(err)),
  })
  const busy = checkIn.isPending || checkOut.isPending
  const busyId = checkIn.isPending ? checkIn.variables?.id : checkOut.isPending ? checkOut.variables?.id : undefined

  function find(e: FormEvent) {
    e.preventDefault()
    if (pass.trim()) lookup.mutate(pass)
  }

  const s = stats.data
  const showingToday = date === '' && debouncedSearch === ''

  return (
    <>
      <PageHeader title="Gate" subtitle={showingToday ? 'Visitors expected today. The list refreshes itself every half minute.' : 'Search results and other days.'} />

      {s && (
        <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
          <Stat title="Expected today" value={s.expectedToday} />
          <Stat title="Not yet arrived" value={s.notYetArrived} tone="text-amber-600" />
          <Stat title="Inside now" value={s.insideNow} tone="text-emerald-600" />
          <Stat title="Left today" value={s.checkedOutToday} />
        </div>
      )}

      <form onSubmit={find} className="mb-5 flex flex-wrap gap-3 rounded-xl border border-brand-100 bg-brand-50 p-4">
        <label htmlFor="pass" className="sr-only">
          Pass code
        </label>
        <input id="pass" value={pass} onChange={(e) => setPass(e.target.value)} placeholder="Type or scan a pass code, e.g. VP-100001" className="min-w-64 flex-1 rounded-lg border border-slate-300 bg-white px-3 py-2 font-mono text-sm uppercase" />
        <Button type="submit" loading={lookup.isPending}>
          Find visitor
        </Button>
      </form>

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex gap-1 overflow-x-auto">
          {tabs.map((t) => (
            <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)} className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${tab === t ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
              {label[t]}
            </button>
          ))}
        </div>
        <div className="ml-auto flex flex-wrap gap-3">
          <input type="search" aria-label="Search visitors" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Name, phone, company or host" className="w-56 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" />
          <input type="date" aria-label="Show a day" value={date} onChange={(e) => setDate(e.target.value)} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" />
          {(date || search) && (
            <Button
              variant="ghost"
              onClick={() => {
                setDate('')
                setSearch('')
                setPass('')
              }}
            >
              Back to today
            </Button>
          )}
        </div>
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title={showingToday ? 'Nobody is expected today' : 'No visitors found'} />
      ) : (
        <ul className="space-y-3">
          {list.data.map((v) => (
            <li key={v.id} className={`rounded-xl border bg-white p-4 shadow-sm ${v.status === 'CHECKED_IN' ? 'border-emerald-300' : 'border-slate-200'}`}>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-lg font-semibold text-slate-900">{v.visitorName}</h2>
                    <StatusBadge status={v.status} />
                    <span className="rounded bg-slate-100 px-2 py-0.5 font-mono text-xs text-slate-700">{v.passCode}</span>
                    {v.lapsed && <span className="rounded-full bg-rose-100 px-2.5 py-0.5 text-xs font-semibold text-rose-800">Did not come</span>}
                  </div>
                  <p className="text-sm text-slate-600">
                    {v.company ? `${v.company} · ` : ''}
                    {v.phone}
                  </p>
                  <p className="text-sm text-slate-700">
                    {v.purpose} <span className="text-slate-500">· to see {v.hostName}</span>
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    {formatDay(v.expectedDate)}
                    {v.expectedTime && ` at ${v.expectedTime.slice(0, 5)}`}
                    {v.checkedInAt && ` · in ${formatDate(v.checkedInAt)}${v.status === 'CHECKED_IN' ? `, inside ${v.minutesInside} min` : `, out ${formatDate(v.checkedOutAt)}`}`}
                  </p>
                </div>
                <div className="flex gap-2">
                  {v.canCheckIn && (
                    <Button loading={busyId === v.id && checkIn.isPending} disabled={busy} onClick={() => checkIn.mutate(v)}>
                      Check in
                    </Button>
                  )}
                  {v.canCheckOut && (
                    <Button variant="secondary" loading={busyId === v.id && checkOut.isPending} disabled={busy} onClick={() => checkOut.mutate(v)}>
                      Check out
                    </Button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
