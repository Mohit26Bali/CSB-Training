import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { claimApi } from '../../api/endpoints'
import type { ClaimStatus } from '../../api/types'
import { ClaimDetails } from '../../components/ClaimDetails'
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge, statusLabel } from '../../components/ui'

type Tab = 'ALL' | ClaimStatus
const tabs: Tab[] = ['ALL', 'PENDING', 'APPROVED', 'IN_REPAIR', 'COMPLETED', 'REJECTED']

export function MyClaimsPage() {
  const [tab, setTab] = useState<Tab>('ALL')
  const claims = useQuery({
    queryKey: ['claims', 'mine', tab],
    queryFn: () => claimApi.mine(tab === 'ALL' ? undefined : tab),
    placeholderData: (previous) => previous,
  })

  return (
    <>
      <PageHeader title="My claims" subtitle="Follow each claim from filing to repair." />

      <div role="tablist" aria-label="Filter by status" className="mb-4 flex gap-1 overflow-x-auto">
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

      {claims.isPending ? (
        <LoadingState />
      ) : claims.isError ? (
        <ErrorState message={errorMessage(claims.error)} onRetry={() => claims.refetch()} />
      ) : claims.data.length === 0 ? (
        <EmptyState
          title={tab === 'ALL' ? 'You haven’t filed any claims' : `No ${statusLabel(tab).toLowerCase()} claims`}
          action={
            tab === 'ALL' && (
              <Link to="/products" className="text-sm font-medium text-brand-700 hover:underline">
                Go to my products
              </Link>
            )
          }
        />
      ) : (
        <ul className="space-y-3">
          {claims.data.map((c) => (
            <li key={c.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <ClaimDetails c={c} />
                </div>
                <StatusBadge status={c.status} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
