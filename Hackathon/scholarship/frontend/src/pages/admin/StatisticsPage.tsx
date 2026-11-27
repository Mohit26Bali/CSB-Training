import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import { EmptyState, ErrorState, formatMoney, LoadingState, PageHeader } from '../../components/ui'

function Stat({ label, value, hint, tone, to }: { label: string; value: string | number; hint?: string; tone?: string; to?: string }) {
  const body = (
    <div className="h-full rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className={`mt-1 text-3xl font-semibold tabular-nums ${tone ?? 'text-slate-900'}`}>{value}</p>
      {hint && <p className="mt-1 text-sm text-slate-500">{hint}</p>}
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

export function StatisticsPage() {
  const stats = useQuery({ queryKey: ['admin', 'statistics'], queryFn: adminApi.statistics })

  if (stats.isPending) return <LoadingState label="Loading statistics…" />
  if (stats.isError) return <ErrorState message={errorMessage(stats.error)} onRetry={() => stats.refetch()} />

  const s = stats.data
  const decided = s.approved + s.rejected

  return (
    <>
      <PageHeader title="Application statistics" subtitle="Applications and awards across every scholarship." />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        <Stat label="Applications" value={s.totalApplications} />
        <Stat label="Awaiting decision" value={s.submitted} tone="text-amber-600" to="/admin/applications" />
        <Stat label="Approved" value={s.approved} tone="text-emerald-600" />
        <Stat label="Rejected" value={s.rejected} tone="text-rose-600" />
        <Stat label="Total awarded" value={formatMoney(s.totalAwarded)} hint={decided > 0 ? `${Math.round(s.approvalRate * 100)}% of decided applications approved` : 'Nothing decided yet'} />
      </div>

      <h2 className="mb-3 mt-10 text-lg font-semibold text-slate-900">By scholarship</h2>
      {s.byProgram.length === 0 ? (
        <EmptyState title="No scholarships yet" />
      ) : (
        <ul className="space-y-3">
          {s.byProgram.map((p) => {
            const pct = (n: number) => (p.total === 0 ? 0 : (n / p.total) * 100)
            return (
              <li key={p.programId} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="font-medium text-slate-900">{p.name}</p>
                  <p className="text-sm text-slate-600">
                    {p.total} application{p.total === 1 ? '' : 's'} · average score {p.averageScore.toFixed(1)} · awarded {formatMoney(p.awardedAmount)}
                  </p>
                </div>
                <div
                  role="img"
                  aria-label={`${p.approved} approved, ${p.rejected} rejected, ${p.submitted} waiting`}
                  className="mt-3 flex h-2.5 overflow-hidden rounded-full bg-slate-200"
                >
                  <div className="bg-emerald-500" style={{ width: `${pct(p.approved)}%` }} />
                  <div className="bg-rose-400" style={{ width: `${pct(p.rejected)}%` }} />
                  <div className="bg-amber-300" style={{ width: `${pct(p.submitted)}%` }} />
                </div>
                <p className="mt-1.5 text-xs text-slate-500">
                  {p.approved} approved · {p.rejected} rejected · {p.submitted} waiting
                </p>
              </li>
            )
          })}
        </ul>
      )}
    </>
  )
}
