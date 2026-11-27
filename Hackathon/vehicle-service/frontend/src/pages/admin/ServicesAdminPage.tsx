import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { ServiceInput, ServiceType } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, Field, formatMoney, LoadingState, Modal, PageHeader, StatusBadge, TextAreaField } from '../../components/ui'

export function ServicesAdminPage() {
  const [editing, setEditing] = useState<ServiceType | 'new' | null>(null)
  const list = useQuery({ queryKey: ['admin', 'services'], queryFn: adminApi.services })

  return (
    <>
      <PageHeader title="Services" subtitle="What the workshop offers, its price for a car and how many days it takes." actions={<Button onClick={() => setEditing('new')}>New service</Button>} />
      <BaysCard />
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="No services yet" action={<Button onClick={() => setEditing('new')}>Add a service</Button>} />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {list.data.map((s) => (
            <li key={s.id} className={`rounded-xl border bg-white p-4 shadow-sm ${s.active ? 'border-slate-200' : 'border-amber-300'}`}>
              <div className="flex items-baseline justify-between gap-2">
                <h2 className="font-semibold text-slate-900">{s.name}</h2>
                <span className="text-sm font-semibold text-slate-900">{formatMoney(s.basePrice)}</span>
              </div>
              <p className="mt-1 text-sm text-slate-600">{s.description}</p>
              <p className="mt-2 text-xs font-medium text-brand-700">
                {s.durationDays} day{s.durationDays === 1 ? '' : 's'}
              </p>
              <div className="mt-3 flex items-center justify-between">
                <StatusBadge status={s.active ? 'ACTIVE' : 'CLOSED'} />
                <Button small variant="secondary" onClick={() => setEditing(s)}>
                  Edit
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {editing && <ServiceModal service={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function BaysCard() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const dash = useQuery({ queryKey: ['admin', 'dashboard'], queryFn: adminApi.dashboard })
  const [value, setValue] = useState<string | null>(null)
  const [error, setError] = useState<string | undefined>()
  const bays = dash.data?.bays

  const save = useMutation({
    mutationFn: () => adminApi.setBays(value ?? ''),
    onSuccess: (r) => {
      toast.success(`The workshop now has ${r.bays} bays`)
      setValue(null)
      setError(undefined)
      queryClient.invalidateQueries()
    },
    onError: (err) => setError(err instanceof ApiError && err.fieldErrors.bays ? err.fieldErrors.bays : errorMessage(err)),
  })

  return (
    <form
      noValidate
      onSubmit={(e: FormEvent) => {
        e.preventDefault()
        save.mutate()
      }}
      className="mb-6 flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
    >
      <div className="w-40">
        <Field label="Service bays" type="number" min={1} max={30} value={value ?? bays ?? ''} onChange={(e) => setValue(e.target.value)} error={error} />
      </div>
      <Button type="submit" small loading={save.isPending} disabled={value === null || Number(value) === bays}>
        Update
      </Button>
      <p className="text-sm text-slate-600">This many vehicles can be in the workshop on one day.</p>
    </form>
  )
}

function ServiceModal({ service, onClose }: { service: ServiceType | null; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<ServiceInput>({
    name: service?.name ?? '',
    description: service?.description ?? '',
    durationDays: service?.durationDays ?? '',
    basePrice: service?.basePrice ?? '',
    active: service?.active ?? true,
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => (service ? adminApi.updateService(service.id, form) : adminApi.createService(form)),
    onSuccess: (saved) => {
      toast.success(service ? `Saved ${saved.name}` : `Added ${saved.name}`)
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
  const set = (key: keyof ServiceInput) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    save.mutate()
  }

  return (
    <Modal title={service ? `Edit ${service.name}` : 'New service'} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="Name" maxLength={60} value={form.name} onChange={set('name')} error={fieldErrors.name} />
        <TextAreaField label="Description" rows={2} maxLength={300} value={form.description} onChange={set('description')} error={fieldErrors.description} />
        <div className="grid grid-cols-2 gap-4">
          <Field label="Days in the workshop" type="number" min={1} max={5} value={form.durationDays} onChange={set('durationDays')} error={fieldErrors.durationDays} />
          <Field label="Price for a car (₹)" type="number" min={100} value={form.basePrice} onChange={set('basePrice')} error={fieldErrors.basePrice} hint="Bikes pay less, SUVs and vans more" />
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={form.active} onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))} />
          Offered to customers
        </label>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {service ? 'Save changes' : 'Add service'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
