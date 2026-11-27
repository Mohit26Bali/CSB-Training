import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { Booking, BookingStatus } from '../../api/types'
import { Progress } from '../../components/Progress'
import { useToast } from '../../components/Toast'
import { Plate } from '../../components/VehiclePhoto'
import { useDebounced } from '../../hooks'
import { Button, EmptyState, ErrorState, Field, formatDate, formatDay, formatMoney, LoadingState, Modal, PageHeader, StatusBadge, TextAreaField } from '../../components/ui'

const tabs: { key: BookingStatus | ''; label: string }[] = [
  { key: '', label: 'All' },
  { key: 'BOOKED', label: 'Booked' },
  { key: 'IN_SERVICE', label: 'In service' },
  { key: 'COMPLETED', label: 'Completed' },
  { key: 'CANCELLED', label: 'Cancelled' },
]

type Action = { kind: 'complete' | 'cancel'; booking: Booking }

export function SchedulePage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<BookingStatus | ''>('')
  const [date, setDate] = useState('')
  const [search, setSearch] = useState('')
  const q = useDebounced(search)
  const [action, setAction] = useState<Action | null>(null)
  const list = useQuery({
    queryKey: ['admin', 'bookings', status, date, q],
    queryFn: () => adminApi.bookings(status || undefined, date, q),
    placeholderData: (previous) => previous,
  })

  const start = useMutation({
    mutationFn: (b: Booking) => adminApi.start(b.id),
    onSuccess: (b) => {
      toast.success(`${b.vehicleNumber} is now in service`)
      queryClient.invalidateQueries()
    },
    onError: (err) => {
      toast.error(errorMessage(err))
      queryClient.invalidateQueries()
    },
  })

  return (
    <>
      <PageHeader title="Schedule" subtitle="Every booking, with the next step for each one." />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex flex-wrap gap-1">
          {tabs.map((t) => (
            <button key={t.key || 'all'} role="tab" aria-selected={status === t.key} onClick={() => setStatus(t.key)} className={`rounded-lg px-3 py-1.5 text-sm font-medium ${status === t.key ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
              {t.label}
            </button>
          ))}
        </div>
        <div className="flex flex-1 flex-wrap items-end justify-end gap-3">
          <div className="w-44">
            <Field label="On day" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <div className="w-56">
            <Field label="Search" placeholder="Number, owner, service…" value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
          {(date || search) && (
            <Button
              variant="secondary"
              small
              onClick={() => {
                setDate('')
                setSearch('')
              }}
            >
              Clear
            </Button>
          )}
        </div>
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="No bookings match" hint="Try another status, day or search." />
      ) : (
        <ul className="space-y-3">
          {list.data.map((b) => (
            <li key={b.id} className={`rounded-xl border border-slate-200 bg-white p-4 shadow-sm ${b.status === 'CANCELLED' ? 'opacity-75' : ''}`}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 space-y-1.5">
                  <p className="text-xs font-medium text-slate-500">
                    {b.number} · booked {formatDate(b.createdAt)}
                  </p>
                  <div className="flex flex-wrap items-center gap-2">
                    <Plate number={b.vehicleNumber} />
                    <h2 className="font-semibold text-slate-900">{b.vehicleLabel}</h2>
                    <StatusBadge status={b.status} />
                  </div>
                  <p className="text-sm text-slate-700">
                    {b.serviceName} · {formatDay(b.startDate)}
                    {b.days > 1 && ` to ${formatDay(b.endDate)}`} · {b.customerName}
                  </p>
                  <Progress status={b.status} />
                  <p className="text-sm text-slate-900">
                    {b.finalCost != null ? (
                      <>
                        Final <span className="font-semibold">{formatMoney(b.finalCost)}</span> <span className="text-xs text-slate-500">(estimate {formatMoney(b.estimatedCost)})</span>
                      </>
                    ) : (
                      <>
                        Estimate <span className="font-semibold">{formatMoney(b.estimatedCost)}</span>
                      </>
                    )}
                  </p>
                  {b.notes && <p className="text-xs text-slate-500">Customer note: “{b.notes}”</p>}
                  {b.workNotes && <p className="text-xs text-slate-500">Work notes: {b.workNotes}</p>}
                  {b.status === 'CANCELLED' && (
                    <p className="text-sm text-rose-700">
                      Cancelled by {b.cancelledBy === b.customerUsername ? 'the customer' : b.cancelledBy}
                      {b.cancelReason && `: ${b.cancelReason}`}
                    </p>
                  )}
                </div>
                <div className="flex flex-col items-end gap-2">
                  {b.status === 'BOOKED' && (
                    <div className="flex gap-2">
                      <Button small disabled={!b.canStart} loading={start.isPending && start.variables?.id === b.id} onClick={() => start.mutate(b)}>
                        Start service
                      </Button>
                      <Button small variant="secondary" onClick={() => setAction({ kind: 'cancel', booking: b })}>
                        Cancel
                      </Button>
                    </div>
                  )}
                  {b.status === 'BOOKED' && !b.canStart && <p className="text-xs text-slate-500">Can start on {formatDay(b.startDate)}</p>}
                  {b.status === 'IN_SERVICE' && (
                    <Button small onClick={() => setAction({ kind: 'complete', booking: b })}>
                      Mark completed
                    </Button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
      {action?.kind === 'complete' && <CompleteModal booking={action.booking} onClose={() => setAction(null)} />}
      {action?.kind === 'cancel' && <CancelModal booking={action.booking} onClose={() => setAction(null)} />}
    </>
  )
}

function CompleteModal({ booking, onClose }: { booking: Booking; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [cost, setCost] = useState('')
  const [notes, setNotes] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const complete = useMutation({
    mutationFn: () => adminApi.complete(booking.id, cost, notes),
    onSuccess: (b) => {
      toast.success(`${b.vehicleNumber} is ready: ${formatMoney(b.finalCost ?? 0)}`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(err.fieldErrors)
        setFormError(null)
      } else {
        setFormError(errorMessage(err))
      }
    },
  })

  return (
    <Modal title={`Complete ${booking.number}`} onClose={onClose}>
      <div className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <p className="text-sm text-slate-600">
          {booking.serviceName} for {booking.vehicleNumber}. Estimate {formatMoney(booking.estimatedCost)}.
        </p>
        <Field label="Final cost (₹)" type="number" min={0} placeholder={String(booking.estimatedCost)} value={cost} onChange={(e) => setCost(e.target.value)} error={fieldErrors.finalCost} hint="Leave empty to bill the estimate" />
        <TextAreaField label="Work notes (optional)" rows={3} maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} error={fieldErrors.workNotes} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={complete.isPending}>
            Not yet
          </Button>
          <Button loading={complete.isPending} onClick={() => complete.mutate()}>
            Mark completed
          </Button>
        </div>
      </div>
    </Modal>
  )
}

function CancelModal({ booking, onClose }: { booking: Booking; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const cancel = useMutation({
    mutationFn: () => adminApi.cancel(booking.id, reason),
    onSuccess: (b) => {
      toast.success(`${b.number} is cancelled`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => setError(err instanceof ApiError && err.fieldErrors.reason ? err.fieldErrors.reason : errorMessage(err)),
  })

  return (
    <Modal title={`Cancel ${booking.number}?`} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          {booking.customerName} will see your reason. {booking.serviceName} for {booking.vehicleNumber} on {formatDay(booking.startDate)}.
        </p>
        <TextAreaField label="Reason" rows={2} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} error={error ?? undefined} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={cancel.isPending}>
            Keep booking
          </Button>
          <Button variant="danger" loading={cancel.isPending} onClick={() => cancel.mutate()}>
            Cancel booking
          </Button>
        </div>
      </div>
    </Modal>
  )
}
