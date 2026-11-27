import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi, studentApi } from '../../api/endpoints'
import type { Room, RoomInput } from '../../api/types'
import { Occupancy, RoomImage } from '../../components/RoomImage'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, SelectField, statusLabel } from '../../components/ui'

export function AdminRoomsPage() {
  const [type, setType] = useState('')
  const [editing, setEditing] = useState<Room | 'new' | null>(null)
  const types = useQuery({ queryKey: ['room-types'], queryFn: studentApi.types })
  const rooms = useQuery({ queryKey: ['admin', 'rooms', type], queryFn: () => adminApi.rooms(type || undefined), placeholderData: (previous) => previous })

  return (
    <>
      <PageHeader title="Rooms" subtitle="Every room with who lives in it." actions={<Button onClick={() => setEditing('new')}>New room</Button>} />
      <div role="tablist" aria-label="Filter by room type" className="mb-4 flex gap-1 overflow-x-auto">
        {['', ...(types.data ?? [])].map((t) => (
          <button
            key={t || 'all'}
            role="tab"
            aria-selected={type === t}
            onClick={() => setType(t)}
            className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${type === t ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
          >
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
              <RoomImage type={r.type} className="h-24 w-full" />
              <div className="p-4">
                <div className="flex items-baseline justify-between">
                  <h2 className="font-semibold text-slate-900">Room {r.number}</h2>
                  <span className="text-sm text-slate-500">Floor {r.floor}</span>
                </div>
                <p className="text-sm text-slate-600">
                  {statusLabel(r.type)}
                  {!r.active && <span className="ml-2 font-medium text-amber-700">Closed for maintenance</span>}
                </p>
                <div className="mt-3">
                  <p className="mb-1 text-xs text-slate-500">
                    {r.occupied}/{r.capacity} beds taken
                  </p>
                  <Occupancy occupied={r.occupied} capacity={r.capacity} />
                </div>
                {r.occupants.length > 0 && <p className="mt-2 text-xs text-slate-500">{r.occupants.join(', ')}</p>}
                <div className="mt-3 flex justify-end">
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
  const types = useQuery({ queryKey: ['room-types'], queryFn: studentApi.types })
  const [form, setForm] = useState<RoomInput>({
    number: room?.number ?? '',
    floor: room?.floor ?? '',
    type: room?.type ?? '',
    capacity: room?.capacity ?? '',
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
        <div className="grid grid-cols-2 gap-4">
          <Field label="Room number" placeholder="A-101" maxLength={20} value={form.number} onChange={set('number')} error={fieldErrors.number} />
          <Field label="Floor" type="number" min={0} max={30} value={form.floor} onChange={set('floor')} error={fieldErrors.floor} />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <SelectField label="Type" value={form.type} onChange={set('type')} error={fieldErrors.type}>
            <option value="">Choose…</option>
            {(types.data ?? []).map((t) => (
              <option key={t} value={t}>
                {statusLabel(t)}
              </option>
            ))}
          </SelectField>
          <Field label="Capacity (beds)" type="number" min={1} max={12} value={form.capacity} onChange={set('capacity')} error={fieldErrors.capacity} />
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={form.active} onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))} />
          Open for allocation (untick while it is under maintenance)
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
