import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { studentApi } from '../../api/endpoints'
import { Occupancy, RoomImage } from '../../components/RoomImage'
import { Button, EmptyState, ErrorState, LoadingState, PageHeader, statusLabel } from '../../components/ui'

export function RoomsPage() {
  const [type, setType] = useState('')
  const types = useQuery({ queryKey: ['room-types'], queryFn: studentApi.types })
  const rooms = useQuery({ queryKey: ['rooms', type], queryFn: () => studentApi.rooms(type || undefined), placeholderData: (previous) => previous })

  return (
    <>
      <PageHeader
        title="Available rooms"
        subtitle="Rooms with at least one free bed. Send a request from My room and the warden picks the room."
        actions={
          <Link to="/my-room">
            <Button>Request a room</Button>
          </Link>
        }
      />

      <div className="mb-6 flex flex-wrap gap-2" role="tablist" aria-label="Filter by room type">
        {['', ...(types.data ?? [])].map((t) => (
          <button
            key={t || 'all'}
            role="tab"
            aria-selected={type === t}
            onClick={() => setType(t)}
            className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${type === t ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
          >
            {t === '' ? 'All types' : statusLabel(t)}
          </button>
        ))}
      </div>

      {rooms.isPending ? (
        <LoadingState />
      ) : rooms.isError ? (
        <ErrorState message={errorMessage(rooms.error)} onRetry={() => rooms.refetch()} />
      ) : rooms.data.length === 0 ? (
        <EmptyState title="No free rooms of this type" hint="You can still send a request; it goes on the waiting list." />
      ) : (
        <ul className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
          {rooms.data.map((r) => (
            <li key={r.id} className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
              <RoomImage type={r.type} className="h-32 w-full" />
              <div className="p-4">
                <div className="flex items-baseline justify-between">
                  <h2 className="font-semibold text-slate-900">Room {r.number}</h2>
                  <span className="text-sm text-slate-500">Floor {r.floor}</span>
                </div>
                <p className="text-sm text-slate-600">{statusLabel(r.type)}</p>
                <div className="mt-3">
                  <p className="mb-1 text-xs text-slate-500">
                    {r.bedsLeft} of {r.capacity} bed{r.capacity === 1 ? '' : 's'} free
                  </p>
                  <Occupancy occupied={r.occupied} capacity={r.capacity} />
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
