import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, errorMessage } from '../../api/client'
import { complaintApi } from '../../api/endpoints'
import type { ComplaintInput, ComplaintStatus } from '../../api/types'
import { PriorityChip, Stars } from '../../components/Chips'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, Field, formatDate, LoadingState, Modal, PageHeader, SelectField, StatusBadge, statusLabel, TextAreaField } from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = 'ALL' | ComplaintStatus
const tabs: Tab[] = ['ALL', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']

export function MyComplaintsPage() {
  const [tab, setTab] = useState<Tab>('ALL')
  const [search, setSearch] = useState('')
  const [filing, setFiling] = useState(false)
  const debouncedSearch = useDebounced(search)
  const list = useQuery({
    queryKey: ['complaints', 'mine', tab, debouncedSearch],
    queryFn: () => complaintApi.mine(tab === 'ALL' ? undefined : tab, debouncedSearch || undefined),
    placeholderData: (previous) => previous,
  })

  return (
    <>
      <PageHeader title="My complaints" subtitle="Everything you have told us about, and what we did." actions={<Button onClick={() => setFiling(true)}>File a complaint</Button>} />
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
            Search complaints
          </label>
          <input id="search" type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search by subject or CMP-2042" className="w-64 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" />
        </div>
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title={search || tab !== 'ALL' ? 'No complaints match' : 'You have not filed any complaints'} action={tab === 'ALL' && !search && <Button onClick={() => setFiling(true)}>File your first complaint</Button>} />
      ) : (
        <ul className="space-y-3">
          {list.data.map((c) => (
            <li key={c.id}>
              <Link to={`/complaints/${c.id}`} className="block rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition hover:border-brand-600 hover:shadow">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-xs font-medium text-slate-500">
                      {c.number} · {statusLabel(c.category)} · filed {formatDate(c.createdAt)}
                    </p>
                    <h2 className="mt-0.5 font-semibold text-slate-900">{c.subject}</h2>
                    {c.resolution ? <p className="mt-1 line-clamp-2 text-sm text-emerald-800">Resolution: {c.resolution}</p> : <p className="mt-1 line-clamp-1 text-sm text-slate-600">{c.description}</p>}
                  </div>
                  <div className="flex flex-col items-end gap-1.5">
                    <StatusBadge status={c.status} />
                    <PriorityChip priority={c.priority} />
                  </div>
                </div>
                <p className="mt-2 flex flex-wrap items-center gap-x-3 text-xs text-slate-500">
                  <span>{c.assigneeName ? `Handled by ${c.assigneeName}` : 'Waiting for an agent'}</span>
                  {c.rating ? <Stars value={c.rating} size="size-3.5" /> : c.canRate ? <span className="font-medium text-brand-700">Rate how we did</span> : null}
                </p>
              </Link>
            </li>
          ))}
        </ul>
      )}
      {filing && <FileModal onClose={() => setFiling(false)} />}
    </>
  )
}

function FileModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const meta = useQuery({ queryKey: ['meta'], queryFn: complaintApi.meta })
  const [form, setForm] = useState<ComplaintInput>({ subject: '', description: '', category: '', priority: 'MEDIUM' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const file = useMutation({
    mutationFn: () => complaintApi.file(form),
    onSuccess: (c) => {
      toast.success(`Complaint ${c.number} filed`)
      queryClient.invalidateQueries()
      onClose()
      navigate(`/complaints/${c.id}`)
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
  const set = (key: keyof ComplaintInput) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    file.mutate()
  }

  return (
    <Modal title="File a complaint" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="Subject" placeholder="e.g. Charged twice for one order" maxLength={120} value={form.subject} onChange={set('subject')} error={fieldErrors.subject} />
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
              <option key={p} value={p}>
                {statusLabel(p)}
              </option>
            ))}
          </SelectField>
        </div>
        <TextAreaField label="What happened?" rows={5} maxLength={2000} value={form.description} onChange={set('description')} error={fieldErrors.description} hint="Include order numbers, dates and who you spoke to." />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={file.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={file.isPending}>
            File complaint
          </Button>
        </div>
      </form>
    </Modal>
  )
}
