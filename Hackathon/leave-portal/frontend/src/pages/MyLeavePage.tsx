import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../api/client'
import { leaveApi } from '../api/endpoints'
import type { Balance, Leave, LeaveStatus } from '../api/types'
import { useToast } from '../components/Toast'
import {
  Button,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  formatDate,
  LoadingState,
  PageHeader,
  StatusBadge,
  Tag,
} from '../components/ui'
import { formatRange, toIso } from '../dates'
import { LeaveFormModal } from './LeaveFormModal'

type Tab = 'ALL' | LeaveStatus

const tabs: { value: Tab; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

function BalanceCard({ b }: { b: Balance }) {
  const taken = b.used + b.pending
  const pct = b.allocated === 0 ? 0 : Math.min(100, (taken / b.allocated) * 100)
  const usedPct = b.allocated === 0 ? 0 : Math.min(100, (b.used / b.allocated) * 100)
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{b.label}</p>
      {b.tracked ? (
        <>
          <p className="mt-1 text-3xl font-semibold tabular-nums text-slate-900">
            {b.available} <span className="text-sm font-normal text-slate-500">of {b.allocated} days left</span>
          </p>
          <div
            role="meter"
            aria-label={`${b.label} used`}
            aria-valuemin={0}
            aria-valuemax={b.allocated}
            aria-valuenow={taken}
            className="mt-3 flex h-2 overflow-hidden rounded-full bg-slate-200"
          >
            <div className="bg-brand-600" style={{ width: `${usedPct}%` }} />
            <div className="bg-amber-300" style={{ width: `${Math.max(0, pct - usedPct)}%` }} />
          </div>
          <p className="mt-1.5 text-xs text-slate-500">
            {b.used} used{b.pending > 0 && ` · ${b.pending} requested`}
          </p>
        </>
      ) : (
        <p className="mt-1 text-sm text-slate-500">No limit. Days are not deducted from a balance.</p>
      )}
    </div>
  )
}

export function MyLeavePage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('ALL')
  const [type, setType] = useState('')
  const [creating, setCreating] = useState(false)
  const [toCancel, setToCancel] = useState<Leave | null>(null)

  const status = tab === 'ALL' ? undefined : tab
  const leave = useQuery({
    queryKey: ['leave', 'mine', status, type],
    queryFn: () => leaveApi.mine({ status, type: type || undefined }),
    placeholderData: (previous) => previous,
  })
  const balance = useQuery({ queryKey: ['leave', 'balance'], queryFn: leaveApi.balance })
  const types = useQuery({ queryKey: ['leave', 'types'], queryFn: leaveApi.types, staleTime: Infinity })

  const cancel = useMutation({
    mutationFn: (id: number) => leaveApi.cancel(id),
    onSuccess: () => toast.success('Leave request cancelled'),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => {
      setToCancel(null)
      return queryClient.invalidateQueries()
    },
  })

  const today = toIso(new Date())
  const canCancel = (l: Leave) => l.status === 'PENDING' || (l.status === 'APPROVED' && l.startDate > today)
  const filtered = tab !== 'ALL' || type !== ''

  return (
    <>
      <PageHeader
        title="My leave"
        subtitle={`Your ${new Date().getFullYear()} balance and requests.`}
        actions={<Button onClick={() => setCreating(true)}>Request leave</Button>}
      />

      {balance.data && (
        <div className="mb-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {balance.data.map((b) => (
            <BalanceCard key={b.leaveType} b={b} />
          ))}
        </div>
      )}

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex gap-1 overflow-x-auto">
          {tabs.map((t) => (
            <button
              key={t.value}
              role="tab"
              aria-selected={tab === t.value}
              onClick={() => setTab(t.value)}
              className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${
                tab === t.value ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
        <div className="ml-auto">
          <label htmlFor="type" className="sr-only">
            Filter by leave type
          </label>
          <select
            id="type"
            value={type}
            onChange={(e) => setType(e.target.value)}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          >
            <option value="">All leave types</option>
            {(types.data ?? []).map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {leave.isPending ? (
        <LoadingState />
      ) : leave.isError ? (
        <ErrorState message={errorMessage(leave.error)} onRetry={() => leave.refetch()} />
      ) : leave.data.length === 0 ? (
        <EmptyState
          title={filtered ? 'No requests match your filters' : 'You haven’t requested any leave yet'}
          action={!filtered && <Button onClick={() => setCreating(true)}>Request leave</Button>}
        />
      ) : (
        <ul className="space-y-3">
          {leave.data.map((l) => (
            <li key={l.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-medium text-slate-900">{formatRange(l.startDate, l.endDate)}</p>
                    <Tag tone="green">{l.leaveTypeLabel}</Tag>
                    <span className="text-xs text-slate-500">
                      {l.days} working day{l.days === 1 ? '' : 's'}
                    </span>
                  </div>
                  <p className="mt-1 text-sm text-slate-600">{l.reason}</p>
                </div>
                <div className="flex items-center gap-3">
                  <StatusBadge status={l.status} />
                  {canCancel(l) && (
                    <Button variant="secondary" small onClick={() => setToCancel(l)}>
                      Cancel
                    </Button>
                  )}
                </div>
              </div>
              {l.decidedBy && (l.status === 'APPROVED' || l.status === 'REJECTED') && (
                <p className="mt-3 text-xs text-slate-500">
                  {l.status === 'APPROVED' ? 'Approved' : 'Rejected'} by {l.decidedBy} on {formatDate(l.decidedAt)}
                  {l.decisionNote && (
                    <span className="mt-2 block rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-700">“{l.decisionNote}”</span>
                  )}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}

      {creating && <LeaveFormModal onClose={() => setCreating(false)} />}

      {toCancel && (
        <ConfirmDialog
          title="Cancel this leave?"
          message={
            toCancel.status === 'APPROVED'
              ? `Your approved leave (${formatRange(toCancel.startDate, toCancel.endDate)}) will be cancelled and the ${toCancel.days} day(s) returned to your balance.`
              : `Your request for ${formatRange(toCancel.startDate, toCancel.endDate)} will be withdrawn.`
          }
          confirmLabel="Cancel leave"
          danger
          loading={cancel.isPending}
          onConfirm={() => cancel.mutate(toCancel.id)}
          onCancel={() => setToCancel(null)}
        />
      )}
    </>
  )
}
