import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import { EmptyState, ErrorState, LoadingState, PageHeader, SeatBar, Tag } from '../../components/ui'

function Stat({ label, value, hint, to, tone }: { label: string; value: number; hint?: string; to?: string; tone?: string }) {
  const body = (
    <div className="h-full rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className={`mt-1 text-3xl font-semibold tabular-nums ${tone ?? 'text-slate-900'}`}>{value}</p>
      {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
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

export function DashboardPage() {
  const dashboard = useQuery({ queryKey: ['admin', 'dashboard'], queryFn: adminApi.dashboard })

  if (dashboard.isPending) return <LoadingState label="Loading dashboard…" />
  if (dashboard.isError) return <ErrorState message={errorMessage(dashboard.error)} onRetry={() => dashboard.refetch()} />

  const d = dashboard.data
  const busiest = [...d.courses].sort((a, b) => b.approvedCount / b.capacity - a.approvedCount / a.capacity)

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Enrollment activity across all courses." />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        <Stat label="Active courses" value={d.activeCourses} hint={`${d.totalCourses} in total`} to="/admin/courses" />
        <Stat label="Employees" value={d.totalEmployees} />
        <Stat label="Pending" value={d.pending} hint="Waiting for review" to="/admin/requests" tone="text-amber-600" />
        <Stat label="Approved" value={d.approved} tone="text-emerald-600" />
        <Stat label="Rejected" value={d.rejected} tone="text-rose-600" />
      </div>

      <h2 className="mb-3 mt-10 text-lg font-semibold text-slate-900">Seats by course</h2>
      {busiest.length === 0 ? (
        <EmptyState title="No courses yet" hint="Create a course to start taking enrollments." />
      ) : (
        <ul className="grid gap-3 md:grid-cols-2">
          {busiest.map((c) => (
            <li key={c.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="mb-3 flex items-center justify-between gap-2">
                <p className="font-medium text-slate-900">{c.title}</p>
                <div className="flex gap-1.5">
                  <Tag>{c.category}</Tag>
                  {!c.active && <Tag>Inactive</Tag>}
                </div>
              </div>
              <SeatBar approved={c.approvedCount} pending={c.pendingCount} capacity={c.capacity} />
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
