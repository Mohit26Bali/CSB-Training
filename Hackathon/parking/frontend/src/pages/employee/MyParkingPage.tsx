import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { employeeApi } from '../../api/endpoints'
import type { ParkingRequest } from '../../api/types'
import { floorLabel } from '../../components/SlotMap'
import { useToast } from '../../components/Toast'
import { Button, ConfirmDialog, EmptyState, ErrorState, Field, formatDate, LoadingState, Modal, PageHeader, SelectField, StatusBadge, TextAreaField } from '../../components/ui'

export function MyParkingPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [asking, setAsking] = useState(false)
  const [cancelling, setCancelling] = useState<ParkingRequest | null>(null)
  const allocation = useQuery({ queryKey: ['allocation'], queryFn: employeeApi.allocation })
  const requests = useQuery({ queryKey: ['requests', 'mine'], queryFn: employeeApi.myRequests })

  const cancel = useMutation({
    mutationFn: (r: ParkingRequest) => employeeApi.cancel(r.id),
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

  const slot = allocation.data
  const pending = requests.data.find((r) => r.status === 'PENDING')

  return (
    <>
      <PageHeader title="My parking" subtitle="Your slot and the requests you have made." />

      {slot ? (
        <section className="mb-8 flex items-center gap-6 rounded-xl border border-emerald-200 bg-white p-6 shadow-sm">
          <div className="flex size-24 shrink-0 flex-col items-center justify-center rounded-xl bg-emerald-50 text-emerald-700">
            <span className="text-xs font-medium uppercase tracking-wide">Slot</span>
            <span className="text-2xl font-bold">{slot.slotCode}</span>
          </div>
          <div>
            <p className="text-lg font-semibold text-slate-900">{floorLabel(slot.floor)}</p>
            <p className="font-mono text-sm tracking-wider text-slate-700">{slot.vehicleNumber}</p>
            <p className="mt-1 text-sm text-slate-500">
              Allocated {formatDate(slot.allocatedAt)} by {slot.allocatedBy}
            </p>
          </div>
        </section>
      ) : pending ? (
        <section className="mb-8 rounded-xl border border-amber-200 bg-amber-50 p-5">
          <p className="font-semibold text-amber-900">Your request is in the queue</p>
          <p className="mt-1 text-sm text-amber-900">
            You are number {pending.queuePosition} for vehicle {pending.vehicleNumber}. The admin will pick a slot for you.
          </p>
        </section>
      ) : (
        <section className="mb-8 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="font-semibold text-slate-900">You do not have a parking slot yet</p>
          <p className="mt-1 text-sm text-slate-600">Send a request with your vehicle number and the admin will allocate one.</p>
          <div className="mt-3">
            <Button onClick={() => setAsking(true)}>Request a slot</Button>
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
                    <span className="font-mono text-sm text-slate-700">{r.vehicleNumber}</span>
                    <span className="text-sm text-slate-500">
                      {r.preferredFloor !== null ? floorLabel(r.preferredFloor) : 'any floor'} · sent {formatDate(r.createdAt)}
                    </span>
                  </div>
                  {r.note && <p className="mt-2 text-sm text-slate-600">“{r.note}”</p>}
                  {r.status === 'APPROVED' && r.slotCode && <p className="mt-2 text-sm font-medium text-emerald-700">Allocated slot {r.slotCode}</p>}
                  {r.status === 'REJECTED' && r.decisionReason && <p className="mt-2 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">Reason: {r.decisionReason}</p>}
                  {r.status === 'PENDING' && <p className="mt-2 text-sm text-amber-700">Queue position {r.queuePosition}</p>}
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
          message="You lose your place in the queue."
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
  const floors = useQuery({ queryKey: ['floors'], queryFn: employeeApi.floors })
  const [vehicle, setVehicle] = useState('')
  const [floor, setFloor] = useState<number | ''>('')
  const [note, setNote] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const ask = useMutation({
    mutationFn: () => employeeApi.ask(vehicle, floor, note),
    onSuccess: (r) => {
      toast.success(`Request sent. You are number ${r.queuePosition} in the queue`)
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
    <Modal title="Request a parking slot" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="Vehicle number" placeholder="MH12 AB 1234" maxLength={15} value={vehicle} onChange={(e) => setVehicle(e.target.value)} error={fieldErrors.vehicleNumber} />
        <SelectField label="Preferred floor (optional)" value={floor} onChange={(e) => setFloor(e.target.value === '' ? '' : Number(e.target.value))} error={fieldErrors.preferredFloor}>
          <option value="">Any floor</option>
          {(floors.data ?? []).map((f) => (
            <option key={f} value={f}>
              {floorLabel(f)}
            </option>
          ))}
        </SelectField>
        <TextAreaField label="Anything the admin should know? (optional)" maxLength={300} value={note} onChange={(e) => setNote(e.target.value)} error={fieldErrors.note} />
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
