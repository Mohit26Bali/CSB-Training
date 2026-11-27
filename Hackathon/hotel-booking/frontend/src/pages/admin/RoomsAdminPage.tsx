import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi, guestApi } from '../../api/endpoints'
import type { Room, RoomInput } from '../../api/types'
import { RoomPhoto } from '../../components/RoomPhoto'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, Field, formatMoney, LoadingState, Modal, PageHeader, SelectField, StatusBadge, statusLabel, TextAreaField } from '../../components/ui'

export function RoomsAdminPage() {
  const [type, setType] = useState('')
  const [editing, setEditing] = useState<Room | 'new' | null>(null)
  const types = useQuery({ queryKey: ['room-types'], queryFn: guestApi.types })
  const rooms = useQuery({ queryKey: ['admin', 'rooms', type], queryFn: () => adminApi.rooms(type), placeholderData: (previous) => previous })

  return (
    <>
      <PageHeader title="Rooms" subtitle="The rooms, their prices and whether they are open for booking." actions={<Button onClick={() => setEditing('new')}>New room</Button>} />
      <div role="tablist" aria-label="Filter by room type" className="mb-4 flex gap-1 overflow-x-auto">
        {['', ...(types.data ?? [])].map((t) => (
          <button key={t || 'all'} role="tab" aria-selected={type === t} onClick={() => setType(t)} className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${type === t ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
            {t === '' ? 'All' : statusLabel(t)}
          </button>
        ))}
      </div>
      {rooms.isPending ? (
        <LoadingState />
      ) : rooms.isError ? (
        <ErrorState message={errorMessage(rooms.error)} onRetry={() => rooms.refetch()} />
      ) : rooms.data.length === 0 ? (
        <EmptyState title="No rooms here" action={<Button onClick={() => setEditing('new')}>Add a room</Button>} />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {rooms.data.map((r) => (
            <li key={r.id} className={`overflow-hidden rounded-xl border bg-white shadow-sm ${r.active ? 'border-slate-200' : 'border-amber-300'}`}>
              <RoomPhoto type={r.type} id={r.id} className="h-32 w-full" />
              <div className="p-4">
                <div className="flex items-baseline justify-between">
                  <h2 className="font-semibold text-slate-900">Room {r.number}</h2>
                  <span className="text-sm font-medium text-slate-900">{formatMoney(r.pricePerNight)}</span>
                </div>
                <p className="text-sm text-slate-600">
                  {statusLabel(r.type)} · floor {r.floor} · up to {r.capacity} guests
                </p>
                <div className="mt-3 flex items-center justify-between">
                  <StatusBadge status={r.active ? 'ACTIVE' : 'CLOSED'} />
                  <Button variant="secondary" small onClick={() => setEditing(r)}>
                    Edit
                  </Button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
      {editing && <RoomFormModal room={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function RoomFormModal({ room, onClose }: { room: Room | null; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const types = useQuery({ queryKey: ['room-types'], queryFn: guestApi.types })
  const [form, setForm] = useState<RoomInput>({
    number: room?.number ?? '',
    type: room?.type ?? '',
    floor: room?.floor ?? '',
    pricePerNight: room?.pricePerNight ?? '',
    capacity: room?.capacity ?? '',
    description: room?.description ?? '',
    amenities: room?.amenities.join(', ') ?? '',
    active: room?.active ?? true,
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => (room ? adminApi.updateRoom(room.id, form) : adminApi.createRoom(form)),
    onSuccess: (saved) => {
      toast.success(room ? `Saved room ${saved.number}` : `Added room ${saved.number}`)
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
    },
  })
  const set = (key: keyof RoomInput) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    save.mutate()
  }

  return (
    <Modal title={room ? `Edit room ${room.number}` : 'New room'} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <div className="grid grid-cols-3 gap-4">
          <Field label="Number" maxLength={10} value={form.number} onChange={set('number')} error={fieldErrors.number} />
          <SelectField label="Type" value={form.type} onChange={set('type')} error={fieldErrors.type}>
            <option value="">Choose…</option>
            {(types.data ?? []).map((t) => (
              <option key={t} value={t}>
                {statusLabel(t)}
              </option>
            ))}
          </SelectField>
          <Field label="Floor" type="number" min={0} value={form.floor} onChange={set('floor')} error={fieldErrors.floor} />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Price per night (₹)" type="number" min={100} value={form.pricePerNight} onChange={set('pricePerNight')} error={fieldErrors.pricePerNight} />
          <Field label="Sleeps up to" type="number" min={1} max={10} value={form.capacity} onChange={set('capacity')} error={fieldErrors.capacity} />
        </div>
        <TextAreaField label="Description" rows={2} maxLength={500} value={form.description} onChange={set('description')} error={fieldErrors.description} />
        <Field label="Amenities" placeholder="Wi-Fi, Air conditioning, Minibar" value={form.amenities} onChange={set('amenities')} error={fieldErrors.amenities} hint="Separate with commas" />
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={form.active} onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))} />
          Open for booking (untick while it is closed for renovation)
        </label>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {room ? 'Save changes' : 'Add room'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
