import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { requestApi } from '../../api/endpoints'
import type { RequestInput, RequestStatus } from '../../api/types'
import { AgingChip, Journey, PriorityChip } from '../../components/Chips'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, Field, formatDate, LoadingState, Modal, PageHeader, SelectField, StatusBadge, statusLabel, TextAreaField } from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = 'ALL' | 'ACTIVE' | RequestStatus
const tabs: Tab[] = ['ALL', 'ACTIVE', 'RESOLVED', 'CLOSED']

export function MyRequestsPage() {
  const [tab, setTab] = useState<Tab>('ALL')
  const [search, setSearch] = useState('')
  const [raising, setRaising] = useState(false)
  const debouncedSearch = useDebounced(search)
  const list = useQuery({
    queryKey: ['requests', 'mine', debouncedSearch],
    queryFn: () => requestApi.mine(undefined, debouncedSearch || undefined),
    placeholderData: (previous) => previous,
  })
  const rows = (list.data ?? []).filter((r) => tab === 'ALL' || (tab === 'ACTIVE' ? r.aging !== null : r.status === tab))

  return (
    <>
      <PageHeader title="My requests" subtitle="What you reported, who is on it and what was done." actions={<Button onClick={() => setRaising(true)}>Report a problem</Button>} />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="Filter" className="flex gap-1 overflow-x-auto">
          {tabs.map((t) => (
            <button
              key={t}
              role="tab"
              aria-selected={tab === t}
              onClick={() => setTab(t)}
              className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${tab === t ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
            >
              {t === 'ALL' ? 'All' : t === 'ACTIVE' ? 'Still open' : statusLabel(t)}
            </button>
          ))}
        </div>
        <div className="ml-auto">
          <label htmlFor="search" className="sr-only">
            Search requests
          </label>
          <input id="search" type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search by place, problem or MR-3042" className="w-64 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" />
        </div>
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState title={search || tab !== 'ALL' ? 'No requests match' : 'You have not reported anything'} action={tab === 'ALL' && !search && <Button onClick={() => setRaising(true)}>Report your first problem</Button>} />
      ) : (
        <ul className="space-y-4">
          {rows.map((r) => (
            <li key={r.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-xs font-medium text-slate-500">
                    {r.number} · {statusLabel(r.category)} · reported {formatDate(r.createdAt)}
                  </p>
                  <h2 className="mt-0.5 font-semibold text-slate-900">{r.location}</h2>
                  <p className="mt-1 text-sm text-slate-600">{r.description}</p>
                </div>
                <div className="flex flex-col items-end gap-1.5">
                  <StatusBadge status={r.status} />
                  <PriorityChip priority={r.priority} />
                  <AgingChip aging={r.aging} days={r.ageDays} />
                </div>
              </div>
              <div className="mt-4">
                <Journey status={r.status} />
              </div>
              {r.technicianName && r.status !== 'CLOSED' && <p className="mt-3 text-sm text-slate-700">Handled by <span className="font-medium">{r.technicianName}</span></p>}
              {r.resolutionNotes && (
                <p className="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-900">
                  <span className="font-semibold">What was done:</span> {r.resolutionNotes}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
      {raising && <RaiseModal onClose={() => setRaising(false)} />}
    </>
  )
}

function RaiseModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const meta = useQuery({ queryKey: ['meta'], queryFn: requestApi.meta })
  const [form, setForm] = useState<RequestInput>({ category: '', description: '', location: '', priority: 'MEDIUM' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const raise = useMutation({
    mutationFn: () => requestApi.raise(form),
    onSuccess: (r) => {
      toast.success(`Reported as ${r.number}`)
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
  const set = (key: keyof RequestInput) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    raise.mutate()
  }

  return (
    <Modal title="Report a problem" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <div className="grid grid-cols-2 gap-4">
          <SelectField label="What kind of problem?" value={form.category} onChange={set('category')} error={fieldErrors.category}>
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
        <Field label="Where?" placeholder="e.g. Block B, Flat 402, bathroom" maxLength={100} value={form.location} onChange={set('location')} error={fieldErrors.location} />
        <TextAreaField label="What is wrong?" rows={4} maxLength={1000} value={form.description} onChange={set('description')} error={fieldErrors.description} hint="When it started and anything that makes it worse." />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={raise.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={raise.isPending}>
            Send report
          </Button>
        </div>
      </form>
    </Modal>
  )
}
