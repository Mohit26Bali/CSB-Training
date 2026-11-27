import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { recruiterApi } from '../../api/endpoints'
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge, Tag } from '../../components/ui'

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

export function RecruiterDashboardPage() {
  const dashboard = useQuery({ queryKey: ['recruiter', 'dashboard'], queryFn: recruiterApi.dashboard })

  if (dashboard.isPending) return <LoadingState label="Loading dashboard…" />
  if (dashboard.isError) return <ErrorState message={errorMessage(dashboard.error)} onRetry={() => dashboard.refetch()} />

  const d = dashboard.data
  const waiting = d.byStatus.find((s) => s.status === 'SUBMITTED')?.count ?? 0

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Where referrals stand across all jobs." />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Open jobs" value={d.openJobs} to="/recruiter/jobs" />
        <Stat label="Referrals" value={d.totalReferrals} to="/recruiter/referrals" />
        <Stat label="New, not yet screened" value={waiting} tone="text-amber-600" to="/recruiter/referrals" />
        <Stat label="Selected" value={d.hires} tone="text-emerald-600" />
      </div>

      <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {d.byStatus.map((s) => (
          <div key={s.status} className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
            <StatusBadge status={s.status} />
            <p className="mt-2 text-2xl font-semibold tabular-nums text-slate-900">{s.count}</p>
          </div>
        ))}
      </div>

      <h2 className="mb-3 mt-10 text-lg font-semibold text-slate-900">Referrals by job</h2>
      {d.byJob.length === 0 ? (
        <EmptyState title="No jobs yet" />
      ) : (
        <ul className="space-y-3">
          {d.byJob.map((j) => {
            const pct = (n: number) => (j.total === 0 ? 0 : (n / j.total) * 100)
            return (
              <li key={j.jobId} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-medium text-slate-900">
                    {j.title} <span className="font-normal text-slate-500">· {j.department}</span>
                  </p>
                  <div className="flex items-center gap-2 text-sm text-slate-600">
                    <StatusBadge status={j.jobStatus} />
                    <span>
                      {j.total} referral{j.total === 1 ? '' : 's'}
                    </span>
                  </div>
                </div>
                {j.total > 0 && (
                  <>
                    <div role="img" aria-label={`${j.inProgress} in progress, ${j.selected} selected, ${j.rejected} rejected`} className="mt-3 flex h-2.5 overflow-hidden rounded-full bg-slate-200">
                      <div className="bg-brand-600" style={{ width: `${pct(j.inProgress)}%` }} />
                      <div className="bg-emerald-500" style={{ width: `${pct(j.selected)}%` }} />
                      <div className="bg-rose-400" style={{ width: `${pct(j.rejected)}%` }} />
                    </div>
                    <p className="mt-1.5 text-xs text-slate-500">
                      {j.inProgress} in progress · {j.selected} selected · {j.rejected} rejected
                    </p>
                  </>
                )}
                {j.total === 0 && j.jobStatus === 'PUBLISHED' && (
                  <p className="mt-2">
                    <Tag>No referrals yet</Tag>
                  </p>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </>
  )
}
