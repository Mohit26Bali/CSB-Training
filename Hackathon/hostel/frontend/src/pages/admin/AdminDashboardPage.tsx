import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import { Occupancy } from '../../components/RoomImage'
import { ErrorState, LoadingState, PageHeader, statusLabel } from '../../components/ui'

function Stat({ label, value, tone, to }: { label: string; value: string | number; tone?: string; to?: string }) {
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

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Occupancy across the hostel (rooms under maintenance are not counted)." />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Occupancy" value={`${d.occupancyPercent}%`} tone="text-brand-700" to="/admin/residents" />
        <Stat label="Beds free" value={d.free} tone="text-emerald-600" to="/admin/rooms" />
        <Stat label="Pending requests" value={d.pendingRequests} tone="text-amber-600" to="/admin/requests" />
        <Stat label="Full rooms" value={d.fullRooms} />
      </div>

      <h2 className="mb-3 mt-10 text-lg font-semibold text-slate-900">By room type</h2>
      <ul className="space-y-3">
        {d.byType.map((t) => (
          <li key={t.type} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-medium text-slate-900">{statusLabel(t.type)}</span>
              <span className="text-sm tabular-nums text-slate-600">
                {t.occupied}/{t.beds} beds · {t.rooms} room{t.rooms === 1 ? '' : 's'}
              </span>
            </div>
            <div className="mt-2">
              <Occupancy occupied={t.occupied} capacity={t.beds} />
            </div>
          </li>
        ))}
      </ul>
    </>
  )
}
