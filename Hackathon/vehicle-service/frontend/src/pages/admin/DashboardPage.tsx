import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import { ErrorState, formatMoney, LoadingState, PageHeader } from '../../components/ui'

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
  const dash = useQuery({ queryKey: ['admin', 'dashboard'], queryFn: adminApi.dashboard })
  if (dash.isPending) return <LoadingState />
  if (dash.isError) return <ErrorState message={errorMessage(dash.error)} onRetry={() => dash.refetch()} />
  const d = dash.data
  const topCount = Math.max(1, ...d.topServices.map((s) => s.count))

  return (
    <>
      <PageHeader title="The workshop today" subtitle="Bays, arrivals and takings at a glance." />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-6">
        <Stat label="Bays in use" value={`${d.baysUsedToday} / ${d.bays}`} hint="today" to="/admin/schedule" />
        <Stat label="Arriving today" value={d.arrivalsToday} hint="still to start" to="/admin/schedule" />
        <Stat label="In service now" value={d.inServiceNow} to="/admin/schedule" />
        <Stat label="Booked ahead" value={d.upcomingBooked} />
        <Stat label="Done this month" value={d.completedThisMonth} />
        <Stat label="Revenue this month" value={formatMoney(d.revenueThisMonth)} />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[2fr_1fr]">
        <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="font-semibold text-slate-900">Next 14 days</h2>
          <p className="mb-4 text-sm text-slate-600">Vehicles booked per day against the {d.bays} bays.</p>
          <ul className="flex h-44 items-end gap-1.5">
            {d.load.map((day) => {
              const [y, m, dd] = day.date.split('-').map(Number)
              const date = new Date(y, m - 1, dd)
              const full = day.free === 0
              return (
                <li key={day.date} className="flex h-full flex-1 flex-col items-center justify-end gap-1" title={`${day.date}: ${day.booked} of ${day.bays} bays`}>
                  <span className="text-[11px] font-semibold text-slate-700">{day.booked}</span>
                  <div className="flex w-full flex-1 items-end rounded bg-slate-100">
                    <div className={`w-full rounded ${full ? 'bg-rose-500' : 'bg-brand-500'}`} style={{ height: `${Math.min(100, (day.booked / day.bays) * 100)}%` }} />
                  </div>
                  <span className="text-[10px] text-slate-500">{new Intl.DateTimeFormat(undefined, { weekday: 'narrow' }).format(date)}</span>
                  <span className="text-[10px] text-slate-400">{dd}</span>
                </li>
              )
            })}
          </ul>
        </section>

        <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-3 font-semibold text-slate-900">Most booked services</h2>
          {d.topServices.length === 0 ? (
            <p className="text-sm text-slate-600">Nothing booked yet.</p>
          ) : (
            <ul className="space-y-3">
              {d.topServices.map((s) => (
                <li key={s.service}>
                  <div className="flex justify-between text-sm">
                    <span className="text-slate-800">{s.service}</span>
                    <span className="font-medium text-slate-900">{s.count}</span>
                  </div>
                  <div className="mt-1 h-2 rounded bg-slate-100">
                    <div className="h-2 rounded bg-brand-500" style={{ width: `${(s.count / topCount) * 100}%` }} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  )
}
