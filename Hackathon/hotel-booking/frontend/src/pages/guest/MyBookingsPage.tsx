import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { guestApi } from '../../api/endpoints'
import type { Booking } from '../../api/types'
import { RoomPhoto } from '../../components/RoomPhoto'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, formatDate, formatDay, formatMoney, LoadingState, Modal, PageHeader, StatusBadge, statusLabel, TextAreaField } from '../../components/ui'

const phaseText = { UPCOMING: 'Upcoming', IN_STAY: 'You are staying', PAST: 'Completed' } as const

export function MyBookingsPage() {
  const list = useQuery({ queryKey: ['bookings', 'mine'], queryFn: () => guestApi.mine() })
  const [cancelling, setCancelling] = useState<Booking | null>(null)

  return (
    <>
      <PageHeader
        title="My bookings"
        subtitle="Requests, confirmed stays and your history."
        actions={
          <Link to="/rooms">
            <Button>Find a room</Button>
          </Link>
        }
      />
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="You have no bookings yet" action={<Link to="/rooms" className="font-medium text-brand-700 hover:underline">Find a room</Link>} />
      ) : (
        <ul className="space-y-4">
          {list.data.map((b) => (
            <li key={b.id} className={`flex overflow-hidden rounded-xl border bg-white shadow-sm ${b.status === 'CANCELLED' ? 'border-slate-200 opacity-80' : 'border-slate-200'}`}>
              <RoomPhoto type={b.roomType} id={b.roomId} className="hidden w-44 shrink-0 sm:block" />
              <div className="flex min-w-0 flex-1 flex-wrap items-start justify-between gap-3 p-4">
                <div className="min-w-0">
                  <p className="text-xs font-medium text-slate-500">
                    {b.number} · requested {formatDate(b.createdAt)}
                  </p>
                  <div className="mt-0.5 flex flex-wrap items-center gap-2">
                    <h2 className="font-semibold text-slate-900">
                      {statusLabel(b.roomType)} room {b.roomNumber}
                    </h2>
                    <StatusBadge status={b.status} />
                    {b.phase && b.status === 'CONFIRMED' && <span className="rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-semibold text-brand-800">{phaseText[b.phase]}</span>}
                  </div>
                  <p className="mt-1 text-sm text-slate-700">
                    {formatDay(b.checkIn)} to {formatDay(b.checkOut)} · {b.nights} night{b.nights === 1 ? '' : 's'} · {b.guests} guest{b.guests === 1 ? '' : 's'}
                  </p>
                  <p className="text-sm font-medium text-slate-900">{formatMoney(b.totalPrice)}</p>
                  {b.specialRequests && <p className="mt-1 text-xs text-slate-500">“{b.specialRequests}”</p>}
                  {b.status === 'PENDING' && <p className="mt-2 text-sm text-amber-700">Waiting for the hotel to confirm.</p>}
                  {b.status === 'CANCELLED' && (
                    <p className="mt-2 text-sm text-rose-700">
                      Cancelled by {b.cancelledBy === b.customerUsername ? 'you' : b.cancelledBy === 'system' ? 'the hotel (room taken)' : 'the hotel'}
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
    mutationFn: () => guestApi.cancel(booking.id, reason),
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
          {statusLabel(booking.roomType)} room {booking.roomNumber}, {formatDay(booking.checkIn)} to {formatDay(booking.checkOut)}. The room becomes available to other guests.
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
