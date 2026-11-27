import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { providerApi } from '../../api/endpoints'
import type { Appointment } from '../../api/types'
import { DateBlock } from '../../components/DateBlock'
import { formatTime, longDay, parseDay, weekday } from '../../dates'
import { Button, EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge } from '../../components/ui'
import { CancelModal } from './CancelModal'
import { useConfirm } from './useConfirm'

function Stat({ label, value, hint, to }: { label: string; value: string | number; hint?: string; to?: string }) {
  const body = (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition hover:border-brand-300">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-900">{value}</p>
      {hint && <p className="text-xs text-slate-500">{hint}</p>}
    </div>
  )
  return to ? <Link to={to}>{body}</Link> : body
}

export function DashboardPage() {
  const dash = useQuery({ queryKey: ['provider', 'dashboard'], queryFn: providerApi.dashboard })
  const confirm = useConfirm()
  const [cancelling, setCancelling] = useState<Appointment | null>(null)
  if (dash.isPending) return <LoadingState />
  if (dash.isError) return <ErrorState message={errorMessage(dash.error)} onRetry={() => dash.refetch()} />
  const d = dash.data
  const most = Math.max(1, ...d.days.map((x) => x.slots))

  return (
    <>
      <PageHeader title="Your week" subtitle="Requests to answer, what is booked and how full your calendar is." actions={<Link to="/provider/calendar"><Button>Open calendar</Button></Link>} />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-5">
        <Stat label="Today" value={d.todayAppointments} hint="appointments" to="/provider/appointments" />
        <Stat label="To confirm" value={d.pending} hint="pending requests" to="/provider/appointments" />
        <Stat label="Confirmed ahead" value={d.upcomingConfirmed} />
        <Stat label="Open slots" value={d.freeSlotsWeek} hint="this week" to="/provider/calendar" />
        <Stat label="Calendar filled" value={`${d.utilisationPercent}%`} hint={`${d.bookedSlotsWeek} booked this week`} />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_1.4fr]">
        <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="font-semibold text-slate-900">Next 7 days</h2>
          <p className="mb-4 text-sm text-slate-600">Slots offered and how many are booked.</p>
          <ul className="flex h-44 items-end gap-2">
            {d.days.map((day) => (
              <li key={day.date} className="flex h-full flex-1 flex-col items-center justify-end gap-1" title={`${day.date}: ${day.booked} of ${day.slots} booked`}>
                <span className="text-[11px] font-semibold text-slate-700">{day.booked}/{day.slots}</span>
                <div className="relative flex w-full flex-1 items-end rounded bg-slate-100">
                  <div className="w-full rounded bg-brand-200" style={{ height: `${(day.slots / most) * 100}%` }}>
                    <div className="absolute bottom-0 w-full rounded bg-brand-600" style={{ height: `${(day.booked / most) * 100}%` }} />
                  </div>
                </div>
                <span className="text-[10px] text-slate-500">{weekday(day.date)}</span>
                <span className="text-[10px] text-slate-400">{parseDay(day.date).getDate()}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-3 font-semibold text-slate-900">Coming up</h2>
          {d.next.length === 0 ? (
            <EmptyState title="Nothing booked yet" hint="Open more slots to let customers book." />
          ) : (
            <ul className="space-y-3">
              {d.next.map((a) => (
                <li key={a.id} className="flex items-center gap-3">
                  <DateBlock start={a.start} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium text-slate-900">
                      {a.customerName} <StatusBadge status={a.status} />
                    </p>
                    <p className="text-sm text-slate-600">
                      {longDay(a.start)}, {formatTime(a.start)} to {formatTime(a.end)}
                    </p>
                    {a.reason && <p className="truncate text-xs text-slate-500">“{a.reason}”</p>}
                  </div>
                  <div className="flex gap-2">
                    {a.canConfirm && (
                      <Button small loading={confirm.isPending && confirm.variables === a.id} onClick={() => confirm.mutate(a.id)}>
                        Confirm
                      </Button>
                    )}
                    <Button small variant="secondary" onClick={() => setCancelling(a)}>
                      Cancel
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
      {cancelling && <CancelModal appointment={cancelling} onClose={() => setCancelling(null)} />}
    </>
  )
}
