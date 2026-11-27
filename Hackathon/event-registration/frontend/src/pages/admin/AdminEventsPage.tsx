import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi, eventApi } from '../../api/endpoints'
import type { EventInput, EventItem, EventStatus } from '../../api/types'
import { useToast } from '../../components/Toast'
import {
  Button,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Field,
  formatDate,
  LoadingState,
  Modal,
  PageHeader,
  SelectField,
  StatusBadge,
  statusLabel,
  TextAreaField,
} from '../../components/ui'
import { SeatBar } from '../employee/EventsPage'

type Tab = 'ALL' | EventStatus
const tabs: Tab[] = ['ALL', 'DRAFT', 'PUBLISHED', 'CANCELLED']

export function AdminEventsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('ALL')
  const [editing, setEditing] = useState<EventItem | 'new' | null>(null)
  const [viewing, setViewing] = useState<EventItem | null>(null)
  const [cancelling, setCancelling] = useState<EventItem | null>(null)

  const events = useQuery({
    queryKey: ['admin', 'events', tab],
    queryFn: () => adminApi.events(tab === 'ALL' ? undefined : tab),
    placeholderData: (previous) => previous,
  })

  const publish = useMutation({
    mutationFn: (e: EventItem) => adminApi.publish(e.id),
    onSuccess: (e) => toast.success(`“${e.title}” is published. Employees can register now`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(),
  })
  const cancel = useMutation({
    mutationFn: (e: EventItem) => adminApi.cancel(e.id),
    onSuccess: (e) => toast.success(`“${e.title}” is cancelled`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => {
      setCancelling(null)
      queryClient.invalidateQueries()
    },
  })
  const busyId = publish.isPending ? publish.variables?.id : undefined

  return (
    <>
      <PageHeader
        title="Events"
        subtitle="Create an event as a draft, publish it when it is ready, and see who registered."
        actions={<Button onClick={() => setEditing('new')}>New event</Button>}
      />

      <div role="tablist" aria-label="Filter by status" className="mb-4 flex gap-1 overflow-x-auto">
        {tabs.map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${tab === t ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
          >
            {t === 'ALL' ? 'All' : statusLabel(t)}
          </button>
        ))}
      </div>

      {events.isPending ? (
        <LoadingState />
      ) : events.isError ? (
        <ErrorState message={errorMessage(events.error)} onRetry={() => events.refetch()} />
      ) : events.data.length === 0 ? (
        <EmptyState title="No events here" action={tab === 'ALL' && <Button onClick={() => setEditing('new')}>Create the first event</Button>} />
      ) : (
        <ul className="space-y-3">
          {events.data.map((e) => {
            const editable = e.status !== 'CANCELLED' && !e.past
            return (
              <li key={e.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="font-semibold text-slate-900">{e.title}</h2>
                      <StatusBadge status={e.status} />
                      {e.past && e.status !== 'CANCELLED' && <span className="text-xs text-slate-500">Already started</span>}
                    </div>
                    <p className="mt-0.5 text-sm text-slate-600">
                      {formatDate(e.startsAt)} · {e.venue} · {e.category}
                    </p>
                    <div className="mt-3 max-w-xs">
                      <p className="mb-1 text-xs text-slate-500">
                        {e.confirmed}/{e.capacity} confirmed{e.waitlisted > 0 && `, ${e.waitlisted} waiting`}
                      </p>
                      <SeatBar confirmed={e.confirmed} capacity={e.capacity} />
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button variant="secondary" small onClick={() => setViewing(e)}>
                      Participants
                    </Button>
                    {editable && (
                      <Button variant="secondary" small onClick={() => setEditing(e)}>
                        Edit
                      </Button>
                    )}
                    {e.status === 'DRAFT' && !e.past && (
                      <Button small loading={busyId === e.id} disabled={publish.isPending} onClick={() => publish.mutate(e)}>
                        Publish
                      </Button>
                    )}
                    {editable && (
                      <Button variant="danger" small onClick={() => setCancelling(e)}>
                        Cancel event
                      </Button>
                    )}
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {editing && <EventFormModal event={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
      {viewing && <ParticipantsModal event={viewing} onClose={() => setViewing(null)} />}
      {cancelling && (
        <ConfirmDialog
          title="Cancel this event?"
          message={`“${cancelling.title}” will be cancelled and all ${cancelling.confirmed + cancelling.waitlisted} registrations on it released. This cannot be undone.`}
          confirmLabel="Cancel event"
          danger
          loading={cancel.isPending}
          onConfirm={() => cancel.mutate(cancelling)}
          onCancel={() => setCancelling(null)}
        />
      )}
    </>
  )
}

/** "2026-11-02T10:00:00" -> "2026-11-02T10:00", the format a datetime-local input wants. */
const toInput = (iso: string) => iso.slice(0, 16)

function EventFormModal({ event, onClose }: { event: EventItem | null; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const categories = useQuery({ queryKey: ['categories'], queryFn: eventApi.categories })
  const [form, setForm] = useState<EventInput>({
    title: event?.title ?? '',
    category: event?.category ?? '',
    venue: event?.venue ?? '',
    description: event?.description ?? '',
    startsAt: event ? toInput(event.startsAt) : '',
    endsAt: event ? toInput(event.endsAt) : '',
    capacity: event?.capacity ?? '',
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => (event ? adminApi.update(event.id, form) : adminApi.create(form)),
    onSuccess: (saved) => {
      toast.success(event ? `Saved “${saved.title}”` : `Created “${saved.title}” as a draft`)
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

  const set = (key: keyof EventInput) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    save.mutate()
  }

  return (
    <Modal title={event ? 'Edit event' : 'New event'} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="Title" maxLength={120} value={form.title} onChange={set('title')} error={fieldErrors.title} />
        <div className="grid grid-cols-2 gap-4">
          <SelectField label="Category" value={form.category} onChange={set('category')} error={fieldErrors.category}>
            <option value="">Choose…</option>
            {(categories.data ?? []).map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </SelectField>
          <Field label="Capacity" type="number" min={1} max={5000} value={form.capacity} onChange={set('capacity')} error={fieldErrors.capacity} />
        </div>
        <Field label="Venue" maxLength={120} value={form.venue} onChange={set('venue')} error={fieldErrors.venue} />
        <div className="grid grid-cols-2 gap-4">
          <Field label="Starts" type="datetime-local" value={form.startsAt} onChange={set('startsAt')} error={fieldErrors.startsAt} />
          <Field label="Ends" type="datetime-local" value={form.endsAt} onChange={set('endsAt')} error={fieldErrors.endsAt} />
        </div>
        <TextAreaField label="Description" rows={4} maxLength={2000} value={form.description} onChange={set('description')} error={fieldErrors.description} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {event ? 'Save changes' : 'Create draft'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function ParticipantsModal({ event, onClose }: { event: EventItem; onClose: () => void }) {
  const list = useQuery({ queryKey: ['admin', 'participants', event.id], queryFn: () => adminApi.participants(event.id) })

  return (
    <Modal title={`Participants: ${event.title}`} onClose={onClose}>
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="Nobody has registered yet" />
      ) : (
        <ul className="max-h-96 divide-y divide-slate-100 overflow-y-auto">
          {list.data.map((p) => (
            <li key={p.id} className="flex items-center justify-between gap-3 py-2.5">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-slate-900">{p.fullname}</p>
                <p className="text-xs text-slate-500">
                  {p.username} · registered {formatDate(p.registeredAt)}
                </p>
              </div>
              <div className="flex items-center gap-2">
                {p.waitlistPosition && <span className="text-xs text-slate-500">#{p.waitlistPosition}</span>}
                <StatusBadge status={p.status} />
              </div>
            </li>
          ))}
        </ul>
      )}
      <div className="mt-4 flex justify-end">
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </div>
    </Modal>
  )
}
