import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, errorMessage } from '../../api/client'
import { ticketApi } from '../../api/endpoints'
import type { TicketInput, TicketStatus } from '../../api/types'
import { PriorityChip } from '../../components/Chips'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, Field, formatDate, LoadingState, Modal, PageHeader, SelectField, StatusBadge, statusLabel, TextAreaField } from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = 'ALL' | TicketStatus
const tabs: Tab[] = ['ALL', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']

export function MyTicketsPage() {
  const [tab, setTab] = useState<Tab>('ALL')
  const [search, setSearch] = useState('')
  const [creating, setCreating] = useState(false)
  const debouncedSearch = useDebounced(search)
  const list = useQuery({
    queryKey: ['tickets', 'mine', tab, debouncedSearch],
    queryFn: () => ticketApi.mine(tab === 'ALL' ? undefined : tab, debouncedSearch || undefined),
    placeholderData: (previous) => previous,
  })

  return (
    <>
      <PageHeader title="My tickets" subtitle="Everything you have reported and where it stands." actions={<Button onClick={() => setCreating(true)}>New ticket</Button>} />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex gap-1 overflow-x-auto">
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
        <div className="ml-auto">
          <label htmlFor="search" className="sr-only">
            Search tickets
          </label>
          <input id="search" type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search by title or HD-1042" className="w-64 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" />
        </div>
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title={search || tab !== 'ALL' ? 'No tickets match' : 'You have not raised any tickets'} action={tab === 'ALL' && !search && <Button onClick={() => setCreating(true)}>Raise your first ticket</Button>} />
      ) : (
        <ul className="space-y-3">
          {list.data.map((t) => (
            <li key={t.id}>
              <Link to={`/tickets/${t.id}`} className="block rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition hover:border-brand-600 hover:shadow">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-xs font-medium text-slate-500">
                      {t.number} · {statusLabel(t.category)} · opened {formatDate(t.createdAt)}
                    </p>
                    <h2 className="mt-0.5 font-semibold text-slate-900">{t.title}</h2>
                    <p className="mt-1 line-clamp-1 text-sm text-slate-600">{t.description}</p>
                  </div>
                  <div className="flex flex-col items-end gap-1.5">
                    <StatusBadge status={t.status} />
                    <PriorityChip priority={t.priority} />
                  </div>
                </div>
                <p className="mt-2 text-xs text-slate-500">
                  {t.assigneeName ? `With ${t.assigneeName}` : 'Waiting for an agent'}
                  {t.commentCount > 0 && ` · ${t.commentCount} message${t.commentCount === 1 ? '' : 's'}`}
                  {(t.status === 'OPEN' || t.status === 'IN_PROGRESS') && ` · target ${formatDate(t.dueAt)}`}
                </p>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {creating && <NewTicketModal onClose={() => setCreating(false)} />}
    </>
  )
}

function NewTicketModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const meta = useQuery({ queryKey: ['meta'], queryFn: ticketApi.meta })
  const [form, setForm] = useState<TicketInput>({ title: '', description: '', category: '', priority: 'MEDIUM' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const create = useMutation({
    mutationFn: () => ticketApi.create(form),
    onSuccess: (t) => {
      toast.success(`Ticket ${t.number} raised`)
      queryClient.invalidateQueries()
      onClose()
      navigate(`/tickets/${t.id}`)
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
  const set = (key: keyof TicketInput) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    create.mutate()
  }

  return (
    <Modal title="Raise a ticket" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="What is the problem?" placeholder="e.g. VPN keeps disconnecting" maxLength={120} value={form.title} onChange={set('title')} error={fieldErrors.title} />
        <div className="grid grid-cols-2 gap-4">
          <SelectField label="Category" value={form.category} onChange={set('category')} error={fieldErrors.category}>
            <option value="">Choose…</option>
            {(meta.data?.categories ?? []).map((c) => (
              <option key={c} value={c}>
                {statusLabel(c)}
              </option>
            ))}
          </SelectField>
          <SelectField label="How urgent?" value={form.priority} onChange={set('priority')} error={fieldErrors.priority}>
            {(meta.data?.priorities ?? []).map((p) => (
              <option key={p.name} value={p.name}>
                {statusLabel(p.name)} (fix within {p.slaHours}h)
              </option>
            ))}
          </SelectField>
        </div>
        <TextAreaField label="Details" rows={5} maxLength={2000} value={form.description} onChange={set('description')} error={fieldErrors.description} hint="What happened, when it started and what you have already tried." />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={create.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={create.isPending}>
            Raise ticket
          </Button>
        </div>
      </form>
    </Modal>
  )
}
