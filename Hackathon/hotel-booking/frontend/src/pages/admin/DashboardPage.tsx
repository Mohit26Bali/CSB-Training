import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import { ErrorState, formatDay, formatMoney, LoadingState, PageHeader, statusLabel } from '../../components/ui'

function Stat({ label, value, tone, to, hint }: { label: string; value: string | number; tone?: string; to?: string; hint?: string }) {
  const body = (
    <div className="h-full rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className={`mt-1 text-3xl font-semibold tabular-nums ${tone ?? 'text-slate-900'}`}>{value}</p>
      {hint && <p className="mt-0.5 text-xs text-slate-500">{hint}</p>}
    </div>
  )
  return to ? (
    <Link to={to} className="block rounded-xl hover:ring-2 hover:ring-brand-100">
      {body}
    </Link>
  ) : (
    body
  )
}

export function DashboardPage() {
  const dashboard = useQuery({ queryKey: ['admin', 'dashboard'], queryFn: adminApi.dashboard })
  if (dashboard.isPending) return <LoadingState label="Loading dashboard…" />
  if (dashboard.isError) return <ErrorState message={errorMessage(dashboard.error)} onRetry={() => dashboard.refetch()} />
  const d = dashboard.data

  return (
    <>
      <PageHeader title="Dashboard" subtitle="How the hotel looks tonight." />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Occupancy tonight" value={`${d.occupancyPercent}%`} tone="text-brand-700" hint={`${d.occupiedTonight} of ${d.activeRooms} open rooms`} to="/admin/calendar" />
        <Stat label="Arrivals today" value={d.arrivalsToday} to="/admin/bookings" />
        <Stat label="Departures today" value={d.departuresToday} />
        <Stat label="Waiting for confirmation" value={d.pendingBookings} tone="text-amber-600" to="/admin/bookings" />
      </div>
      <div className="mt-4 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Revenue this month" value={formatMoney(d.revenueThisMonth)} tone="text-emerald-600" hint="confirmed stays checking in this month" />
      </div>

      <div className="mt-10 grid gap-8 lg:grid-cols-2">
        <section>
          <h2 className="mb-3 text-lg font-semibold text-slate-900">By room type</h2>
          <ul className="space-y-3">
            {d.byType.map((t) => (
              <li key={t.type} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium text-slate-900">{statusLabel(t.type)}</span>
                  <span className="text-sm text-slate-600">
                    {t.occupiedTonight}/{t.rooms} occupied · avg {formatMoney(t.averagePrice)}
                  </span>
                </div>
                <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
                  <div className="h-full rounded-full bg-brand-600" style={{ width: `${t.rooms === 0 ? 0 : (100 * t.occupiedTonight) / t.rooms}%` }} />
                </div>
              </li>
            ))}
          </ul>
        </section>
        <section>
          <h2 className="mb-3 text-lg font-semibold text-slate-900">Next arrivals</h2>
          {d.nextArrivals.length === 0 ? (
            <p className="rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-500 shadow-sm">No confirmed arrivals coming up.</p>
          ) : (
            <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white shadow-sm">
              {d.nextArrivals.map((b) => (
                <li key={b.id} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
                  <div>
                    <p className="font-medium text-slate-900">{b.customerName}</p>
                    <p className="text-xs text-slate-500">
                      Room {b.roomNumber} · {b.nights} night{b.nights === 1 ? '' : 's'}
                    </p>
                  </div>
                  <span className="text-slate-700">{formatDay(b.checkIn)}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  )
}
