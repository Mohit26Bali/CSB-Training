import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { Booking, BookingStatus } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, formatDate, formatDay, formatMoney, LoadingState, Modal, PageHeader, StatusBadge, statusLabel, TextAreaField } from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = 'ALL' | BookingStatus
const tabs: Tab[] = ['PENDING', 'CONFIRMED', 'CANCELLED', 'ALL']

export function BookingsAdminPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('PENDING')
  const [search, setSearch] = useState('')
  const [cancelling, setCancelling] = useState<Booking | null>(null)
  const debouncedSearch = useDebounced(search)
  const list = useQuery({
    queryKey: ['admin', 'bookings', tab, debouncedSearch],
    queryFn: () => adminApi.bookings(tab === 'ALL' ? undefined : tab, undefined, debouncedSearch),
    placeholderData: (previous) => previous,
  })

  const confirm = useMutation({
    mutationFn: (b: Booking) => adminApi.confirm(b.id),
    onSuccess: (b) => toast.success(b.autoCancelled > 0 ? `${b.number} confirmed. ${b.autoCancelled} other request${b.autoCancelled === 1 ? '' : 's'} for those dates released` : `${b.number} confirmed`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(),
  })
  const busyId = confirm.isPending ? confirm.variables?.id : undefined

  return (
    <>
      <PageHeader title="Bookings" subtitle="Pending requests come first, soonest check-in on top." />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex gap-1 overflow-x-auto">
          {tabs.map((t) => (
            <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)} className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${tab === t ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
              {t === 'ALL' ? 'All' : statusLabel(t)}
            </button>
          ))}
        </div>
        <div className="ml-auto">
          <label htmlFor="search" className="sr-only">
            Search bookings
          </label>
          <input id="search" type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Guest, room or HB-4012" className="w-60 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" />
        </div>
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title={tab === 'PENDING' ? 'No requests waiting' : 'Nothing to show'} />
      ) : (
        <ul className="space-y-3">
          {list.data.map((b) => (
            <li key={b.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-xs font-semibold text-slate-500">{b.number}</span>
                    <StatusBadge status={b.status} />
                    {b.phase === 'IN_STAY' && <span className="rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-semibold text-brand-800">In the hotel</span>}
                  </div>
                  <h2 className="mt-1 font-semibold text-slate-900">
                    {b.customerName} · {statusLabel(b.roomType)} room {b.roomNumber}
                  </h2>
                  <p className="text-sm text-slate-700">
                    {formatDay(b.checkIn)} to {formatDay(b.checkOut)} · {b.nights} night{b.nights === 1 ? '' : 's'} · {b.guests} guest{b.guests === 1 ? '' : 's'} · {formatMoney(b.totalPrice)}
                  </p>
                  <p className="text-xs text-slate-500">
                    Requested {formatDate(b.createdAt)}
                    {b.confirmedBy && ` · confirmed by ${b.confirmedBy}`}
                  </p>
                  {b.specialRequests && <p className="mt-1 text-sm text-slate-600">“{b.specialRequests}”</p>}
                  {b.status === 'CANCELLED' && (
                    <p className="mt-1 text-sm text-rose-700">
                      Cancelled by {b.cancelledBy}
                      {b.cancelReason && `: ${b.cancelReason}`}
                    </p>
                  )}
                </div>
                <div className="flex gap-2">
                  {b.status === 'PENDING' && (
                    <Button small loading={busyId === b.id} disabled={confirm.isPending} onClick={() => confirm.mutate(b)}>
                      Confirm
                    </Button>
                  )}
                  {b.status !== 'CANCELLED' && b.phase !== 'PAST' && (
                    <Button variant="secondary" small onClick={() => setCancelling(b)}>
                      Cancel
                    </Button>
                  )}
                </div>
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
    mutationFn: () => adminApi.cancel(booking.id, reason),
    onSuccess: (b) => {
      toast.success(`${b.number} cancelled`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(err instanceof ApiError ? (err.fieldErrors.reason ?? err.message) : errorMessage(err))
      queryClient.invalidateQueries()
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    cancel.mutate()
  }

  return (
    <Modal title={`Cancel ${booking.number}`} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="text-sm text-slate-600">
          {booking.customerName}, room {booking.roomNumber}, {formatDay(booking.checkIn)} to {formatDay(booking.checkOut)}.
        </p>
        <TextAreaField label="Reason (the guest sees this)" rows={3} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} error={error ?? undefined} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={cancel.isPending}>
            Keep booking
          </Button>
          <Button type="submit" variant="danger" loading={cancel.isPending}>
            Cancel booking
          </Button>
        </div>
      </form>
    </Modal>
  )
}
