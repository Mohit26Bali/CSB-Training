import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { visitorApi } from '../../api/endpoints'
import type { Visit, VisitInput } from '../../api/types'
import { PassCard } from '../../components/PassCard'
import { useToast } from '../../components/Toast'
import { Button, ConfirmDialog, EmptyState, ErrorState, Field, formatDate, formatDay, LoadingState, Modal, PageHeader, StatusBadge, TextAreaField } from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = 'UPCOMING' | 'TODAY' | 'HISTORY' | 'ALL'
const tabs: Tab[] = ['UPCOMING', 'TODAY', 'HISTORY', 'ALL']
const today = () => new Date().toISOString().slice(0, 10)

export function InvitationsPage() {
  const [tab, setTab] = useState<Tab>('UPCOMING')
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<Visit | 'new' | null>(null)
  const [pass, setPass] = useState<Visit | null>(null)
  const [cancelling, setCancelling] = useState<Visit | null>(null)
  const toast = useToast()
  const queryClient = useQueryClient()
  const debouncedSearch = useDebounced(search)
  const list = useQuery({ queryKey: ['visitors', 'mine', debouncedSearch], queryFn: () => visitorApi.mine(undefined, debouncedSearch || undefined), placeholderData: (previous) => previous })

  const cancel = useMutation({
    mutationFn: (v: Visit) => visitorApi.cancel(v.id),
    onSuccess: (v) => toast.success(`Invitation for ${v.visitorName} cancelled`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => {
      setCancelling(null)
      queryClient.invalidateQueries()
    },
  })

  const t = today()
  const rows = (list.data ?? []).filter((v) => {
    if (tab === 'TODAY') return v.expectedDate === t && v.status !== 'CANCELLED'
    if (tab === 'UPCOMING') return v.status === 'EXPECTED' && v.expectedDate >= t
    if (tab === 'HISTORY') return v.status === 'CHECKED_OUT' || v.status === 'CANCELLED' || v.lapsed
    return true
  })

  return (
    <>
      <PageHeader title="My visitors" subtitle="Invite someone, share their pass and see when they arrive." actions={<Button onClick={() => setEditing('new')}>Invite a visitor</Button>} />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="Filter" className="flex gap-1 overflow-x-auto">
          {tabs.map((x) => (
            <button key={x} role="tab" aria-selected={tab === x} onClick={() => setTab(x)} className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${tab === x ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
              {x.charAt(0) + x.slice(1).toLowerCase()}
            </button>
          ))}
        </div>
        <div className="ml-auto">
          <label htmlFor="search" className="sr-only">
            Search visitors
          </label>
          <input id="search" type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Name, phone, company or pass" className="w-64 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" />
        </div>
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState title={search ? 'No visitors match' : tab === 'UPCOMING' ? 'No visitors coming up' : 'Nothing here'} action={!search && <Button onClick={() => setEditing('new')}>Invite a visitor</Button>} />
      ) : (
        <ul className="space-y-3">
          {rows.map((v) => (
            <li key={v.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="font-semibold text-slate-900">{v.visitorName}</h2>
                    <StatusBadge status={v.status} />
                    {v.lapsed && <span className="rounded-full bg-rose-100 px-2.5 py-0.5 text-xs font-semibold text-rose-800">Did not come</span>}
                    {v.dueToday && <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-800">Today</span>}
                  </div>
                  <p className="text-sm text-slate-600">
                    {v.company ? `${v.company} · ` : ''}
                    {v.phone}
                  </p>
                  <p className="text-sm text-slate-700">{v.purpose}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    {formatDay(v.expectedDate)}
                    {v.expectedTime && ` at ${v.expectedTime.slice(0, 5)}`} · pass <span className="font-mono">{v.passCode}</span>
                  </p>
                  {v.checkedInAt && (
                    <p className="mt-1 text-sm font-medium text-emerald-700">
                      {v.status === 'CHECKED_IN' ? `Arrived ${formatDate(v.checkedInAt)}, inside for ${v.minutesInside} min` : `In ${formatDate(v.checkedInAt)}, out ${formatDate(v.checkedOutAt)}`}
                    </p>
                  )}
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button variant="secondary" small onClick={() => setPass(v)}>
                    Pass
                  </Button>
                  {v.canCancel && (
                    <>
                      <Button variant="secondary" small onClick={() => setEditing(v)}>
                        Edit
                      </Button>
                      <Button variant="secondary" small onClick={() => setCancelling(v)}>
                        Cancel
                      </Button>
                    </>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {editing && <InviteModal visit={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
      {pass && (
        <Modal title="Visitor pass" onClose={() => setPass(null)}>
          <PassCard visit={pass} />
          <div className="mt-4 flex justify-end">
            <Button variant="secondary" onClick={() => setPass(null)}>
              Close
            </Button>
          </div>
        </Modal>
      )}
      {cancelling && (
        <ConfirmDialog
          title="Cancel this invitation?"
          message={`${cancelling.visitorName} will not be let in with pass ${cancelling.passCode}.`}
          confirmLabel="Cancel invitation"
          danger
          loading={cancel.isPending}
          onConfirm={() => cancel.mutate(cancelling)}
          onCancel={() => setCancelling(null)}
        />
      )}
    </>
  )
}

function InviteModal({ visit, onClose }: { visit: Visit | null; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<VisitInput>({
    visitorName: visit?.visitorName ?? '',
    phone: visit?.phone ?? '',
    company: visit?.company ?? '',
    purpose: visit?.purpose ?? '',
    expectedDate: visit?.expectedDate ?? today(),
    expectedTime: visit?.expectedTime?.slice(0, 5) ?? '',
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [created, setCreated] = useState<Visit | null>(null)

  const save = useMutation({
    mutationFn: () => (visit ? visitorApi.update(visit.id, form) : visitorApi.invite(form)),
    onSuccess: (v) => {
      toast.success(visit ? `Saved ${v.visitorName}` : `${v.visitorName} is invited`)
      queryClient.invalidateQueries()
      if (visit) onClose()
      else setCreated(v)
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
  const set = (key: keyof VisitInput) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    save.mutate()
  }

  if (created) {
    return (
      <Modal title="Invitation sent" onClose={onClose}>
        <p className="mb-4 text-sm text-slate-600">Share this pass with {created.visitorName}. They show the code at the gate.</p>
        <PassCard visit={created} />
        <div className="mt-4 flex justify-end">
          <Button onClick={onClose}>Done</Button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal title={visit ? 'Edit invitation' : 'Invite a visitor'} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <div className="grid grid-cols-2 gap-4">
          <Field label="Visitor name" maxLength={100} value={form.visitorName} onChange={set('visitorName')} error={fieldErrors.visitorName} />
          <Field label="Visitor phone" type="tel" placeholder="+91 98450 00000" value={form.phone} onChange={set('phone')} error={fieldErrors.phone} />
        </div>
        <Field label="Company (optional)" maxLength={100} value={form.company} onChange={set('company')} error={fieldErrors.company} />
        <TextAreaField label="Purpose of the visit" rows={2} maxLength={200} value={form.purpose} onChange={set('purpose')} error={fieldErrors.purpose} />
        <div className="grid grid-cols-2 gap-4">
          <Field label="Expected date" type="date" min={today()} value={form.expectedDate} onChange={set('expectedDate')} error={fieldErrors.expectedDate} />
          <Field label="Around what time? (optional)" type="time" value={form.expectedTime} onChange={set('expectedTime')} error={fieldErrors.expectedTime} />
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {visit ? 'Save changes' : 'Send invitation'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
