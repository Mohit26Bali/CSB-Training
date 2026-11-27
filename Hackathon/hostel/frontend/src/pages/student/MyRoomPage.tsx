import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { studentApi } from '../../api/endpoints'
import type { RoomRequest } from '../../api/types'
import { RoomImage } from '../../components/RoomImage'
import { useToast } from '../../components/Toast'
import {
  Button,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  formatDate,
  LoadingState,
  Modal,
  PageHeader,
  SelectField,
  StatusBadge,
  statusLabel,
  TextAreaField,
} from '../../components/ui'

export function MyRoomPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [asking, setAsking] = useState(false)
  const [cancelling, setCancelling] = useState<RoomRequest | null>(null)

  const allocation = useQuery({ queryKey: ['allocation'], queryFn: studentApi.allocation })
  const requests = useQuery({ queryKey: ['requests', 'mine'], queryFn: studentApi.myRequests })

  const cancel = useMutation({
    mutationFn: (r: RoomRequest) => studentApi.cancel(r.id),
    onSuccess: () => toast.success('Your request is cancelled'),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => {
      setCancelling(null)
      queryClient.invalidateQueries()
    },
  })

  if (allocation.isPending || requests.isPending) return <LoadingState />
  if (allocation.isError) return <ErrorState message={errorMessage(allocation.error)} onRetry={() => allocation.refetch()} />
  if (requests.isError) return <ErrorState message={errorMessage(requests.error)} onRetry={() => requests.refetch()} />

  const room = allocation.data
  const pending = requests.data.find((r) => r.status === 'PENDING')

  return (
    <>
      <PageHeader title="My room" subtitle="Your current room and the requests you have made." />

      {room ? (
        <section className="mb-8 flex overflow-hidden rounded-xl border border-emerald-200 bg-white shadow-sm">
          <RoomImage type={room.roomType} className="hidden w-48 shrink-0 sm:block" />
          <div className="p-5">
            <p className="text-xs font-semibold uppercase tracking-wide text-emerald-700">You live in</p>
            <h2 className="mt-1 text-2xl font-semibold text-slate-900">Room {room.roomNumber}</h2>
            <p className="text-slate-600">
              {statusLabel(room.roomType)} room, floor {room.floor}
            </p>
            <p className="mt-2 text-sm text-slate-500">
              Allocated {formatDate(room.allocatedAt)} by {room.allocatedBy}
            </p>
          </div>
        </section>
      ) : pending ? (
        <section className="mb-8 rounded-xl border border-amber-200 bg-amber-50 p-5">
          <p className="font-semibold text-amber-900">Your request is waiting</p>
          <p className="mt-1 text-sm text-amber-900">
            You are number {pending.queuePosition} on the waiting list. The warden will pick a room for you.
          </p>
        </section>
      ) : (
        <section className="mb-8 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="font-semibold text-slate-900">You do not have a room yet</p>
          <p className="mt-1 text-sm text-slate-600">Send a request and the warden will allocate one when a bed is free.</p>
          <div className="mt-3">
            <Button onClick={() => setAsking(true)}>Request a room</Button>
          </div>
        </section>
      )}

      <h2 className="mb-3 text-lg font-semibold text-slate-900">My requests</h2>
      {requests.data.length === 0 ? (
        <EmptyState title="No requests yet" />
      ) : (
        <ul className="space-y-3">
          {requests.data.map((r) => (
            <li key={r.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <StatusBadge status={r.status} />
                    <span className="text-sm text-slate-600">
                      {r.preferredType ? `Prefers ${statusLabel(r.preferredType).toLowerCase()}` : 'No preferred type'} · sent {formatDate(r.createdAt)}
                    </span>
                  </div>
                  {r.note && <p className="mt-2 text-sm text-slate-600">“{r.note}”</p>}
                  {r.status === 'APPROVED' && r.roomNumber && <p className="mt-2 text-sm font-medium text-emerald-700">Allocated room {r.roomNumber}</p>}
                  {r.status === 'REJECTED' && r.decisionReason && (
                    <p className="mt-2 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">Reason: {r.decisionReason}</p>
                  )}
                  {r.status === 'PENDING' && <p className="mt-2 text-sm text-amber-700">Waiting list position {r.queuePosition}</p>}
                </div>
                {r.status === 'PENDING' && (
                  <Button variant="secondary" small onClick={() => setCancelling(r)}>
                    Cancel request
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {asking && <AskModal onClose={() => setAsking(false)} />}
      {cancelling && (
        <ConfirmDialog
          title="Cancel this request?"
          message="You lose your place on the waiting list."
          confirmLabel="Cancel request"
          danger
          loading={cancel.isPending}
          onConfirm={() => cancel.mutate(cancelling)}
          onCancel={() => setCancelling(null)}
        />
      )}
    </>
  )
}

function AskModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const types = useQuery({ queryKey: ['room-types'], queryFn: studentApi.types })
  const [preferred, setPreferred] = useState('')
  const [note, setNote] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const ask = useMutation({
    mutationFn: () => studentApi.ask(preferred, note),
    onSuccess: (r) => {
      toast.success(`Request sent. You are number ${r.queuePosition} on the waiting list`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(err.fieldErrors)
        setFormError(null)
      } else {
        setFieldErrors({})
        setFormError(errorMessage(err))
      }
      queryClient.invalidateQueries()
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    ask.mutate()
  }

  return (
    <Modal title="Request a room" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <SelectField label="Preferred room type (optional)" value={preferred} onChange={(e) => setPreferred(e.target.value)} error={fieldErrors.type}>
          <option value="">No preference</option>
          {(types.data ?? []).map((t) => (
            <option key={t} value={t}>
              {statusLabel(t)}
            </option>
          ))}
        </SelectField>
        <TextAreaField label="Anything the warden should know? (optional)" maxLength={300} value={note} onChange={(e) => setNote(e.target.value)} error={fieldErrors.note} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={ask.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={ask.isPending}>
            Send request
          </Button>
        </div>
      </form>
    </Modal>
  )
}
