import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { managerApi } from '../../api/endpoints'
import { ErrorState, LoadingState, PageHeader, statusLabel } from '../../components/ui'

function Tile({ label, value, tone, to }: { label: string; value: string | number; tone?: string; to?: string }) {
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

export function DashboardPage() {
  const dashboard = useQuery({ queryKey: ['manager', 'dashboard'], queryFn: managerApi.dashboard })
  if (dashboard.isPending) return <LoadingState label="Loading dashboard…" />
  if (dashboard.isError) return <ErrorState message={errorMessage(dashboard.error)} onRetry={() => dashboard.refetch()} />
  const d = dashboard.data
  const pipeline = [
    ['Open', d.open, 'bg-amber-500'],
    ['Assigned', d.assigned, 'bg-sky-400'],
    ['In progress', d.inProgress, 'bg-sky-600'],
    ['Resolved', d.resolved, 'bg-emerald-500'],
    ['Closed', d.closed, 'bg-slate-400'],
  ] as const
  const total = pipeline.reduce((s, p) => s + p[1], 0)
  const maxLoad = Math.max(1, ...d.technicianLoad.map((t) => t.activeJobs))

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Everything in the community at a glance." />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Tile label="Emergencies" value={d.emergencies} tone="text-rose-600" to="/manager/requests" />
        <Tile label="Stale (5+ days)" value={d.stale} tone="text-rose-600" to="/manager/requests" />
        <Tile label="Ageing (2-4 days)" value={d.aging} tone="text-amber-600" to="/manager/requests" />
        <Tile label="Average time to resolve" value={`${d.averageResolutionHours}h`} />
      </div>

      <section className="mt-8">
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Where requests are</h2>
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex h-4 overflow-hidden rounded-full bg-slate-100">
            {pipeline.map(([label, n, color]) => (
              <div key={label} className={color} style={{ width: `${total === 0 ? 0 : (100 * n) / total}%` }} title={`${label}: ${n}`} />
            ))}
          </div>
          <ul className="mt-4 grid grid-cols-2 gap-3 text-sm sm:grid-cols-5">
            {pipeline.map(([label, n, color]) => (
              <li key={label} className="flex items-center gap-2">
                <span className={`size-2.5 rounded-full ${color}`} />
                <span className="text-slate-700">{label}</span>
                <span className="ml-auto font-semibold tabular-nums text-slate-900">{n}</span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <div className="mt-8 grid gap-8 lg:grid-cols-2">
        <section>
          <h2 className="mb-3 text-lg font-semibold text-slate-900">By category</h2>
          <ul className="grid grid-cols-2 gap-3">
            {d.byCategory.map((c) => (
              <li key={c.category} className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
                <p className="text-sm font-medium text-slate-900">{statusLabel(c.category)}</p>
                <p className="mt-1 text-xs text-slate-500">
                  <span className={`font-semibold ${c.active > 0 ? 'text-amber-700' : 'text-slate-500'}`}>{c.active} open</span> · {c.resolved} done · {c.total} in all
                </p>
              </li>
            ))}
          </ul>
        </section>
        <section>
          <h2 className="mb-3 text-lg font-semibold text-slate-900">Technician workload</h2>
          <ul className="space-y-2 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            {d.technicianLoad.filter((t) => t.active).map((t) => (
              <li key={t.id} className="text-sm">
                <div className="flex justify-between">
                  <span className="text-slate-800">
                    {t.name} <span className="text-xs text-slate-500">({statusLabel(t.trade).toLowerCase()})</span>
                  </span>
                  <span className="font-semibold tabular-nums text-slate-900">{t.activeJobs}</span>
                </div>
                <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-slate-100">
                  <div className="h-full rounded-full bg-brand-600" style={{ width: `${(100 * t.activeJobs) / maxLoad}%` }} />
                </div>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </>
  )
}
