import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi, customerApi } from '../../api/endpoints'
import type { MenuInput, MenuItem } from '../../api/types'
import { DishPhoto, VegMark } from '../../components/DishPhoto'
import { useToast } from '../../components/Toast'
import { useDebounced } from '../../hooks'
import { Button, EmptyState, ErrorState, Field, formatMoney, LoadingState, Modal, PageHeader, SelectField, StatusBadge, statusLabel, TextAreaField } from '../../components/ui'

export function MenuAdminPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [category, setCategory] = useState('')
  const [search, setSearch] = useState('')
  const q = useDebounced(search)
  const [editing, setEditing] = useState<MenuItem | 'new' | null>(null)
  const categories = useQuery({ queryKey: ['menu', 'categories'], queryFn: customerApi.categories })
  const list = useQuery({ queryKey: ['admin', 'menu', category, q], queryFn: () => adminApi.menu(category, q), placeholderData: (previous) => previous })

  const toggle = useMutation({
    mutationFn: (m: MenuItem) =>
      adminApi.updateDish(m.id, { name: m.name, description: m.description ?? '', category: m.category, price: m.price, vegetarian: m.vegetarian, active: !m.active }),
    onSuccess: (m) => {
      toast.success(`${m.name} is now ${m.active ? 'on the menu' : 'sold out'}`)
      queryClient.invalidateQueries()
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  return (
    <>
      <PageHeader title="Menu" subtitle="Switch a dish off when it sells out. Customers can only order dishes that are on." actions={<Button onClick={() => setEditing('new')}>New dish</Button>} />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="w-56">
          <Field label="Search" placeholder="Dish name…" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <div className="w-52">
          <SelectField label="Category" value={category} onChange={(e) => setCategory(e.target.value)}>
            <option value="">All</option>
            {(categories.data ?? []).map((c) => (
              <option key={c} value={c}>
                {statusLabel(c).replace(' and ', ' & ')}
              </option>
            ))}
          </SelectField>
        </div>
      </div>
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="No dish found" action={<Button onClick={() => setEditing('new')}>Add a dish</Button>} />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {list.data.map((m) => (
            <li key={m.id} className={`flex overflow-hidden rounded-xl border bg-white shadow-sm ${m.active ? 'border-slate-200' : 'border-amber-300'}`}>
              <DishPhoto imageKey={m.imageKey} category={m.category} className={`w-24 shrink-0 ${m.active ? '' : 'grayscale'}`} />
              <div className="min-w-0 flex-1 space-y-1 p-3">
                <div className="flex items-start justify-between gap-2">
                  <h2 className="flex items-center gap-1.5 truncate font-semibold text-slate-900">
                    <VegMark vegetarian={m.vegetarian} />
                    <span className="truncate">{m.name}</span>
                  </h2>
                  <span className="text-sm font-semibold text-slate-900">{formatMoney(m.price)}</span>
                </div>
                <p className="text-xs text-slate-500">{statusLabel(m.category).replace(' and ', ' & ')}</p>
                <div className="flex items-center justify-between pt-1">
                  <StatusBadge status={m.active ? 'ACTIVE' : 'CLOSED'} />
                  <div className="flex gap-1.5">
                    <Button small variant="secondary" loading={toggle.isPending && toggle.variables?.id === m.id} onClick={() => toggle.mutate(m)}>
                      {m.active ? 'Sold out' : 'Back on'}
                    </Button>
                    <Button small variant="secondary" onClick={() => setEditing(m)}>
                      Edit
                    </Button>
                  </div>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
      {editing && <DishModal dish={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function DishModal({ dish, onClose }: { dish: MenuItem | null; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const categories = useQuery({ queryKey: ['menu', 'categories'], queryFn: customerApi.categories })
  const [form, setForm] = useState<MenuInput>({
    name: dish?.name ?? '',
    description: dish?.description ?? '',
    category: dish?.category ?? '',
    price: dish?.price ?? '',
    vegetarian: dish?.vegetarian ?? true,
    active: dish?.active ?? true,
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => (dish ? adminApi.updateDish(dish.id, form) : adminApi.createDish(form)),
    onSuccess: (saved) => {
      toast.success(dish ? `Saved ${saved.name}` : `Added ${saved.name}`)
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
  const set = (key: keyof MenuInput) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    save.mutate()
  }

  return (
    <Modal title={dish ? `Edit ${dish.name}` : 'New dish'} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="Name" maxLength={60} value={form.name} onChange={set('name')} error={fieldErrors.name} />
        <TextAreaField label="Description" rows={2} maxLength={300} value={form.description} onChange={set('description')} error={fieldErrors.description} />
        <div className="grid grid-cols-2 gap-4">
          <SelectField label="Category" value={form.category} onChange={set('category')} error={fieldErrors.category}>
            <option value="">Choose…</option>
            {(categories.data ?? []).map((c) => (
              <option key={c} value={c}>
                {statusLabel(c).replace(' and ', ' & ')}
              </option>
            ))}
          </SelectField>
          <Field label="Price (₹)" type="number" min={10} value={form.price} onChange={set('price')} error={fieldErrors.price} />
        </div>
        <div className="flex flex-wrap gap-6 text-sm text-slate-700">
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={form.vegetarian} onChange={(e) => setForm((f) => ({ ...f, vegetarian: e.target.checked }))} />
            Vegetarian
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={form.active} onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))} />
            On the menu
          </label>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {dish ? 'Save changes' : 'Add dish'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
