import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { ParkingRequest, RequestStatus, Slot } from '../../api/types'
import { floorLabel, SlotMap } from '../../components/SlotMap'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, formatDate, LoadingState, Modal, PageHeader, StatusBadge, statusLabel, TextAreaField } from '../../components/ui'

type Tab = 'ALL' | RequestStatus
const tabs: Tab[] = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'ALL']

export function AdminRequestsPage() {
  const [tab, setTab] = useState<Tab>('PENDING')
  const [approving, setApproving] = useState<ParkingRequest | null>(null)
  const [rejecting, setRejecting] = useState<ParkingRequest | null>(null)
  const list = useQuery({
    queryKey: ['admin', 'requests', tab],
    queryFn: () => adminApi.requests(tab === 'ALL' ? undefined : tab),
    placeholderData: (previous) => previous,
  })

  return (
    <>
      <PageHeader title="Parking requests" subtitle="Pending requests are the queue, oldest first." />
      <div role="tablist" aria-label="Filter by status" className="mb-4 flex gap-1 overflow-x-auto">
        {tabs.map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${tab === t ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
          >
            {t === 'ALL' ? 'All' : statusLabel(t)}
          </button>
        ))}
      </div>
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title={tab === 'PENDING' ? 'No requests waiting' : 'Nothing to show'} />
      ) : (
        <ul className="space-y-3">
          {list.data.map((r) => (
            <li key={r.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-medium text-slate-900">{r.employeeName}</p>
                    <StatusBadge status={r.status} />
                    {r.queuePosition && <span className="text-xs font-medium text-amber-700">#{r.queuePosition} in line</span>}
                  </div>
                  <p className="mt-0.5 text-sm text-slate-600">
                    <span className="font-mono">{r.vehicleNumber}</span> · {r.preferredFloor !== null ? `prefers ${floorLabel(r.preferredFloor).toLowerCase()}` : 'any floor'} · sent {formatDate(r.createdAt)}
                  </p>
                  {r.note && <p className="mt-2 text-sm text-slate-600">“{r.note}”</p>}
                  {r.status === 'APPROVED' && <p className="mt-2 text-sm text-emerald-700">Slot {r.slotCode}, by {r.decidedBy}</p>}
                  {r.status === 'REJECTED' && <p className="mt-2 text-sm text-rose-700">Rejected by {r.decidedBy}: {r.decisionReason}</p>}
                </div>
                {r.status === 'PENDING' && (
                  <div className="flex gap-2">
                    <Button small onClick={() => setApproving(r)}>
                      Allocate a slot
                    </Button>
                    <Button variant="secondary" small onClick={() => setRejecting(r)}>
                      Reject
                    </Button>
                  </div>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      {approving && <ApproveModal request={approving} onClose={() => setApproving(null)} />}
      {rejecting && <RejectModal request={rejecting} onClose={() => setRejecting(null)} />}
    </>
  )
}

function ApproveModal({ request, onClose }: { request: ParkingRequest; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const slots = useQuery({ queryKey: ['admin', 'slots', 'all'], queryFn: () => adminApi.slots() })
  const free = (slots.data ?? []).filter((s) => s.status === 'AVAILABLE' && !s.occupied)
  const [picked, setPicked] = useState<Slot | null>(null)
  const [error, setError] = useState<string | null>(null)

  const approve = useMutation({
    mutationFn: () => adminApi.approve(request.id, picked!.id),
    onSuccess: (r) => {
      toast.success(`${r.employeeName} now has slot ${r.slotCode}`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(err instanceof ApiError ? (err.fieldErrors.slotId ?? err.message) : errorMessage(err))
      setPicked(null)
      queryClient.invalidateQueries() // the slot may have just been taken
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    approve.mutate()
  }

  return (
    <Modal title={`Allocate a slot to ${request.employeeName}`} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="text-sm text-slate-600">
          Vehicle <span className="font-mono">{request.vehicleNumber}</span>
          {request.preferredFloor !== null && `, prefers ${floorLabel(request.preferredFloor).toLowerCase()}`}. Pick a green slot.
        </p>
        {slots.isPending ? <LoadingState /> : free.length === 0 ? <p className="text-sm text-amber-700">No free slot. Free one up or add a slot first.</p> : <SlotMap slots={free} selectedId={picked?.id} onPick={setPicked} />}
        {error && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={approve.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={approve.isPending} disabled={!picked}>
            {picked ? `Allocate ${picked.code}` : 'Allocate'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function RejectModal({ request, onClose }: { request: ParkingRequest; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const reject = useMutation({
    mutationFn: () => adminApi.reject(request.id, reason),
    onSuccess: (r) => {
      toast.success(`Rejected ${r.employeeName}'s request`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => setError(err instanceof ApiError ? (err.fieldErrors.reason ?? err.message) : errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    reject.mutate()
  }

  return (
    <Modal title={`Reject ${request.employeeName}'s request`} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <TextAreaField label="Reason (the employee sees this)" maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} error={error ?? undefined} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={reject.isPending}>
            Cancel
          </Button>
          <Button type="submit" variant="danger" loading={reject.isPending}>
            Reject
          </Button>
        </div>
      </form>
    </Modal>
  )
}
