import { useQuery } from '@tanstack/react-query'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import { ErrorState, formatMoney, LoadingState, PageHeader, StatusBadge } from '../../components/ui'
import { parseDay } from '../../dates'

function Stat({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-900">{value}</p>
      {hint && <p className="text-xs text-slate-500">{hint}</p>}
    </div>
  )
}

export function SalesPage() {
  const dash = useQuery({ queryKey: ['admin', 'dashboard'], queryFn: adminApi.dashboard })
  if (dash.isPending) return <LoadingState />
  if (dash.isError) return <ErrorState message={errorMessage(dash.error)} onRetry={() => dash.refetch()} />
  const d = dash.data
  const top = Math.max(1, ...d.bestSellers.map((s) => s.quantity))
  const best = Math.max(1, ...d.lastSevenDays.map((x) => x.revenue))

  return (
    <>
      <PageHeader title="Sales" subtitle="Cancelled orders are not counted as sales." />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-6">
        <Stat label="Orders today" value={d.ordersToday} />
        <Stat label="Revenue today" value={formatMoney(d.revenueToday)} />
        <Stat label="Open orders" value={d.openOrders} hint="not delivered yet" />
        <Stat label="Average order" value={formatMoney(d.averageOrder)} />
        <Stat label="Delivered" value={d.deliveredThisWeek} hint="last 7 days" />
        <Stat label="Revenue" value={formatMoney(d.revenueThisWeek)} hint="last 7 days" />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="font-semibold text-slate-900">Last 7 days</h2>
          <p className="mb-4 text-sm text-slate-600">Revenue per day, with the number of orders.</p>
          <ul className="flex h-48 items-end gap-2">
            {d.lastSevenDays.map((day) => (
              <li key={day.date} className="flex h-full flex-1 flex-col items-center justify-end gap-1" title={`${day.date}: ${day.orders} orders, ${formatMoney(day.revenue)}`}>
                <span className="text-[11px] font-semibold text-slate-700">{day.orders}</span>
                <div className="flex w-full flex-1 items-end rounded bg-slate-100">
                  <div className="w-full rounded bg-brand-500" style={{ height: `${(day.revenue / best) * 100}%` }} />
                </div>
                <span className="text-[10px] text-slate-500">{new Intl.DateTimeFormat(undefined, { weekday: 'short' }).format(parseDay(day.date))}</span>
              </li>
            ))}
          </ul>
        </section>

        <div className="space-y-6">
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="mb-3 font-semibold text-slate-900">Best sellers</h2>
            {d.bestSellers.length === 0 ? (
              <p className="text-sm text-slate-600">No sales yet.</p>
            ) : (
              <ul className="space-y-3">
                {d.bestSellers.map((s) => (
                  <li key={s.name}>
                    <div className="flex justify-between text-sm">
                      <span className="text-slate-800">{s.name}</span>
                      <span className="font-medium text-slate-900">{s.quantity} sold</span>
                    </div>
                    <div className="mt-1 h-2 rounded bg-slate-100">
                      <div className="h-2 rounded bg-brand-500" style={{ width: `${(s.quantity / top) * 100}%` }} />
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="mb-3 font-semibold text-slate-900">Orders by status</h2>
            <ul className="space-y-2">
              {d.byStatus.map((s) => (
                <li key={s.status} className="flex items-center justify-between text-sm">
                  <StatusBadge status={s.status} />
                  <span className="font-medium text-slate-900">{s.count}</span>
                </li>
              ))}
            </ul>
          </section>
        </div>
      </div>
    </>
  )
}
