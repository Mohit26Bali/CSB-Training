import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../api/client'
import { agentApi, complaintApi } from '../api/endpoints'
import type { Complaint, ComplaintStatus, TimelineEvent } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { PriorityChip, Stars } from '../components/Chips'
import { useToast } from '../components/Toast'
import { Button, ErrorState, formatDate, LoadingState, Modal, SelectField, StatusBadge, statusLabel, TextAreaField } from '../components/ui'

function describe(e: TimelineEvent) {
  switch (e.type) {
    case 'FILED':
      return 'Complaint filed'
    case 'ASSIGNED':
      return e.note ?? 'Assigned'
    case 'FEEDBACK':
      return 'Customer feedback'
    default:
      return e.status === 'RESOLVED' ? 'Marked resolved' : e.status === 'CLOSED' ? 'Closed' : e.status === 'IN_PROGRESS' ? 'Work in progress' : statusLabel(e.status ?? '')
  }
}

function Timeline({ events }: { events: TimelineEvent[] }) {
  return (
    <ol className="relative ml-3 space-y-5 border-l-2 border-slate-200 pl-6">
      {events.map((e) => (
        <li key={e.id} className="relative">
          <span className={`absolute -left-[33px] top-1 size-4 rounded-full border-2 border-white ${e.type === 'FEEDBACK' ? 'bg-amber-400' : e.status === 'RESOLVED' || e.status === 'CLOSED' ? 'bg-emerald-500' : 'bg-brand-600'}`} />
          <p className="text-sm font-semibold text-slate-900">{describe(e)}</p>
          <p className="text-xs text-slate-500">
            {e.actorName}
            {e.actorRole === 'SUPPORT_AGENT' && ' · support'} · {formatDate(e.createdAt)}
          </p>
          {e.note && e.type !== 'ASSIGNED' && <p className="mt-1 whitespace-pre-wrap rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">{e.note}</p>}
        </li>
      ))}
    </ol>
  )
}

