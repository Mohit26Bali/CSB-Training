import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import { floorLabel } from '../../components/SlotMap'
import { ErrorState, LoadingState, PageHeader } from '../../components/ui'

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
      <PageHeader title="Dashboard" subtitle="Occupancy of the lot (reserved and closed slots are not counted)." />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Occupancy" value={`${d.occupancyPercent}%`} tone="text-brand-700" to="/admin/allocations" />
        <Stat label="Free slots" value={d.free} tone="text-emerald-600" to="/admin/slots" />
        <Stat label="Waiting requests" value={d.pendingRequests} tone="text-amber-600" to="/admin/requests" />
        <Stat label="Reserved / closed" value={`${d.reserved} / ${d.maintenance}`} />
      </div>

      <h2 className="mb-3 mt-10 text-lg font-semibold text-slate-900">Floor by floor</h2>
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {d.byFloor.map((f) => (
          <li key={f.floor} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="font-medium text-slate-900">{floorLabel(f.floor)}</p>
            <p className="mt-2 text-3xl font-semibold tabular-nums text-brand-700">{f.free}</p>
            <p className="text-xs text-slate-500">
              free of {f.slots} · {f.occupied} in use
            </p>
          </li>
        ))}
      </ul>
    </>
  )
}
