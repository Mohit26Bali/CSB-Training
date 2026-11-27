import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { applicationApi } from '../../api/endpoints'
import { ScoreBadge } from '../../components/ScoreBadge'
import { EmptyState, ErrorState, formatDate, formatMoney, LoadingState, PageHeader, StatusBadge } from '../../components/ui'

export function MyApplicationsPage() {
  const mine = useQuery({ queryKey: ['applications', 'mine'], queryFn: applicationApi.mine })

  return (
    <>
      <PageHeader title="My applications" subtitle="Where each of your applications stands." />

      {mine.isPending ? (
        <LoadingState />
      ) : mine.isError ? (
        <ErrorState message={errorMessage(mine.error)} onRetry={() => mine.refetch()} />
      ) : mine.data.length === 0 ? (
        <EmptyState
          title="You haven’t applied for anything yet"
          action={
            <Link to="/programs" className="text-sm font-medium text-brand-700 hover:underline">
              Browse scholarships
            </Link>
          }
        />
      ) : (
        <ul className="space-y-3">
          {mine.data.map((a) => (
            <li key={a.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <p className="font-medium text-slate-900">{a.programName}</p>
                  <p className="mt-0.5 text-sm text-slate-600">
                    {a.course}, year {a.yearOfStudy} · CGPA {a.gpa.toFixed(2)} · income {formatMoney(a.annualFamilyIncome)}
                  </p>
                  <div className="mt-2">
                    <ScoreBadge score={a.eligibilityScore} meets={a.meetsCriteria} />
                  </div>
                  <p className="mt-2 text-xs text-slate-500">Submitted {formatDate(a.submittedAt)}</p>
                </div>
                <div className="text-right">
                  <StatusBadge status={a.status} />
                  {a.status === 'APPROVED' && <p className="mt-1 text-sm font-semibold text-emerald-700">{formatMoney(a.programAmount)} awarded</p>}
                </div>
              </div>
              {a.decisionReason && (
                <p className={`mt-3 rounded-lg px-3 py-2 text-sm ${a.status === 'APPROVED' ? 'bg-emerald-50 text-emerald-900' : 'bg-rose-50 text-rose-800'}`}>
                  <span className="font-medium">
                    {a.status === 'APPROVED' ? 'Approved' : 'Rejected'} by {a.decidedBy} on {formatDate(a.decidedAt)}:
                  </span>{' '}
                  {a.decisionReason}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
