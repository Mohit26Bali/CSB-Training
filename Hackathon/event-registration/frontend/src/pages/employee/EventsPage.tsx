import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { eventApi, registrationApi } from '../../api/endpoints'
import type { EventItem } from '../../api/types'
import { EventImage } from '../../components/EventImage'
import { useToast } from '../../components/Toast'
import { Button, ConfirmDialog, EmptyState, ErrorState, formatDate, LoadingState, PageHeader, StatusBadge } from '../../components/ui'
import { useDebounced } from '../../hooks'

export function SeatBar({ confirmed, capacity }: { confirmed: number; capacity: number }) {
  const pct = Math.min(100, Math.round((100 * confirmed) / capacity))
  const full = confirmed >= capacity
  return (
    <div role="img" aria-label={`${confirmed} of ${capacity} seats taken`} className="h-1.5 overflow-hidden rounded-full bg-slate-100">
      <div className={`h-full rounded-full ${full ? 'bg-rose-500' : pct >= 80 ? 'bg-amber-500' : 'bg-emerald-500'}`} style={{ width: `${pct}%` }} />
    </div>
  )
}

export function EventsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [leaving, setLeaving] = useState<EventItem | null>(null)
  const debouncedSearch = useDebounced(search)

  const events = useQuery({
    queryKey: ['events', debouncedSearch, category],
    queryFn: () => eventApi.upcoming(debouncedSearch || undefined, category || undefined),
    placeholderData: (previous) => previous,
  })
  const categories = useQuery({ queryKey: ['categories'], queryFn: eventApi.categories })

  const register = useMutation({
    mutationFn: ({ event, waitlist }: { event: EventItem; waitlist: boolean }) => registrationApi.register(event.id, waitlist),
    onSuccess: (r) =>
      toast.success(r.status === 'WAITLISTED' ? `You are #${r.waitlistPosition} on the waitlist for ${r.eventTitle}` : `You are registered for ${r.eventTitle}`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(), // seat counts may have changed under us
  })
  const cancel = useMutation({
    mutationFn: (event: EventItem) => registrationApi.cancel(event.myRegistrationId!),
    onSuccess: (r) => toast.success(`Cancelled your place at ${r.eventTitle}`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => {
      setLeaving(null)
      queryClient.invalidateQueries()
    },
  })
  const busyId = register.isPending ? register.variables?.event.id : undefined
  const filtered = search !== '' || category !== ''

  return (
    <>
      <PageHeader title="Upcoming events" subtitle="Pick an event and reserve your seat. Full events have a waitlist." />

      <div className="mb-6 flex flex-wrap gap-3">
        <div className="min-w-56 flex-1">
          <label htmlFor="search" className="sr-only">
            Search events
          </label>
          <input
            id="search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by title, venue or description"
            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label htmlFor="category" className="sr-only">
            Filter by category
          </label>
          <select id="category" value={category} onChange={(e) => setCategory(e.target.value)} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
            <option value="">All categories</option>
            {(categories.data ?? []).map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </div>
        {filtered && (
          <Button
            variant="ghost"
            onClick={() => {
              setSearch('')
              setCategory('')
            }}
          >
            Clear filters
          </Button>
        )}
      </div>

      {events.isPending ? (
        <LoadingState />
      ) : events.isError ? (
        <ErrorState message={errorMessage(events.error)} onRetry={() => events.refetch()} />
      ) : events.data.length === 0 ? (
        <EmptyState title={filtered ? 'No events match your filters' : 'No upcoming events yet'} hint={filtered ? 'Try a different search or category.' : 'Check back soon.'} />
      ) : (
        <ul className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {events.data.map((e) => {
            const full = e.spotsLeft === 0
            return (
              <li key={e.id} className="flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                <div className="relative">
                  <EventImage category={e.category} className="h-36 w-full" />
                  <span className="absolute left-3 top-3 rounded-full bg-black/55 px-2.5 py-0.5 text-xs font-medium text-white">{e.category}</span>
                </div>
                <div className="flex flex-1 flex-col p-5">
                  <h2 className="font-semibold text-slate-900">{e.title}</h2>
                  <p className="mt-1 text-sm text-slate-600">{formatDate(e.startsAt)}</p>
                  <p className="text-sm text-slate-500">{e.venue}</p>
                  <p className="mt-3 line-clamp-3 flex-1 text-sm text-slate-600">{e.description}</p>
                  <div className="mt-4">
                    <div className="mb-1.5 flex justify-between text-xs text-slate-500">
                      <span>{full ? 'Full' : `${e.spotsLeft} of ${e.capacity} seats left`}</span>
                      {e.waitlisted > 0 && <span>{e.waitlisted} on waitlist</span>}
                    </div>
                    <SeatBar confirmed={e.confirmed} capacity={e.capacity} />
                  </div>
                  <div className="mt-4 flex items-center justify-between gap-3">
                    {e.myStatus ? (
                      <>
                        <StatusBadge status={e.myStatus} />
                        <Button variant="secondary" small onClick={() => setLeaving(e)}>
                          {e.myStatus === 'WAITLISTED' ? 'Leave waitlist' : 'Cancel'}
                        </Button>
                      </>
                    ) : (
                      <>
                        <span className="text-xs text-slate-500">{full ? 'No seats left' : 'Seats available'}</span>
                        <Button
                          small
                          variant={full ? 'secondary' : 'primary'}
                          loading={busyId === e.id}
                          disabled={register.isPending}
                          onClick={() => register.mutate({ event: e, waitlist: full })}
                        >
                          {full ? 'Join waitlist' : 'Register'}
                        </Button>
                      </>
                    )}
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {leaving && (
        <ConfirmDialog
          title={leaving.myStatus === 'WAITLISTED' ? 'Leave the waitlist?' : 'Cancel your registration?'}
          message={`${leaving.title} on ${formatDate(leaving.startsAt)}. ${leaving.myStatus === 'CONFIRMED' ? 'Your seat goes to the next person waiting, if any.' : ''}`}
          confirmLabel={leaving.myStatus === 'WAITLISTED' ? 'Leave waitlist' : 'Cancel registration'}
          danger
          loading={cancel.isPending}
          onConfirm={() => cancel.mutate(leaving)}
          onCancel={() => setLeaving(null)}
        />
      )}
    </>
  )
}
