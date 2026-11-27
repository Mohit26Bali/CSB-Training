import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import { ErrorState, formatDate, formatMoney, LoadingState, PageHeader, statusLabel } from '../../components/ui'

function Stat({ label, value, hint, to, alert }: { label: string; value: string | number; hint?: string; to?: string; alert?: boolean }) {
  const body = (
    <div className={`rounded-xl border bg-white p-4 shadow-sm transition hover:border-brand-300 ${alert ? 'border-amber-300' : 'border-slate-200'}`}>
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-900">{value}</p>
      {hint && <p className="text-xs text-slate-500">{hint}</p>}
    </div>
  )
  return to ? <Link to={to}>{body}</Link> : body
}

function Bars({ title, rows, total }: { title: string; rows: { key: string; count: number }[]; total: number }) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="mb-3 font-semibold text-slate-900">{title}</h2>
      <ul className="space-y-3">
        {rows.map((r) => (
          <li key={r.key}>
            <div className="flex justify-between text-sm">
              <span className="text-slate-800">{statusLabel(r.key)}</span>
              <span className="font-medium text-slate-900">{r.count}</span>
            </div>
            <div className="mt-1 h-2 rounded bg-slate-100">
              <div className="h-2 rounded bg-brand-500" style={{ width: `${total === 0 ? 0 : (r.count / total) * 100}%` }} />
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}

export function DashboardPage() {
  const dash = useQuery({ queryKey: ['admin', 'dashboard'], queryFn: adminApi.dashboard })
  if (dash.isPending) return <LoadingState />
  if (dash.isError) return <ErrorState message={errorMessage(dash.error)} onRetry={() => dash.refetch()} />
  const d = dash.data

  return (
    <>
      <PageHeader title="Equipment overview" subtitle="Where everything is and what needs attention." />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4 xl:grid-cols-8">
        <Stat label="Assets" value={d.totalAssets} to="/admin/assets" />
        <Stat label="Available" value={d.available} to="/admin/assets" />
        <Stat label="With staff" value={d.assigned} />
        <Stat label="Return requests" value={d.returnRequests} to="/admin/returns" alert={d.returnRequests > 0} />
        <Stat label="Overdue" value={d.overdue} alert={d.overdue > 0} />
        <Stat label="In repair" value={d.inRepair} />
        <Stat label="Poor condition" value={d.poorCondition} />
        <Stat label="Asset value" value={formatMoney(d.assetValue)} hint="not retired" />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        <Bars title="By category" rows={d.byCategory} total={Math.max(1, ...d.byCategory.map((r) => r.count))} />
        <Bars title="By status" rows={d.byStatus} total={Math.max(1, ...d.byStatus.map((r) => r.count))} />
        <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-3 font-semibold text-slate-900">Waiting the longest</h2>
          {d.oldestRequests.length === 0 ? (
            <p className="text-sm text-slate-600">No return requests are waiting.</p>
          ) : (
            <ul className="space-y-3">
              {d.oldestRequests.map((a) => (
                <li key={a.id} className="text-sm">
                  <p className="font-medium text-slate-900">
                    {a.assetName} <span className="font-mono text-xs text-slate-500">{a.assetTag}</span>
                  </p>
                  <p className="text-slate-600">
                    {a.employeeName} · asked {formatDate(a.returnRequestedAt)}
                  </p>
                </li>
              ))}
              <li>
                <Link to="/admin/returns" className="text-sm font-medium text-brand-700 hover:underline">
                  Open the return queue →
                </Link>
              </li>
            </ul>
          )}
        </section>
      </div>
    </>
  )
}
