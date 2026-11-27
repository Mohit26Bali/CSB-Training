import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { agentApi } from '../../api/endpoints'
import { Stars } from '../../components/Chips'
import { ErrorState, LoadingState, PageHeader, statusLabel } from '../../components/ui'

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

const priorityBar: Record<string, string> = { URGENT: 'bg-rose-500', HIGH: 'bg-amber-500', MEDIUM: 'bg-sky-500', LOW: 'bg-slate-400' }

export function StatisticsPage() {
  const stats = useQuery({ queryKey: ['agent', 'statistics'], queryFn: agentApi.statistics })
  if (stats.isPending) return <LoadingState label="Loading statistics…" />
  if (stats.isError) return <ErrorState message={errorMessage(stats.error)} onRetry={() => stats.refetch()} />
  const s = stats.data
  const peak = Math.max(1, ...s.lastSevenDays.flatMap((d) => [d.filed, d.resolved]))
  const maxCategory = Math.max(1, ...s.byCategory.map((c) => c.count))
  const activeTotal = s.activeByPriority.reduce((sum, p) => sum + p.count, 0)

  return (
    <>
      <PageHeader title="Statistics" subtitle="How complaints are coming in and being handled." />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Unassigned" value={s.unassigned} tone="text-amber-600" to="/agent/queue" />
        <Stat label="Mine, active" value={s.mine} to="/agent/queue" />
        <Stat label="Waiting 3+ days" value={s.waitingOverThreeDays} tone="text-rose-600" to="/agent/queue" />
        <Stat label="Resolution rate" value={`${s.resolutionRatePercent}%`} tone="text-emerald-600" hint={`${s.total} complaints in all`} />
      </div>
      <div className="mt-4 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Open" value={s.open} />
        <Stat label="In progress" value={s.inProgress} />
        <Stat label="Average time to resolve" value={`${s.averageResolutionHours}h`} />
        <div className="h-full rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Customer rating</p>
          <p className="mt-1 text-3xl font-semibold tabular-nums text-slate-900">{s.ratings === 0 ? '–' : s.averageRating.toFixed(1)}</p>
          <div className="mt-0.5 flex items-center gap-2">
            <Stars value={Math.round(s.averageRating)} size="size-3.5" />
            <span className="text-xs text-slate-500">{s.ratings} rating{s.ratings === 1 ? '' : 's'}</span>
          </div>
        </div>
      </div>

      <section className="mt-10">
        <h2 className="mb-3 text-lg font-semibold text-slate-900">The last 7 days</h2>
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex h-40 items-end gap-3" role="img" aria-label="Complaints filed and resolved per day">
            {s.lastSevenDays.map((d) => (
              <div key={d.date} className="flex flex-1 flex-col items-center gap-1">
                <div className="flex h-32 w-full items-end justify-center gap-1">
                  <div className="w-1/3 rounded-t bg-brand-600" style={{ height: `${(100 * d.filed) / peak}%`, minHeight: d.filed ? 4 : 0 }} title={`${d.filed} filed`} />
                  <div className="w-1/3 rounded-t bg-emerald-500" style={{ height: `${(100 * d.resolved) / peak}%`, minHeight: d.resolved ? 4 : 0 }} title={`${d.resolved} resolved`} />
                </div>
                <span className="text-[11px] text-slate-500">{new Date(d.date + 'T00:00').toLocaleDateString(undefined, { weekday: 'short' })}</span>
              </div>
            ))}
          </div>
          <p className="mt-3 flex gap-4 text-xs text-slate-600">
            <span className="flex items-center gap-1.5">
              <span className="size-2.5 rounded bg-brand-600" /> Filed
            </span>
            <span className="flex items-center gap-1.5">
              <span className="size-2.5 rounded bg-emerald-500" /> Resolved
            </span>
          </p>
        </div>
      </section>

      <div className="mt-10 grid gap-8 lg:grid-cols-2">
        <section>
          <h2 className="mb-3 text-lg font-semibold text-slate-900">Active by priority</h2>
          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="flex h-3 overflow-hidden rounded-full bg-slate-100">
              {s.activeByPriority.map((p) => (
                <div key={p.name} className={priorityBar[p.name]} style={{ width: `${activeTotal === 0 ? 0 : (100 * p.count) / activeTotal}%` }} title={`${p.name}: ${p.count}`} />
              ))}
            </div>
            <ul className="mt-4 grid grid-cols-2 gap-2 text-sm">
              {s.activeByPriority.map((p) => (
                <li key={p.name} className="flex items-center gap-2">
                  <span className={`size-2.5 rounded-full ${priorityBar[p.name]}`} />
                  <span className="text-slate-700">{statusLabel(p.name)}</span>
                  <span className="ml-auto font-semibold tabular-nums text-slate-900">{p.count}</span>
                </li>
              ))}
            </ul>
          </div>
        </section>
        <section>
          <h2 className="mb-3 text-lg font-semibold text-slate-900">All complaints by category</h2>
          <ul className="space-y-2 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            {s.byCategory.map((c) => (
              <li key={c.name} className="text-sm">
                <div className="flex justify-between">
                  <span className="text-slate-700">{statusLabel(c.name)}</span>
                  <span className="font-semibold tabular-nums text-slate-900">{c.count}</span>
                </div>
                <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-slate-100">
                  <div className="h-full rounded-full bg-brand-600" style={{ width: `${(100 * c.count) / maxCategory}%` }} />
                </div>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </>
  )
}
