import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { librarianApi } from '../../api/endpoints'
import { ErrorState, formatDay, formatMoney, LoadingState, PageHeader } from '../../components/ui'

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

export function DeskPage() {
  const dashboard = useQuery({ queryKey: ['librarian', 'dashboard'], queryFn: librarianApi.dashboard })
  if (dashboard.isPending) return <LoadingState label="Loading the desk…" />
  if (dashboard.isError) return <ErrorState message={errorMessage(dashboard.error)} onRetry={() => dashboard.refetch()} />
  const d = dashboard.data

  return (
    <>
      <PageHeader title="Desk" subtitle="What needs your attention today." />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="To hand over" value={d.reserved} tone="text-amber-600" to="/desk/loans" />
        <Stat label="On loan" value={d.issued} to="/desk/loans" />
        <Stat label="Overdue" value={d.overdue} tone="text-rose-600" to="/desk/loans" />
        <Stat label="Fines collected" value={formatMoney(d.finesCollected)} tone="text-emerald-600" />
      </div>

      <div className="mt-10 grid gap-8 lg:grid-cols-2">
        <section>
          <h2 className="mb-3 text-lg font-semibold text-slate-900">Overdue books</h2>
          {d.overdueLoans.length === 0 ? (
            <p className="rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-500 shadow-sm">Nothing is overdue. 🎉</p>
          ) : (
            <ul className="space-y-2">
              {d.overdueLoans.map((l) => (
                <li key={l.id} className="rounded-xl border border-rose-200 bg-white p-3 shadow-sm">
                  <p className="font-medium text-slate-900">{l.bookTitle}</p>
                  <p className="text-sm text-slate-600">
                    {l.memberName} · due {formatDay(l.dueDate!)} · {l.daysOverdue} day{l.daysOverdue === 1 ? '' : 's'} late · {formatMoney(l.fine)}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </section>
        <section>
          <h2 className="mb-3 text-lg font-semibold text-slate-900">Stock by category</h2>
          <ul className="space-y-2">
            {d.byCategory.map((c) => (
              <li key={c.category} className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
                <div className="flex justify-between text-sm">
                  <span className="font-medium text-slate-900">{c.category}</span>
                  <span className="tabular-nums text-slate-600">
                    {c.available}/{c.copies} copies in · {c.titles} titles
                  </span>
                </div>
                <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-slate-100">
                  <div className="h-full rounded-full bg-brand-600" style={{ width: `${c.copies === 0 ? 0 : (100 * c.available) / c.copies}%` }} />
                </div>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </>
  )
}
