import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { providerApi } from '../../api/endpoints'
import type { Appointment, AppointmentStatus } from '../../api/types'
import { DateBlock } from '../../components/DateBlock'
import { formatTime, longDay } from '../../dates'
import { useDebounced } from '../../hooks'
import { Button, EmptyState, ErrorState, Field, formatDate, LoadingState, PageHeader, StatusBadge } from '../../components/ui'
import { CancelModal } from './CancelModal'
import { useConfirm } from './useConfirm'

const tabs: { key: AppointmentStatus | ''; label: string }[] = [
  { key: '', label: 'All' },
  { key: 'PENDING', label: 'To confirm' },
  { key: 'CONFIRMED', label: 'Confirmed' },
  { key: 'CANCELLED', label: 'Cancelled' },
]

export function AppointmentsPage() {
  const [status, setStatus] = useState<AppointmentStatus | ''>('')
  const [date, setDate] = useState('')
  const [search, setSearch] = useState('')
  const q = useDebounced(search)
  const [cancelling, setCancelling] = useState<Appointment | null>(null)
  const confirm = useConfirm()
  const list = useQuery({
    queryKey: ['provider', 'appointments', status, date, q],
    queryFn: () => providerApi.appointments(status || undefined, date, q),
    placeholderData: (previous) => previous,
  })

  return (
    <>
      <PageHeader title="Appointments" subtitle="Requests waiting for you come first." />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex flex-wrap gap-1">
          {tabs.map((t) => (
            <button key={t.key || 'all'} role="tab" aria-selected={status === t.key} onClick={() => setStatus(t.key)} className={`rounded-lg px-3 py-1.5 text-sm font-medium ${status === t.key ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
              {t.label}
            </button>
          ))}
        </div>
        <div className="flex flex-1 flex-wrap items-end justify-end gap-3">
          <div className="w-44">
            <Field label="On day" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <div className="w-56">
            <Field label="Search" placeholder="Customer, reason, AP-…" value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
          {(date || search) && (
            <Button
              variant="secondary"
              small
              onClick={() => {
                setDate('')
                setSearch('')
              }}
            >
              Clear
            </Button>
          )}
        </div>
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="No appointments match" hint="Try another status, day or search." />
      ) : (
        <ul className="space-y-3">
          {list.data.map((a) => (
            <li key={a.id} className={`flex gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm ${a.status === 'CANCELLED' ? 'opacity-75' : ''}`}>
              <DateBlock start={a.start} muted={a.status === 'CANCELLED' || a.phase === 'PAST'} />
              <div className="flex min-w-0 flex-1 flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 space-y-1">
                  <p className="text-xs font-medium text-slate-500">
                    {a.number} · requested {formatDate(a.createdAt)}
                  </p>
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="font-semibold text-slate-900">{a.customerName}</h2>
                    <StatusBadge status={a.status} />
                    {a.phase === 'PAST' && a.status !== 'CANCELLED' && <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-semibold text-slate-600">Past</span>}
                  </div>
                  <p className="text-sm text-slate-700">
                    {longDay(a.start)}, {formatTime(a.start)} to {formatTime(a.end)}
                  </p>
                  {a.reason && <p className="text-xs text-slate-500">“{a.reason}”</p>}
                  {a.status === 'CANCELLED' && (
                    <p className="text-sm text-rose-700">
                      Cancelled by {a.cancelledBy === a.customerUsername ? 'the customer' : a.cancelledBy}
                      {a.cancelReason && `: ${a.cancelReason}`}
                    </p>
                  )}
                </div>
                <div className="flex gap-2">
                  {a.canConfirm && (
                    <Button small loading={confirm.isPending && confirm.variables === a.id} onClick={() => confirm.mutate(a.id)}>
                      Confirm
                    </Button>
                  )}
                  {a.status !== 'CANCELLED' && a.phase !== 'PAST' && (
                    <Button small variant="secondary" onClick={() => setCancelling(a)}>
                      Cancel
                    </Button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
      {cancelling && <CancelModal appointment={cancelling} onClose={() => setCancelling(null)} />}
    </>
  )
}
