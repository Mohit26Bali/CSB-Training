import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../api/client'
import { agentApi, ticketApi } from '../api/endpoints'
import type { Ticket, TicketStatus } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { PriorityChip, SlaChip } from '../components/Chips'
import { useToast } from '../components/Toast'
import { Button, ErrorState, formatDate, LoadingState, Modal, SelectField, StatusBadge, statusLabel, TextAreaField } from '../components/ui'

const pipeline: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']

function Pipeline({ status }: { status: TicketStatus }) {
  const at = pipeline.indexOf(status)
  return (
    <ol className="flex items-center gap-1 text-xs font-medium" aria-label="Progress">
      {pipeline.map((s, i) => (
        <li key={s} className="flex items-center gap-1">
          <span className={`rounded-full px-2.5 py-1 ${i < at ? 'bg-brand-100 text-brand-800' : i === at ? 'bg-brand-700 text-white' : 'bg-slate-100 text-slate-500'}`}>{statusLabel(s)}</span>
          {i < pipeline.length - 1 && <span className="text-slate-300">›</span>}
        </li>
      ))}
    </ol>
  )
}

export function TicketPage() {
  const id = Number(useParams().id)
  const { user } = useAuth()
  const agent = user?.role === 'SUPPORT_AGENT'
  const detail = useQuery({ queryKey: ['ticket', id], queryFn: () => ticketApi.detail(id) })

  if (detail.isPending) return <LoadingState />
  if (detail.isError) return <ErrorState message={errorMessage(detail.error)} onRetry={() => detail.refetch()} />
  const { ticket, comments } = detail.data

  return (
    <>
      <Link to={agent ? '/agent/queue' : '/tickets'} className="text-sm font-medium text-brand-700 hover:underline">
        ← Back to {agent ? 'the queue' : 'my tickets'}
      </Link>
      <div className="mt-3 grid gap-6 lg:grid-cols-[1fr_18rem]">
        <div className="min-w-0 space-y-6">
          <header>
            <p className="text-sm font-medium text-slate-500">
              {ticket.number} · {statusLabel(ticket.category)}
            </p>
            <h1 className="mt-1 text-2xl font-semibold text-slate-900">{ticket.title}</h1>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <StatusBadge status={ticket.status} />
              <PriorityChip priority={ticket.priority} />
              {agent && <SlaChip ticket={ticket} />}
            </div>
            <div className="mt-4 overflow-x-auto">
              <Pipeline status={ticket.status} />
            </div>
          </header>

          <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
              {ticket.requesterName} wrote on {formatDate(ticket.createdAt)}
            </p>
            <p className="mt-2 whitespace-pre-wrap text-sm text-slate-800">{ticket.description}</p>
          </section>

          {ticket.resolution && (
            <section className="rounded-xl border border-emerald-200 bg-emerald-50 p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-emerald-800">Resolution</p>
              <p className="mt-1 whitespace-pre-wrap text-sm text-emerald-900">{ticket.resolution}</p>
            </section>
          )}

          <section>
            <h2 className="mb-3 text-lg font-semibold text-slate-900">Conversation</h2>
            {comments.length === 0 ? (
              <p className="text-sm text-slate-500">No messages yet.</p>
            ) : (
              <ul className="space-y-3">
                {comments.map((c) => (
                  <li key={c.id} className={`rounded-xl border p-3 ${c.internal ? 'border-amber-200 bg-amber-50' : c.authorRole === 'SUPPORT_AGENT' ? 'border-brand-100 bg-brand-50' : 'border-slate-200 bg-white'}`}>
                    <p className="text-xs text-slate-500">
                      <span className="font-semibold text-slate-800">{c.authorName}</span>
                      {c.authorRole === 'SUPPORT_AGENT' && ' · support'} · {formatDate(c.createdAt)}
                      {c.internal && <span className="ml-2 rounded bg-amber-200 px-1.5 py-0.5 font-semibold text-amber-900">Internal note</span>}
                    </p>
                    <p className="mt-1 whitespace-pre-wrap text-sm text-slate-800">{c.body}</p>
                  </li>
                ))}
              </ul>
            )}
            {ticket.status !== 'CLOSED' ? <CommentForm ticket={ticket} agent={agent} /> : <p className="mt-4 text-sm text-slate-500">This ticket is closed. Raise a new one if the problem comes back.</p>}
          </section>
        </div>

        <aside className="space-y-4">
          <div className="rounded-xl border border-slate-200 bg-white p-4 text-sm shadow-sm">
            <dl className="space-y-2">
              <div>
                <dt className="text-xs uppercase tracking-wide text-slate-500">Requested by</dt>
                <dd className="font-medium text-slate-900">{ticket.requesterName}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase tracking-wide text-slate-500">Assigned to</dt>
                <dd className="font-medium text-slate-900">{ticket.assigneeName ?? 'Nobody yet'}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase tracking-wide text-slate-500">Target</dt>
                <dd className="text-slate-900">{formatDate(ticket.dueAt)}</dd>
              </div>
              {ticket.resolvedAt && (
                <div>
                  <dt className="text-xs uppercase tracking-wide text-slate-500">Resolved</dt>
                  <dd className="text-slate-900">{formatDate(ticket.resolvedAt)}</dd>
                </div>
              )}
              {!agent && (ticket.status === 'OPEN' || ticket.status === 'IN_PROGRESS') && <SlaNote ticket={ticket} />}
            </dl>
          </div>
          {agent && <AgentPanel ticket={ticket} />}
        </aside>
      </div>
    </>
  )
}

