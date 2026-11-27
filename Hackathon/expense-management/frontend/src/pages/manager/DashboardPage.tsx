import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { managerApi } from '../../api/endpoints'
import { EmptyState, ErrorState, formatMoney, LoadingState, PageHeader } from '../../components/ui'

function Stat({
  label,
  count,
  amount,
  tone,
  to,
}: {
  label: string
  count: number
  amount: number
  tone: string
  to?: string
}) {
  const body = (
    <div className="h-full rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className={`mt-1 text-3xl font-semibold tabular-nums ${tone}`}>{count}</p>
      <p className="mt-1 text-sm text-slate-500">{formatMoney(amount)}</p>
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

/** "3 days" / "today": how long the oldest pending expense has been waiting. */
function waiting(iso: string): string {
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000)
  return days <= 0 ? 'today' : `${days} day${days === 1 ? '' : 's'} ago`
}

export function DashboardPage() {
  const dashboard = useQuery({ queryKey: ['manager', 'dashboard'], queryFn: managerApi.dashboard })

  if (dashboard.isPending) return <LoadingState label="Loading dashboard…" />
  if (dashboard.isError) return <ErrorState message={errorMessage(dashboard.error)} onRetry={() => dashboard.refetch()} />

  const { summary: s, approvedByCategory, oldestPendingAt } = dashboard.data
  const max = Math.max(...approvedByCategory.map((c) => c.amount), 1)

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Where your team’s expenses stand." />

      <div className="grid gap-4 sm:grid-cols-3">
        <Stat label="Pending review" count={s.pendingCount} amount={s.pendingAmount} tone="text-amber-600" to="/manager/review" />
        <Stat label="Approved" count={s.approvedCount} amount={s.approvedAmount} tone="text-emerald-600" />
        <Stat label="Rejected" count={s.rejectedCount} amount={s.rejectedAmount} tone="text-rose-600" />
      </div>

      {oldestPendingAt && (
        <p className="mt-4 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-900">
          The oldest pending expense was submitted {waiting(oldestPendingAt)}.{' '}
          <Link to="/manager/review" className="font-medium underline">
            Review now
          </Link>
        </p>
      )}

      <h2 className="mb-3 mt-10 text-lg font-semibold text-slate-900">Approved spend by category</h2>
      {approvedByCategory.length === 0 ? (
        <EmptyState title="No approved expenses yet" hint="Approved expenses will be summarised here." />
      ) : (
        <ul className="space-y-3 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          {approvedByCategory.map((c) => (
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
                aria-valuemax={max}
                aria-valuenow={c.amount}
                className="h-2 overflow-hidden rounded-full bg-slate-200"
              >
                <div className="h-full rounded-full bg-brand-600" style={{ width: `${(c.amount / max) * 100}%` }} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
