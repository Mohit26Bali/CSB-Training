import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, errorMessage } from '../../api/client'
import { guestApi } from '../../api/endpoints'
import type { Room } from '../../api/types'
import { RoomPhoto } from '../../components/RoomPhoto'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, Field, formatDay, formatMoney, LoadingState, Modal, PageHeader, SelectField, statusLabel, TextAreaField } from '../../components/ui'

const iso = (offset: number) => {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function SearchPage() {
  const [checkIn, setCheckIn] = useState(iso(7))
  const [checkOut, setCheckOut] = useState(iso(10))
  const [guests, setGuests] = useState('2')
  const [type, setType] = useState('')
  const [booking, setBooking] = useState<Room | null>(null)
  const types = useQuery({ queryKey: ['room-types'], queryFn: guestApi.types })

  const rooms = useQuery({
    queryKey: ['rooms', 'available', checkIn, checkOut, type, guests],
    queryFn: () => guestApi.available(checkIn, checkOut, type, Number(guests) || undefined),
    enabled: checkIn !== '' && checkOut !== '',
    placeholderData: (previous) => previous,
    retry: false,
  })
  const nights = rooms.data?.[0]?.nights

  return (
    <>
      <PageHeader title="Find a room" subtitle="Choose your dates and see what is free, with the price for the whole stay." />

      <div className="mb-6 grid gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Check-in" type="date" min={iso(0)} value={checkIn} onChange={(e) => setCheckIn(e.target.value)} />
        <Field label="Check-out" type="date" min={checkIn || iso(1)} value={checkOut} onChange={(e) => setCheckOut(e.target.value)} />
        <Field label="Guests" type="number" min={1} max={10} value={guests} onChange={(e) => setGuests(e.target.value)} />
        <SelectField label="Room type" value={type} onChange={(e) => setType(e.target.value)}>
          <option value="">Any type</option>
          {(types.data ?? []).map((t) => (
            <option key={t} value={t}>
              {statusLabel(t)}
            </option>
          ))}
        </SelectField>
      </div>

      {rooms.isPending ? (
        <LoadingState label="Looking for rooms…" />
      ) : rooms.isError ? (
        <ErrorState message={errorMessage(rooms.error)} onRetry={() => rooms.refetch()} />
      ) : rooms.data.length === 0 ? (
        <EmptyState title="No rooms are free for those dates" hint="Try other dates, fewer guests or another room type." />
      ) : (
        <>
          <p className="mb-4 text-sm text-slate-600">
            {rooms.data.length} room{rooms.data.length === 1 ? '' : 's'} free · {formatDay(checkIn)} to {formatDay(checkOut)} · {nights} night{nights === 1 ? '' : 's'}
          </p>
          <ul className="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
            {rooms.data.map((r) => (
              <li key={r.id} className="flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                <div className="relative">
                  <RoomPhoto type={r.type} id={r.id} className="h-48 w-full" />
                  <span className="absolute left-3 top-3 rounded-full bg-black/55 px-2.5 py-0.5 text-xs font-medium text-white">{statusLabel(r.type)}</span>
                </div>
                <div className="flex flex-1 flex-col p-5">
                  <div className="flex items-baseline justify-between">
                    <h2 className="font-semibold text-slate-900">Room {r.number}</h2>
                    <span className="text-xs text-slate-500">
                      Floor {r.floor} · up to {r.capacity} guests
                    </span>
                  </div>
                  {r.description && <p className="mt-2 line-clamp-2 text-sm text-slate-600">{r.description}</p>}
                  <ul className="mt-3 flex flex-wrap gap-1.5">
                    {r.amenities.slice(0, 5).map((a) => (
                      <li key={a} className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                        {a}
                      </li>
                    ))}
                  </ul>
                  <div className="mt-auto flex items-end justify-between pt-4">
                    <div>
                      <p className="text-lg font-semibold text-slate-900">{formatMoney(r.totalPrice ?? r.pricePerNight)}</p>
                      <p className="text-xs text-slate-500">
                        {formatMoney(r.pricePerNight)} a night · {r.nights} night{r.nights === 1 ? '' : 's'}
                      </p>
                    </div>
                    <Button small onClick={() => setBooking(r)}>
                      Book this room
                    </Button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </>
      )}

      {booking && <BookModal room={booking} checkIn={checkIn} checkOut={checkOut} guests={Number(guests) || 1} onClose={() => setBooking(null)} />}
    </>
  )
}

function BookModal({ room, checkIn, checkOut, guests, onClose }: { room: Room; checkIn: string; checkOut: string; guests: number; onClose: () => void }) {
  const toast = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [count, setCount] = useState(String(Math.min(guests, room.capacity)))
  const [requests, setRequests] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const book = useMutation({
    mutationFn: () => guestApi.book({ roomId: room.id, checkIn, checkOut, guests: Number(count), specialRequests: requests }),
    onSuccess: (b) => {
      toast.success(`Request ${b.number} sent. The hotel will confirm it`)
      queryClient.invalidateQueries()
      onClose()
      navigate('/bookings')
    },
    onError: (err) => {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(err.fieldErrors)
        setFormError(null)
      } else {
        setFieldErrors({})
        setFormError(errorMessage(err))
      }
      queryClient.invalidateQueries() // the room may have just been taken
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    book.mutate()
  }

  return (
    <Modal title={`Book room ${room.number}`} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <RoomPhoto type={room.type} id={room.id} className="h-36 w-full rounded-lg" />
        <dl className="grid grid-cols-2 gap-3 rounded-lg bg-slate-50 p-3 text-sm">
          <div>
            <dt className="text-xs uppercase tracking-wide text-slate-500">Check-in</dt>
            <dd className="font-medium text-slate-900">{formatDay(checkIn)}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-slate-500">Check-out</dt>
            <dd className="font-medium text-slate-900">{formatDay(checkOut)}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-slate-500">Stay</dt>
            <dd className="text-slate-900">
              {room.nights} night{room.nights === 1 ? '' : 's'} at {formatMoney(room.pricePerNight)}
            </dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-slate-500">Total</dt>
            <dd className="text-lg font-semibold text-slate-900">{formatMoney(room.totalPrice ?? 0)}</dd>
          </div>
        </dl>
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label={`Guests (this room sleeps up to ${room.capacity})`} type="number" min={1} max={room.capacity} value={count} onChange={(e) => setCount(e.target.value)} error={fieldErrors.guests} />
        <TextAreaField label="Special requests (optional)" rows={2} maxLength={300} value={requests} onChange={(e) => setRequests(e.target.value)} error={fieldErrors.specialRequests} />
        {(fieldErrors.checkIn || fieldErrors.checkOut) && <p className="text-sm text-rose-700">{fieldErrors.checkIn ?? fieldErrors.checkOut}</p>}
        <p className="text-xs text-slate-500">The hotel confirms your request, usually within a few hours. You can cancel free of charge until the day before check-in.</p>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={book.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={book.isPending}>
            Send booking request
          </Button>
        </div>
      </form>
    </Modal>
  )
}
