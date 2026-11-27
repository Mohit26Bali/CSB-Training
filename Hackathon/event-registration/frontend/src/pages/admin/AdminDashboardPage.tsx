import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import { EmptyState, ErrorState, formatDate, LoadingState, PageHeader, StatusBadge } from '../../components/ui'
import { SeatBar } from '../employee/EventsPage'

function Stat({ label, value, tone, to }: { label: string; value: number; tone?: string; to?: string }) {
  const body = (
    <div className="h-full rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className={`mt-1 text-3xl font-semibold tabular-nums ${tone ?? 'text-slate-900'}`}>{value}</p>
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

export function AdminDashboardPage() {
  const dashboard = useQuery({ queryKey: ['admin', 'dashboard'], queryFn: adminApi.dashboard })

  if (dashboard.isPending) return <LoadingState label="Loading dashboard…" />
  if (dashboard.isError) return <ErrorState message={errorMessage(dashboard.error)} onRetry={() => dashboard.refetch()} />

  const d = dashboard.data
  const live = d.events.filter((e) => e.status === 'PUBLISHED')

  return (
    <>
      <PageHeader title="Dashboard" subtitle="How full your events are." />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Events" value={d.totalEvents} to="/admin/events" />
        <Stat label="Upcoming and open" value={d.upcomingPublished} tone="text-emerald-600" to="/admin/events" />
        <Stat label="Confirmed seats" value={d.confirmedSeats} />
        <Stat label="On waitlists" value={d.waitlisted} tone="text-amber-600" />
      </div>

      <h2 className="mb-3 mt-10 text-lg font-semibold text-slate-900">Participants per event</h2>
      {live.length === 0 ? (
        <EmptyState title="No published events yet" />
      ) : (
        <ul className="space-y-3">
          {live.map((e) => (
            <li key={e.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <span className="font-medium text-slate-900">{e.title}</span>
                  <span className="ml-2 text-sm text-slate-500">
                    {e.category} · {formatDate(e.startsAt)}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  {e.waitlisted > 0 && <StatusBadge status="WAITLISTED" />}
                  <span className="text-sm tabular-nums text-slate-700">
                    {e.confirmed}/{e.capacity} <span className="text-slate-400">({e.fillPercent}%)</span>
                    {e.waitlisted > 0 && <span className="text-slate-500"> + {e.waitlisted} waiting</span>}
                  </span>
                </div>
              </div>
              <div className="mt-2">
                <SeatBar confirmed={e.confirmed} capacity={e.capacity} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
