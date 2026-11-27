import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { referralApi } from '../../api/endpoints'
import type { ReferralStatus } from '../../api/types'
import { Pipeline } from '../../components/Pipeline'
import { EmptyState, ErrorState, formatDate, LoadingState, PageHeader, statusLabel } from '../../components/ui'

type Tab = 'ALL' | ReferralStatus
const tabs: Tab[] = ['ALL', 'SUBMITTED', 'SCREENING', 'INTERVIEW', 'SELECTED', 'REJECTED']

export function MyReferralsPage() {
  const [tab, setTab] = useState<Tab>('ALL')
  const referrals = useQuery({
    queryKey: ['referrals', 'mine', tab],
    queryFn: () => referralApi.mine(tab === 'ALL' ? undefined : tab),
    placeholderData: (previous) => previous,
  })

  return (
    <>
      <PageHeader title="My referrals" subtitle="Follow each candidate you have referred through the hiring process." />

      <div role="tablist" aria-label="Filter by stage" className="mb-4 flex gap-1 overflow-x-auto">
        {tabs.map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${
              tab === t ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            {t === 'ALL' ? 'All' : statusLabel(t)}
          </button>
        ))}
      </div>

      {referrals.isPending ? (
        <LoadingState />
      ) : referrals.isError ? (
        <ErrorState message={errorMessage(referrals.error)} onRetry={() => referrals.refetch()} />
      ) : referrals.data.length === 0 ? (
        <EmptyState
          title={tab === 'ALL' ? 'You haven’t referred anyone yet' : `No ${statusLabel(tab).toLowerCase()} referrals`}
          action={
            tab === 'ALL' && (
              <Link to="/jobs" className="text-sm font-medium text-brand-700 hover:underline">
                See open positions
              </Link>
            )
          }
        />
      ) : (
        <ul className="space-y-3">
          {referrals.data.map((r) => (
            <li key={r.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <p className="font-medium text-slate-900">{r.candidateName}</p>
                  <p className="text-sm text-slate-600">
                    for {r.jobTitle} <span className="text-slate-400">· {r.jobDepartment}</span>
                  </p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {r.candidateEmail}
                    {r.candidatePhone && ` · ${r.candidatePhone}`}
                  </p>
                </div>
                <Pipeline status={r.status} />
              </div>
              {r.notes && <p className="mt-2 text-sm text-slate-600">“{r.notes}”</p>}
              <p className="mt-2 text-xs text-slate-500">
                Referred {formatDate(r.submittedAt)}
                {r.updatedBy && ` · updated by ${r.updatedBy} on ${formatDate(r.updatedAt)}`}
              </p>
              {r.statusNote && <p className="mt-2 rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-700">Recruiter’s note: {r.statusNote}</p>}
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
