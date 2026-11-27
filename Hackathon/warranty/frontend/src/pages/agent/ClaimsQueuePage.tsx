import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { agentApi } from '../../api/endpoints'
import type { Claim, ClaimStatus, Priority } from '../../api/types'
import { ClaimDetails } from '../../components/ClaimDetails'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, LoadingState, Modal, PageHeader, StatusBadge, statusLabel, TextAreaField } from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = ClaimStatus | 'ALL'
const tabs: Tab[] = ['PENDING', 'APPROVED', 'IN_REPAIR', 'COMPLETED', 'REJECTED', 'ALL']
type Step = 'approve' | 'start-repair'

export function ClaimsQueuePage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('PENDING')
  const [priority, setPriority] = useState('')
  const [search, setSearch] = useState('')
  const [toReject, setToReject] = useState<Claim | null>(null)
  const [toComplete, setToComplete] = useState<Claim | null>(null)
  const debouncedSearch = useDebounced(search)

  const status = tab === 'ALL' ? undefined : tab
  const list = useQuery({
    queryKey: ['agent', 'claims', status, priority, debouncedSearch],
    queryFn: () => agentApi.claims({ status, priority: (priority || undefined) as Priority | undefined, search: debouncedSearch || undefined }),
    placeholderData: (previous) => previous,
  })
  const summary = useQuery({ queryKey: ['agent', 'summary'], queryFn: agentApi.summary })
  const counts = new Map((summary.data ?? []).map((s) => [s.status, s.count]))

  const step = useMutation({
    mutationFn: ({ kind, claim }: { kind: Step; claim: Claim }) =>
      kind === 'approve' ? agentApi.approve(claim.id) : agentApi.startRepair(claim.id),
    onSuccess: (c) => toast.success(c.status === 'APPROVED' ? `Approved the claim for ${c.productName}` : `Repair started for ${c.productName}`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(),
  })

  const busyId = step.isPending ? step.variables?.claim.id : undefined
  const filtered = priority !== '' || search !== ''

  return (
    <>
      <PageHeader title="Claims queue" subtitle="High-priority claims are shown first in the pending queue." />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex gap-1 overflow-x-auto">
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
              {t !== 'ALL' && counts.has(t) && <span className="ml-1 opacity-70">({counts.get(t)})</span>}
            </button>
          ))}
        </div>
        <div className="ml-auto flex flex-wrap gap-3">
          <label htmlFor="search" className="sr-only">
            Search claims
          </label>
          <input
            id="search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search product, serial or customer"
            className="w-60 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          />
          <label htmlFor="priority" className="sr-only">
            Filter by priority
          </label>
          <select id="priority" value={priority} onChange={(e) => setPriority(e.target.value)} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
            <option value="">Any priority</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </select>
        </div>
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title={filtered ? 'No claims match your filters' : tab === 'PENDING' ? 'No claims waiting' : 'Nothing to show'} />
      ) : (
        <ul className="space-y-3">
          {list.data.map((c) => (
            <li key={c.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <ClaimDetails c={c} showCustomer />
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <StatusBadge status={c.status} />
                  {c.status === 'PENDING' && (
                    <>
                      <Button small loading={busyId === c.id} disabled={step.isPending} onClick={() => step.mutate({ kind: 'approve', claim: c })}>
                        Approve
                      </Button>
                      <Button variant="secondary" small disabled={step.isPending} onClick={() => setToReject(c)}>
                        Reject
                      </Button>
                    </>
                  )}
                  {c.status === 'APPROVED' && (
                    <Button small loading={busyId === c.id} disabled={step.isPending} onClick={() => step.mutate({ kind: 'start-repair', claim: c })}>
                      Start repair
                    </Button>
                  )}
                  {c.status === 'IN_REPAIR' && (
                    <Button small disabled={step.isPending} onClick={() => setToComplete(c)}>
                      Mark completed
                    </Button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {toReject && <RejectModal claim={toReject} onClose={() => setToReject(null)} />}
      {toComplete && <CompleteModal claim={toComplete} onClose={() => setToComplete(null)} />}
    </>
  )
}

function RejectModal({ claim, onClose }: { claim: Claim; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const reject = useMutation({
    mutationFn: () => agentApi.reject(claim.id, reason),
    onSuccess: () => {
      toast.success(`Rejected the claim for ${claim.productName}`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(err instanceof ApiError ? (err.fieldErrors.reason ?? err.message) : errorMessage(err))
      if (err instanceof ApiError && err.status === 409) queryClient.invalidateQueries()
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    reject.mutate()
  }

  return (
    <Modal title="Reject this claim?" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="text-sm text-slate-600">
          {claim.customerName}’s claim for {claim.brand} {claim.productName}. The customer will read your reason.
        </p>
        <TextAreaField
          label="Reason"
          maxLength={255}
          placeholder="For example: liquid damage is not covered"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          error={error ?? undefined}
          hint={`${reason.length}/255`}
        />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={reject.isPending}>
            Cancel
          </Button>
          <Button type="submit" variant="danger" loading={reject.isPending}>
            Reject claim
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function CompleteModal({ claim, onClose }: { claim: Claim; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)

  const complete = useMutation({
    mutationFn: () => agentApi.complete(claim.id, note),
    onSuccess: () => {
      toast.success(`Repair completed for ${claim.productName}`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => setError(err instanceof ApiError ? (err.fieldErrors.resolutionNote ?? err.message) : errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    complete.mutate()
  }

  return (
    <Modal title="Mark repair as completed" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="text-sm text-slate-600">
          {claim.brand} {claim.productName} for {claim.customerName}.
        </p>
        <TextAreaField
          label="What was done? (optional)"
          maxLength={500}
          placeholder="For example: replaced the battery and tested for 24 hours"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          error={error ?? undefined}
        />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={complete.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={complete.isPending}>
            Mark completed
          </Button>
        </div>
      </form>
    </Modal>
  )
}
