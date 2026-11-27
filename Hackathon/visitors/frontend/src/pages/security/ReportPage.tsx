import { useQuery } from '@tanstack/react-query'
import { errorMessage } from '../../api/client'
import { securityApi } from '../../api/endpoints'
import { ErrorState, formatDay, LoadingState, PageHeader } from '../../components/ui'

function Stat({ title, value, tone, hint }: { title: string; value: string | number; tone?: string; hint?: string }) {
  return (
    <div className="h-full rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{title}</p>
      <p className={`mt-1 text-3xl font-semibold tabular-nums ${tone ?? 'text-slate-900'}`}>{value}</p>
      {hint && <p className="mt-0.5 text-xs text-slate-500">{hint}</p>}
    </div>
  )
}

export function ReportPage() {
  const stats = useQuery({ queryKey: ['security', 'stats'], queryFn: securityApi.stats })
  if (stats.isPending) return <LoadingState label="Loading the report…" />
  if (stats.isError) return <ErrorState message={errorMessage(stats.error)} onRetry={() => stats.refetch()} />
  const s = stats.data
  const peak = Math.max(1, ...s.lastSevenDays.map((d) => d.expected))

  return (
    <>
      <PageHeader title="Daily report" subtitle={`Visitor numbers for ${formatDay(s.date)} and the days around it.`} />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat title="Expected today" value={s.expectedToday} />
        <Stat title="Inside now" value={s.insideNow} tone="text-emerald-600" />
        <Stat title="Left today" value={s.checkedOutToday} />
        <Stat title="Cancelled today" value={s.cancelledToday} tone="text-slate-500" />
      </div>
      <div className="mt-4 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat title="Still to arrive" value={s.notYetArrived} tone="text-amber-600" />
        <Stat title="Coming in the next 7 days" value={s.upcomingNextSevenDays} />
        <Stat title="No-shows yesterday" value={s.noShowsYesterday} tone="text-rose-600" />
        <Stat title="Average visit" value={`${s.averageVisitMinutes} min`} hint="from check-in to check-out" />
      </div>

      <div className="mt-10 grid gap-8 lg:grid-cols-[2fr_1fr]">
        <section>
          <h2 className="mb-3 text-lg font-semibold text-slate-900">The last 7 days</h2>
          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="flex h-44 items-end gap-3" role="img" aria-label="Visitors expected and arrived per day">
              {s.lastSevenDays.map((d) => (
                <div key={d.date} className="flex flex-1 flex-col items-center gap-1">
                  <div className="flex h-36 w-full items-end justify-center gap-1">
                    <div className="w-1/3 rounded-t bg-slate-300" style={{ height: `${(100 * d.expected) / peak}%`, minHeight: d.expected ? 4 : 0 }} title={`${d.expected} expected`} />
                    <div className="w-1/3 rounded-t bg-brand-600" style={{ height: `${(100 * d.arrived) / peak}%`, minHeight: d.arrived ? 4 : 0 }} title={`${d.arrived} arrived`} />
                  </div>
                  <span className="text-[11px] text-slate-500">{new Date(d.date + 'T00:00').toLocaleDateString(undefined, { weekday: 'short' })}</span>
                </div>
              ))}
            </div>
            <p className="mt-3 flex gap-4 text-xs text-slate-600">
              <span className="flex items-center gap-1.5">
                <span className="size-2.5 rounded bg-slate-300" /> Expected
              </span>
              <span className="flex items-center gap-1.5">
                <span className="size-2.5 rounded bg-brand-600" /> Arrived
              </span>
            </p>
          </div>
        </section>
        <section>
          <h2 className="mb-3 text-lg font-semibold text-slate-900">Busiest hosts today</h2>
          {s.busiestHostsToday.length === 0 ? (
            <p className="rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-500 shadow-sm">Nobody is hosting today.</p>
          ) : (
            <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white shadow-sm">
              {s.busiestHostsToday.map((h) => (
                <li key={h.host} className="flex items-center justify-between px-4 py-3 text-sm">
                  <span className="text-slate-800">{h.host}</span>
                  <span className="font-semibold tabular-nums text-slate-900">{h.visitors}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  )
}
