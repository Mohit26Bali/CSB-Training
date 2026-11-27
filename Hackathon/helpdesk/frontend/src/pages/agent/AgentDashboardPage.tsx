import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { agentApi } from '../../api/endpoints'
import { ErrorState, LoadingState, PageHeader, statusLabel } from '../../components/ui'

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

const priorityBar: Record<string, string> = { CRITICAL: 'bg-rose-500', HIGH: 'bg-amber-500', MEDIUM: 'bg-sky-500', LOW: 'bg-slate-400' }

export function AgentDashboardPage() {
  const dashboard = useQuery({ queryKey: ['agent', 'dashboard'], queryFn: agentApi.dashboard })
  if (dashboard.isPending) return <LoadingState label="Loading dashboard…" />
  if (dashboard.isError) return <ErrorState message={errorMessage(dashboard.error)} onRetry={() => dashboard.refetch()} />
  const d = dashboard.data
  const activeTotal = d.activeByPriority.reduce((s, p) => s + p.count, 0)
  const maxCategory = Math.max(1, ...d.byCategory.map((c) => c.count))

  return (
    <>
      <PageHeader title="Dashboard" subtitle="The state of the queue right now." />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Unassigned" value={d.unassigned} tone="text-amber-600" to="/agent/queue" />
        <Stat label="Mine, active" value={d.mine} to="/agent/queue" />
        <Stat label="Past SLA" value={d.breached} tone="text-rose-600" to="/agent/queue" />
        <Stat label="At risk" value={d.atRisk} tone="text-amber-600" to="/agent/queue" />
      </div>
      <div className="mt-4 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Open" value={d.open} />
        <Stat label="In progress" value={d.inProgress} />
        <Stat label="Resolved" value={d.resolved} tone="text-emerald-600" />
        <Stat label="Average time to resolve" value={`${d.averageResolutionHours}h`} />
      </div>

      <div className="mt-10 grid gap-8 lg:grid-cols-2">
        <section>
          <h2 className="mb-3 text-lg font-semibold text-slate-900">Active tickets by priority</h2>
          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="flex h-3 overflow-hidden rounded-full bg-slate-100">
              {d.activeByPriority.map((p) => (
                <div key={p.name} className={priorityBar[p.name]} style={{ width: `${activeTotal === 0 ? 0 : (100 * p.count) / activeTotal}%` }} title={`${p.name}: ${p.count}`} />
              ))}
            </div>
            <ul className="mt-4 grid grid-cols-2 gap-2 text-sm">
              {d.activeByPriority.map((p) => (
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
          <h2 className="mb-3 text-lg font-semibold text-slate-900">All tickets by category</h2>
          <ul className="space-y-2 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            {d.byCategory.map((c) => (
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
