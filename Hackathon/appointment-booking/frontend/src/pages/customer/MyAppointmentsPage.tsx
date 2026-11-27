import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { customerApi } from '../../api/endpoints'
import type { Appointment } from '../../api/types'
import { ProviderPhoto } from '../../components/ProviderPhoto'
import { useToast } from '../../components/Toast'
import { DateBlock } from '../../components/DateBlock'
import { formatTime, longDay } from '../../dates'
import { Button, EmptyState, ErrorState, formatDate, LoadingState, Modal, PageHeader, StatusBadge, TextAreaField } from '../../components/ui'

const scopes = [
  { key: 'upcoming', label: 'Upcoming' },
  { key: 'previous', label: 'Previous' },
] as const

export function MyAppointmentsPage() {
  const [scope, setScope] = useState<'upcoming' | 'previous'>('upcoming')
  const list = useQuery({ queryKey: ['appointments', 'mine', scope], queryFn: () => customerApi.mine(scope), placeholderData: (previous) => previous })
  const [cancelling, setCancelling] = useState<Appointment | null>(null)

  return (
    <>
      <PageHeader
        title="My appointments"
        subtitle="Requests, confirmed visits and your history."
        actions={
          <Link to="/providers">
            <Button>Find a provider</Button>
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
          title={scope === 'upcoming' ? 'No upcoming appointments' : 'Nothing here yet'}
          hint={scope === 'upcoming' ? 'Find a provider and pick an open time.' : 'Past and cancelled appointments show up here.'}
          action={scope === 'upcoming' ? <Link to="/providers" className="font-medium text-brand-700 hover:underline">Find a provider</Link> : undefined}
        />
      ) : (
        <ul className="space-y-3">
          {list.data.map((a) => (
            <li key={a.id} className={`flex gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm ${a.status === 'CANCELLED' ? 'opacity-80' : ''}`}>
              <DateBlock start={a.start} muted={a.status === 'CANCELLED' || a.phase === 'PAST'} />
              <div className="flex min-w-0 flex-1 flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 space-y-1">
                  <p className="text-xs font-medium text-slate-500">
                    {a.number} · requested {formatDate(a.createdAt)}
                  </p>
                  <div className="flex flex-wrap items-center gap-2">
                    <ProviderPhoto id={a.providerId} name={a.providerName} title={a.providerTitle} className="size-8 text-xs" />
                    <h2 className="font-semibold text-slate-900">{a.providerName}</h2>
                    <span className="text-sm text-brand-700">{a.providerTitle}</span>
                    <StatusBadge status={a.status} />
                  </div>
                  <p className="text-sm text-slate-700">
                    {longDay(a.start)}, {formatTime(a.start)} to {formatTime(a.end)}
                    {a.location && ` · ${a.location}`}
                  </p>
                  {a.reason && <p className="text-xs text-slate-500">“{a.reason}”</p>}
                  {a.status === 'PENDING' && <p className="text-sm text-amber-700">Waiting for the provider to confirm.</p>}
                  {a.status === 'CONFIRMED' && a.phase === 'UPCOMING' && <p className="text-sm text-emerald-700">Confirmed by {a.confirmedBy}.</p>}
                  {a.status === 'CANCELLED' && (
                    <p className="text-sm text-rose-700">
                      Cancelled by {a.cancelledBy === a.customerUsername ? 'you' : 'the provider'}
                      {a.cancelReason && `: ${a.cancelReason}`}
                    </p>
                  )}
                </div>
                {a.canCancel ? (
                  <Button variant="secondary" small onClick={() => setCancelling(a)}>
                    Cancel
                  </Button>
                ) : (
                  a.status !== 'CANCELLED' && a.phase === 'UPCOMING' && <p className="max-w-40 text-xs text-slate-500">Less than 2 hours to go. Please call the provider to cancel.</p>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      {cancelling && <CancelModal appointment={cancelling} onClose={() => setCancelling(null)} />}
    </>
  )
}

function CancelModal({ appointment, onClose }: { appointment: Appointment; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const cancel = useMutation({
    mutationFn: () => customerApi.cancel(appointment.id, reason),
    onSuccess: (a) => {
      toast.success(`${a.number} is cancelled`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(errorMessage(err))
      queryClient.invalidateQueries()
    },
  })

  return (
    <Modal title={`Cancel ${appointment.number}?`} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          {appointment.providerName}, {longDay(appointment.start)} at {formatTime(appointment.start)}. The time becomes available to other customers.
        </p>
        <TextAreaField label="Reason (optional)" rows={2} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} error={error ?? undefined} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={cancel.isPending}>
            Keep it
          </Button>
          <Button variant="danger" loading={cancel.isPending} onClick={() => cancel.mutate()}>
            Cancel appointment
          </Button>
        </div>
      </div>
    </Modal>
  )
}
