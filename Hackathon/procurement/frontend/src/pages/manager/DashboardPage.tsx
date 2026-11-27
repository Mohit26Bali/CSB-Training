import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { managerApi } from '../../api/endpoints'
import { EmptyState, ErrorState, formatMoney, LoadingState, PageHeader, StatusBadge } from '../../components/ui'

function waiting(iso: string): string {
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000)
  return days <= 0 ? 'today' : `${days} day${days === 1 ? '' : 's'} ago`
}

export function DashboardPage() {
  const dashboard = useQuery({ queryKey: ['manager', 'dashboard'], queryFn: managerApi.dashboard })

  if (dashboard.isPending) return <LoadingState label="Loading dashboard…" />
  if (dashboard.isError) return <ErrorState message={errorMessage(dashboard.error)} onRetry={() => dashboard.refetch()} />

  const d = dashboard.data
  const usedPct = d.monthlyBudget === 0 ? 0 : Math.min(100, (d.spentThisMonth / d.monthlyBudget) * 100)
  const nearLimit = usedPct >= 85
  const maxCategory = Math.max(...d.spendByCategory.map((c) => c.amount), 1)
  const monthName = new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' }).format(new Date())

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Spending and the state of every request." />

      <section aria-labelledby="budget" className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="budget" className="text-sm font-medium uppercase tracking-wide text-slate-500">
            Budget for {monthName}
          </h2>
          <p className="text-sm text-slate-600">
            <span className={`text-2xl font-semibold tabular-nums ${nearLimit ? 'text-rose-600' : 'text-slate-900'}`}>
              {formatMoney(d.spentThisMonth)}
            </span>{' '}
            of {formatMoney(d.monthlyBudget)} approved
          </p>
        </div>
        <div
          role="meter"
          aria-label="Budget used this month"
          aria-valuemin={0}
          aria-valuemax={d.monthlyBudget}
          aria-valuenow={d.spentThisMonth}
          className="mt-3 h-3 overflow-hidden rounded-full bg-slate-200"
        >
          <div className={`h-full rounded-full ${nearLimit ? 'bg-rose-500' : 'bg-brand-600'}`} style={{ width: `${usedPct}%` }} />
        </div>
        <p className="mt-2 text-sm text-slate-500">
          {formatMoney(d.remainingThisMonth)} left. Requests that would go over the budget cannot be approved.
        </p>
      </section>

      <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {d.byStatus.map((s) => (
          <Link key={s.status} to="/manager/review" className="block rounded-xl hover:ring-2 hover:ring-brand-100">
            <div className="h-full rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
              <StatusBadge status={s.status} />
              <p className="mt-2 text-2xl font-semibold tabular-nums text-slate-900">{s.count}</p>
              <p className="text-xs text-slate-500">{formatMoney(s.amount)}</p>
            </div>
          </Link>
        ))}
      </div>

      {d.oldestPendingAt && (
        <p className="mt-4 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-900">
          The oldest pending request was submitted {waiting(d.oldestPendingAt)}.{' '}
          <Link to="/manager/review" className="font-medium underline">
            Review now
          </Link>
        </p>
      )}

      <h2 className="mb-3 mt-10 text-lg font-semibold text-slate-900">Approved spend by category ({monthName})</h2>
      {d.spendByCategory.length === 0 ? (
        <EmptyState title="Nothing approved this month yet" />
      ) : (
        <ul className="space-y-3 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          {d.spendByCategory.map((c) => (
            <li key={c.category}>
              <div className="mb-1 flex items-baseline justify-between gap-3 text-sm">
                <span className="font-medium text-slate-800">{c.categoryLabel}</span>
                <span className="tabular-nums text-slate-600">
                  {formatMoney(c.amount)} <span className="text-xs text-slate-400">({c.count})</span>
                </span>
              </div>
              <div
                role="meter"
                aria-label={`${c.categoryLabel} spend`}
                aria-valuemin={0}
                aria-valuemax={maxCategory}
                aria-valuenow={c.amount}
                className="h-2 overflow-hidden rounded-full bg-slate-200"
              >
                <div className="h-full rounded-full bg-brand-600" style={{ width: `${(c.amount / maxCategory) * 100}%` }} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
