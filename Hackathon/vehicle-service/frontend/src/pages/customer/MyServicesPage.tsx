import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { customerApi } from '../../api/endpoints'
import type { Booking } from '../../api/types'
import { Progress } from '../../components/Progress'
import { useToast } from '../../components/Toast'
import { Plate, VehiclePhoto } from '../../components/VehiclePhoto'
import { Button, EmptyState, ErrorState, formatDate, formatDay, formatMoney, LoadingState, Modal, PageHeader, StatusBadge, TextAreaField } from '../../components/ui'

const scopes = [
  { key: 'upcoming', label: 'Upcoming' },
  { key: 'previous', label: 'Previous' },
] as const

export function MyServicesPage() {
  const [scope, setScope] = useState<'upcoming' | 'previous'>('upcoming')
  const list = useQuery({ queryKey: ['bookings', 'mine', scope], queryFn: () => customerApi.mine(scope), placeholderData: (previous) => previous })
  const [cancelling, setCancelling] = useState<Booking | null>(null)

  return (
    <>
      <PageHeader
        title="My services"
        subtitle="Follow each service from booked to done, and see what you paid."
        actions={
          <Link to="/book">
            <Button>Book a service</Button>
          </Link>
        }
      />
      <div role="tablist" aria-label="Upcoming or previous" className="mb-4 flex gap-1">
        {scopes.map((s) => (
          <button key={s.key} role="tab" aria-selected={scope === s.key} onClick={() => setScope(s.key)} className={`rounded-lg px-3 py-1.5 text-sm font-medium ${scope === s.key ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
            {s.label}
          </button>
        ))}
      </div>
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState
          title={scope === 'upcoming' ? 'Nothing booked' : 'No previous services'}
          hint={scope === 'upcoming' ? 'Book a service for one of your vehicles.' : 'Finished and cancelled services show up here.'}
          action={scope === 'upcoming' ? <Link to="/book" className="font-medium text-brand-700 hover:underline">Book a service</Link> : undefined}
        />
      ) : (
        <ul className="space-y-4">
          {list.data.map((b) => (
            <li key={b.id} className={`flex overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm ${b.status === 'CANCELLED' ? 'opacity-80' : ''}`}>
              <VehiclePhoto type={b.vehicleType} id={b.vehicleId} className="hidden w-40 shrink-0 sm:block" />
              <div className="flex min-w-0 flex-1 flex-wrap items-start justify-between gap-3 p-4">
                <div className="min-w-0 space-y-2">
                  <p className="text-xs font-medium text-slate-500">
                    {b.number} · booked {formatDate(b.createdAt)}
                  </p>
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="font-semibold text-slate-900">{b.serviceName}</h2>
                    <StatusBadge status={b.status} />
                  </div>
                  <p className="flex flex-wrap items-center gap-2 text-sm text-slate-700">
                    <Plate number={b.vehicleNumber} /> {b.vehicleLabel}
                  </p>
                  <p className="text-sm text-slate-700">
                    {formatDay(b.startDate)}
                    {b.days > 1 && ` to ${formatDay(b.endDate)}`}
                  </p>
                  <Progress status={b.status} />
                  <p className="text-sm text-slate-900">
                    {b.finalCost != null ? (
                      <>
                        Final bill <span className="font-semibold">{formatMoney(b.finalCost)}</span> <span className="text-xs text-slate-500">(estimate {formatMoney(b.estimatedCost)})</span>
                      </>
                    ) : (
                      <>
                        Estimated <span className="font-semibold">{formatMoney(b.estimatedCost)}</span>
                      </>
                    )}
                  </p>
                  {b.notes && <p className="text-xs text-slate-500">Your note: “{b.notes}”</p>}
                  {b.workNotes && <p className="text-xs text-slate-500">From the workshop: {b.workNotes}</p>}
                  {b.status === 'CANCELLED' && (
                    <p className="text-sm text-rose-700">
                      Cancelled by {b.cancelledBy === b.customerUsername ? 'you' : 'the workshop'}
                      {b.cancelReason && `: ${b.cancelReason}`}
                    </p>
                  )}
                </div>
                {b.canCancel && (
                  <Button variant="secondary" small onClick={() => setCancelling(b)}>
                    Cancel booking
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      {cancelling && <CancelModal booking={cancelling} onClose={() => setCancelling(null)} />}
    </>
  )
}

function CancelModal({ booking, onClose }: { booking: Booking; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const cancel = useMutation({
    mutationFn: () => customerApi.cancel(booking.id, reason),
    onSuccess: (b) => {
      toast.success(`${b.number} is cancelled`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(errorMessage(err))
      queryClient.invalidateQueries()
    },
  })

  return (
    <Modal title={`Cancel ${booking.number}?`} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          {booking.serviceName} for {booking.vehicleNumber} on {formatDay(booking.startDate)}. The bay becomes free for other customers.
        </p>
        <TextAreaField label="Reason (optional)" rows={2} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} error={error ?? undefined} />
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
