import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { managerApi, requestApi } from '../../api/endpoints'
import type { Technician, TechnicianInput } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, SelectField, StatusBadge, statusLabel } from '../../components/ui'

export function TechniciansPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [adding, setAdding] = useState(false)
  const list = useQuery({ queryKey: ['manager', 'technicians'], queryFn: managerApi.technicians })

  const toggle = useMutation({
    mutationFn: (t: Technician) => managerApi.setActive(t.id, !t.active),
    onSuccess: (t) => toast.success(t.active ? `${t.name} is available again` : `${t.name} will not get new jobs`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(),
  })
  const busyId = toggle.isPending ? toggle.variables?.id : undefined

  return (
    <>
      <PageHeader title="Technicians" subtitle="Staff and vendors you can assign work to." actions={<Button onClick={() => setAdding(true)}>Add a technician</Button>} />
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="No technicians yet" action={<Button onClick={() => setAdding(true)}>Add the first one</Button>} />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {list.data.map((t) => (
            <li key={t.id} className={`rounded-xl border bg-white p-4 shadow-sm ${t.active ? 'border-slate-200' : 'border-slate-200 opacity-70'}`}>
              <div className="flex items-start justify-between gap-2">
                <div>
                  <h2 className="font-semibold text-slate-900">{t.name}</h2>
                  <p className="text-sm text-slate-600">{statusLabel(t.trade)}</p>
                  {t.phone && <p className="mt-0.5 font-mono text-xs text-slate-500">{t.phone}</p>}
                </div>
                <StatusBadge status={t.active ? 'ACTIVE' : 'CLOSED'} />
              </div>
              <div className="mt-3 flex items-center justify-between">
                <span className="text-sm text-slate-600">
                  {t.activeJobs} active job{t.activeJobs === 1 ? '' : 's'}
                </span>
                <Button variant="secondary" small loading={busyId === t.id} disabled={toggle.isPending} onClick={() => toggle.mutate(t)}>
                  {t.active ? 'Make unavailable' : 'Make available'}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {adding && <AddModal onClose={() => setAdding(false)} />}
    </>
  )
}

function AddModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const meta = useQuery({ queryKey: ['meta'], queryFn: requestApi.meta })
  const [form, setForm] = useState<TechnicianInput>({ name: '', trade: '', phone: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const add = useMutation({
    mutationFn: () => managerApi.addTechnician(form),
    onSuccess: (t) => {
      toast.success(`${t.name} added`)
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
  const set = (key: keyof TechnicianInput) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    add.mutate()
  }

  return (
    <Modal title="Add a technician" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="Name" maxLength={80} value={form.name} onChange={set('name')} error={fieldErrors.name} />
        <div className="grid grid-cols-2 gap-4">
          <SelectField label="Trade" value={form.trade} onChange={set('trade')} error={fieldErrors.trade}>
            <option value="">Choose…</option>
            {(meta.data?.categories ?? []).map((c) => (
              <option key={c} value={c}>
                {statusLabel(c)}
              </option>
            ))}
          </SelectField>
          <Field label="Phone (optional)" type="tel" placeholder="+91 98450 00000" value={form.phone} onChange={set('phone')} error={fieldErrors.phone} />
        </div>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={add.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={add.isPending}>
            Add
          </Button>
        </div>
      </form>
    </Modal>
  )
}