function SlaNote({ ticket }: { ticket: Ticket }) {
  return <p className="text-xs text-slate-500">{ticket.slaState === 'BREACHED' ? 'We are past our target on this one and are working on it.' : 'We aim to resolve this by the target above.'}</p>
}

function CommentForm({ ticket, agent }: { ticket: Ticket; agent: boolean }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [body, setBody] = useState('')
  const [internal, setInternal] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const send = useMutation({
    mutationFn: () => ticketApi.comment(ticket.id, body, internal),
    onSuccess: () => {
      toast.success(internal ? 'Internal note added' : 'Message sent')
      setBody('')
      setInternal(false)
      queryClient.invalidateQueries()
    },
    onError: (err) => setError(err instanceof ApiError ? (err.fieldErrors.body ?? err.message) : errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    send.mutate()
  }

  return (
    <form onSubmit={submit} noValidate className="mt-4 space-y-3">
      <TextAreaField label={agent ? 'Reply or note' : 'Add a message'} rows={3} maxLength={1000} value={body} onChange={(e) => setBody(e.target.value)} error={error ?? undefined} />
      <div className="flex flex-wrap items-center justify-between gap-3">
        {agent ? (
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" checked={internal} onChange={(e) => setInternal(e.target.checked)} />
            Internal note (the employee will not see it)
          </label>
        ) : (
          <span />
        )}
        <Button type="submit" loading={send.isPending}>
          {internal ? 'Add note' : 'Send'}
        </Button>
      </div>
    </form>
  )
}

function AgentPanel({ ticket }: { ticket: Ticket }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [moving, setMoving] = useState<TicketStatus | null>(null)
  const agents = useQuery({ queryKey: ['agents'], queryFn: agentApi.agents })

  const assign = useMutation({
    mutationFn: (assignee: string) => agentApi.assign(ticket.id, assignee),
    onSuccess: (t) => toast.success(t.assigneeName ? `Assigned to ${t.assigneeName}` : 'Back in the unassigned queue'),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(),
  })
  const done = ticket.status === 'RESOLVED' || ticket.status === 'CLOSED'

  return (
    <div className="space-y-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="text-sm font-semibold text-slate-900">Agent actions</h2>
      {!done && (
        <SelectField label="Assignee" value={ticket.assigneeUsername ?? ''} onChange={(e) => assign.mutate(e.target.value)} disabled={assign.isPending}>
          <option value="" disabled={ticket.status !== 'OPEN'}>
            Unassigned
          </option>
          {(agents.data ?? []).map((a) => (
            <option key={a.username} value={a.username}>
              {a.fullname} ({a.activeTickets} active)
            </option>
          ))}
        </SelectField>
      )}
      {ticket.allowedNext.length === 0 ? (
        <p className="text-sm text-slate-500">This ticket is closed and cannot be changed.</p>
      ) : (
        <div className="flex flex-col gap-2">
          {ticket.allowedNext.map((s, i) => (
            <Button key={s} variant={i === 0 ? 'primary' : 'secondary'} onClick={() => setMoving(s)}>
              {ticket.status === 'RESOLVED' && s === 'IN_PROGRESS' ? 'Reopen work' : s === 'IN_PROGRESS' ? 'Start work' : s === 'RESOLVED' ? 'Mark resolved' : 'Close ticket'}
            </Button>
          ))}
        </div>
      )}
      {moving && <StatusModal ticket={ticket} to={moving} onClose={() => setMoving(null)} />}
    </div>
  )
}

function StatusModal({ ticket, to, onClose }: { ticket: Ticket; to: TicketStatus; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const resolving = to === 'RESOLVED'

  const move = useMutation({
    mutationFn: () => agentApi.setStatus(ticket.id, to, note),
    onSuccess: (t) => {
      toast.success(`${t.number} is now ${statusLabel(t.status).toLowerCase()}`)
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
    <Modal title={`${ticket.number}: move to ${statusLabel(to).toLowerCase()}`} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {to === 'CLOSED' && <p className="text-sm text-slate-600">Closed tickets cannot be reopened.</p>}
        {(resolving || to === 'IN_PROGRESS' || to === 'CLOSED') && (
          <TextAreaField label={resolving ? 'How was it resolved?' : 'Note (optional)'} rows={4} maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} error={error ?? undefined} />
        )}
        {!resolving && error && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={move.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={move.isPending}>
            Move to {statusLabel(to).toLowerCase()}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