export function ComplaintPage() {
  const id = Number(useParams().id)
  const { user } = useAuth()
  const agent = user?.role === 'SUPPORT_AGENT'
  const detail = useQuery({ queryKey: ['complaint', id], queryFn: () => complaintApi.detail(id) })

  if (detail.isPending) return <LoadingState />
  if (detail.isError) return <ErrorState message={errorMessage(detail.error)} onRetry={() => detail.refetch()} />
  const { complaint: c, timeline } = detail.data

  return (
    <>
      <Link to={agent ? '/agent/queue' : '/complaints'} className="text-sm font-medium text-brand-700 hover:underline">
        ← Back to {agent ? 'the queue' : 'my complaints'}
      </Link>
      <div className="mt-3 grid gap-6 lg:grid-cols-[1fr_18rem]">
        <div className="min-w-0 space-y-6">
          <header>
            <p className="text-sm font-medium text-slate-500">
              {c.number} · {statusLabel(c.category)}
            </p>
            <h1 className="mt-1 text-2xl font-semibold text-slate-900">{c.subject}</h1>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <StatusBadge status={c.status} />
              <PriorityChip priority={c.priority} />
              {c.ageDays >= 3 && <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-800">Waiting {c.ageDays} days</span>}
            </div>
          </header>

          <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
              {c.customerName} wrote on {formatDate(c.createdAt)}
            </p>
            <p className="mt-2 whitespace-pre-wrap text-sm text-slate-800">{c.description}</p>
          </section>

          {c.resolution && (
            <section className="rounded-xl border border-emerald-200 bg-emerald-50 p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-emerald-800">Resolution</p>
              <p className="mt-1 whitespace-pre-wrap text-sm text-emerald-900">{c.resolution}</p>
            </section>
          )}

          {c.rating && (
            <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{agent ? 'Customer feedback' : 'Your feedback'}</p>
              <div className="mt-1 flex items-center gap-2">
                <Stars value={c.rating} />
                {c.feedback && <span className="text-sm text-slate-700">“{c.feedback}”</span>}
              </div>
            </section>
          )}
          {!agent && c.canRate && <RateForm complaint={c} />}

          <section>
            <h2 className="mb-4 text-lg font-semibold text-slate-900">Timeline</h2>
            <Timeline events={timeline} />
          </section>
        </div>

        <aside className="space-y-4">
          <div className="rounded-xl border border-slate-200 bg-white p-4 text-sm shadow-sm">
            <dl className="space-y-2">
              <div>
                <dt className="text-xs uppercase tracking-wide text-slate-500">Customer</dt>
                <dd className="font-medium text-slate-900">{c.customerName}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase tracking-wide text-slate-500">Handled by</dt>
                <dd className="font-medium text-slate-900">{c.assigneeName ?? 'Not yet assigned'}</dd>
              </div>
              {c.resolvedAt && (
                <div>
                  <dt className="text-xs uppercase tracking-wide text-slate-500">Resolved</dt>
                  <dd className="text-slate-900">{formatDate(c.resolvedAt)}</dd>
                </div>
              )}
            </dl>
          </div>
          {agent && <AgentPanel complaint={c} />}
        </aside>
      </div>
    </>
  )
}

function RateForm({ complaint }: { complaint: Complaint }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [error, setError] = useState<string | null>(null)

  const send = useMutation({
    mutationFn: () => complaintApi.feedback(complaint.id, rating, comment),
    onSuccess: () => {
      toast.success('Thank you for your feedback')
      queryClient.invalidateQueries()
    },
    onError: (err) => setError(err instanceof ApiError ? (err.fieldErrors.rating ?? err.fieldErrors.comment ?? err.message) : errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    send.mutate()
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-3 rounded-xl border border-brand-100 bg-brand-50 p-4">
      <p className="text-sm font-semibold text-slate-900">How well did we handle this?</p>
      <Stars value={rating} onPick={setRating} size="size-7" />
      <TextAreaField label="Anything to add? (optional)" rows={2} maxLength={500} value={comment} onChange={(e) => setComment(e.target.value)} />
      {error && (
        <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
          {error}
        </p>
      )}
      <Button type="submit" loading={send.isPending}>
        Send feedback
      </Button>
    </form>
  )
}

function AgentPanel({ complaint }: { complaint: Complaint }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [moving, setMoving] = useState<ComplaintStatus | null>(null)
  const agents = useQuery({ queryKey: ['agents'], queryFn: agentApi.agents })

  const assign = useMutation({
    mutationFn: (assignee: string) => agentApi.assign(complaint.id, assignee),
    onSuccess: (c) => toast.success(c.assigneeName ? `Assigned to ${c.assigneeName}` : 'Back in the unassigned queue'),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(),
  })
  const finished = complaint.status === 'RESOLVED' || complaint.status === 'CLOSED'

  return (
    <div className="space-y-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="text-sm font-semibold text-slate-900">Agent actions</h2>
      {!finished && (
        <SelectField label="Assignee" value={complaint.assigneeUsername ?? ''} onChange={(e) => assign.mutate(e.target.value)} disabled={assign.isPending}>
          <option value="" disabled={complaint.status !== 'OPEN'}>
            Unassigned
          </option>
          {(agents.data ?? []).map((a) => (
            <option key={a.username} value={a.username}>
              {a.fullname} ({a.activeComplaints} active)
            </option>
          ))}
        </SelectField>
      )}
      {complaint.allowedNext.length === 0 ? (
        <p className="text-sm text-slate-500">This complaint is closed and cannot be changed.</p>
      ) : (
        <div className="flex flex-col gap-2">
          {complaint.allowedNext.map((s, i) => (
            <Button key={s} variant={i === 0 ? 'primary' : 'secondary'} onClick={() => setMoving(s)}>
              {complaint.status === 'RESOLVED' && s === 'IN_PROGRESS' ? 'Reopen work' : s === 'IN_PROGRESS' ? 'Start work' : s === 'RESOLVED' ? 'Resolve' : 'Close complaint'}
            </Button>
          ))}
        </div>
      )}
      {moving && <StatusModal complaint={complaint} to={moving} onClose={() => setMoving(null)} />}
    </div>
  )
}

function StatusModal({ complaint, to, onClose }: { complaint: Complaint; to: ComplaintStatus; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const resolving = to === 'RESOLVED'

  const move = useMutation({
    mutationFn: () => agentApi.setStatus(complaint.id, to, note),
    onSuccess: (c) => {
      toast.success(`${c.number} is now ${statusLabel(c.status).toLowerCase()}`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(err instanceof ApiError ? (err.fieldErrors.note ?? err.message) : errorMessage(err))
      queryClient.invalidateQueries() // someone else may have just changed it
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    move.mutate()
  }

  return (
    <Modal title={`${complaint.number}: ${statusLabel(to).toLowerCase()}`} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {to === 'CLOSED' && <p className="text-sm text-slate-600">Closed complaints cannot be reopened.</p>}
        <TextAreaField
          label={resolving ? 'Resolution notes (the customer will read this)' : 'Remark for the timeline (optional)'}
          rows={4}
          maxLength={1000}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          error={error ?? undefined}
        />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={move.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={move.isPending}>
            {resolving ? 'Resolve' : `Move to ${statusLabel(to).toLowerCase()}`}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
