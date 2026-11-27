import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { registrationApi } from '../../api/endpoints'
import type { Registration } from '../../api/types'
import { EventImage } from '../../components/EventImage'
import { useToast } from '../../components/Toast'
import { Button, ConfirmDialog, EmptyState, ErrorState, formatDate, LoadingState, PageHeader, StatusBadge } from '../../components/ui'

export function MyRegistrationsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [leaving, setLeaving] = useState<Registration | null>(null)
  const list = useQuery({ queryKey: ['registrations', 'mine'], queryFn: registrationApi.mine })

  const cancel = useMutation({
    mutationFn: (r: Registration) => registrationApi.cancel(r.id),
    onSuccess: (r) => toast.success(`Cancelled your place at ${r.eventTitle}`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => {
      setLeaving(null)
      queryClient.invalidateQueries()
    },
  })

  if (list.isPending) return <LoadingState />
  if (list.isError) return <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />

  const now = Date.now()
  const started = (r: Registration) => new Date(r.startsAt).getTime() <= now

  return (
    <>
      <PageHeader title="My registrations" subtitle="Your seats and waitlist spots, soonest first." />
      {list.data.length === 0 ? (
        <EmptyState
          title="You have not registered for anything yet"
          action={
            <Link to="/events" className="font-medium text-brand-700 hover:underline">
              Browse upcoming events
            </Link>
          }
        />
      ) : (
        <ul className="space-y-3">
          {list.data.map((r) => (
            <li key={r.id} className="flex overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
              <EventImage category={r.eventCategory} className="hidden w-32 shrink-0 sm:block" />
              <div className="flex flex-1 flex-wrap items-center justify-between gap-3 p-4">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="font-semibold text-slate-900">{r.eventTitle}</h2>
                    <StatusBadge status={r.status} />
                    {r.eventStatus === 'CANCELLED' && <StatusBadge status="CANCELLED" />}
                  </div>
                  <p className="mt-0.5 text-sm text-slate-600">{formatDate(r.startsAt)}</p>
                  <p className="text-sm text-slate-500">
                    {r.venue} · {r.eventCategory}
                  </p>
                  {r.status === 'WAITLISTED' && r.waitlistPosition && (
                    <p className="mt-1 text-sm font-medium text-amber-700">You are number {r.waitlistPosition} on the waitlist. You will be confirmed automatically when a seat opens.</p>
                  )}
                </div>
                {started(r) ? (
                  <span className="text-xs text-slate-500">Already started</span>
                ) : (
                  <Button variant="secondary" small onClick={() => setLeaving(r)}>
                    {r.status === 'WAITLISTED' ? 'Leave waitlist' : 'Cancel'}
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {leaving && (
        <ConfirmDialog
          title={leaving.status === 'WAITLISTED' ? 'Leave the waitlist?' : 'Cancel your registration?'}
          message={`${leaving.eventTitle} on ${formatDate(leaving.startsAt)}.`}
          confirmLabel={leaving.status === 'WAITLISTED' ? 'Leave waitlist' : 'Cancel registration'}
          danger
          loading={cancel.isPending}
          onConfirm={() => cancel.mutate(leaving)}
          onCancel={() => setLeaving(null)}
        />
      )}
    </>
  )
}
