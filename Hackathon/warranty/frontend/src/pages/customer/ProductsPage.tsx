import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, errorMessage } from '../../api/client'
import { claimApi, productApi } from '../../api/endpoints'
import type { Priority, Product } from '../../api/types'
import { ProductImage } from '../../components/ProductImage'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, Field, formatDay, LoadingState, Modal, PageHeader, SelectField, TextAreaField } from '../../components/ui'
import { useDebounced } from '../../hooks'

function WarrantyBadge({ p }: { p: Product }) {
  if (!p.warrantyActive) {
    return <span className="inline-flex rounded-full bg-rose-100 px-2.5 py-0.5 text-xs font-semibold text-rose-800">Expired {formatDay(p.warrantyEnd)}</span>
  }
  if (p.expiringSoon) {
    return (
      <span className="inline-flex rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-800">
        Expires in {p.daysLeft} day{p.daysLeft === 1 ? '' : 's'}
      </span>
    )
  }
  return <span className="inline-flex rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-semibold text-emerald-800">Covered until {formatDay(p.warrantyEnd)}</span>
}

export function ProductsPage() {
  const [search, setSearch] = useState('')
  const [registering, setRegistering] = useState(false)
  const [claiming, setClaiming] = useState<Product | null>(null)
  const debouncedSearch = useDebounced(search)

  const products = useQuery({
    queryKey: ['products', 'mine', debouncedSearch],
    queryFn: () => productApi.mine(debouncedSearch || undefined),
    placeholderData: (previous) => previous,
  })

  return (
    <>
      <PageHeader
        title="My products"
        subtitle="Register what you have bought and file a claim while it is under warranty."
        actions={<Button onClick={() => setRegistering(true)}>Register a product</Button>}
      />

      <div className="mb-5 max-w-sm">
        <label htmlFor="search" className="sr-only">
          Search your products
        </label>
        <input
          id="search"
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by name, brand or serial number"
          className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
        />
      </div>

      {products.isPending ? (
        <LoadingState />
      ) : products.isError ? (
        <ErrorState message={errorMessage(products.error)} onRetry={() => products.refetch()} />
      ) : products.data.length === 0 ? (
        <EmptyState
          title={search ? 'No products match your search' : 'You haven’t registered any products yet'}
          action={!search && <Button onClick={() => setRegistering(true)}>Register your first product</Button>}
        />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {products.data.map((p) => (
            <li key={p.id} className={`flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm ${p.warrantyActive ? '' : 'opacity-80'}`}>
              <ProductImage category={p.category} label={p.categoryLabel} className="h-36 w-full" />
              <div className="flex flex-1 flex-col p-4">
                <h2 className="font-semibold text-slate-900">
                  {p.brand} {p.name}
                </h2>
                <p className="mt-0.5 text-xs text-slate-500">
                  {p.categoryLabel} · serial {p.serialNumber}
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  Bought {formatDay(p.purchaseDate)} · {p.warrantyMonths} month warranty
                </p>
                <div className="mt-3">
                  <WarrantyBadge p={p} />
                </div>
                <div className="mt-4 flex flex-1 items-end">
                  {p.hasActiveClaim ? (
                    <Link to="/claims" className="text-sm font-medium text-brand-700 hover:underline">
                      Claim in progress: view
                    </Link>
                  ) : (
                    <Button small className="w-full" disabled={!p.warrantyActive} onClick={() => setClaiming(p)}>
                      {p.warrantyActive ? 'File a claim' : 'Warranty expired'}
                    </Button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {registering && <RegisterProductModal onClose={() => setRegistering(false)} />}
      {claiming && <ClaimModal product={claiming} onClose={() => setClaiming(null)} />}
    </>
  )
}

function RegisterProductModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const categories = useQuery({ queryKey: ['categories'], queryFn: productApi.categories, staleTime: Infinity })
  const [form, setForm] = useState({ name: '', brand: '', category: '', serialNumber: '', purchaseDate: '', warrantyMonths: '12' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const register = useMutation({
    mutationFn: () => productApi.register({ ...form, warrantyMonths: Number(form.warrantyMonths) }),
    onSuccess: (p) => {
      toast.success(`Registered ${p.brand} ${p.name}`)
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

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))
  const today = new Date().toISOString().slice(0, 10)

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    register.mutate()
  }

  return (
    <Modal title="Register a product" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <div className="grid grid-cols-2 gap-4">
          <Field label="Brand" maxLength={80} placeholder="Samsung" value={form.brand} onChange={set('brand')} error={fieldErrors.brand} />
          <Field label="Product" maxLength={120} placeholder="Galaxy S23" value={form.name} onChange={set('name')} error={fieldErrors.name} />
        </div>
        <SelectField label="Category" value={form.category} onChange={set('category')} error={fieldErrors.category}>
          <option value="">Select a category</option>
          {(categories.data ?? []).map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </SelectField>
        <Field label="Serial number" maxLength={40} placeholder="SM12345678" value={form.serialNumber} onChange={set('serialNumber')} error={fieldErrors.serialNumber} hint="Printed on the product or its box" />
        <div className="grid grid-cols-2 gap-4">
          <Field label="Purchase date" type="date" max={today} value={form.purchaseDate} onChange={set('purchaseDate')} error={fieldErrors.purchaseDate} />
          <Field label="Warranty (months)" type="number" min="1" max="60" value={form.warrantyMonths} onChange={set('warrantyMonths')} error={fieldErrors.warrantyMonths} />
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={register.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={register.isPending}>
            Register product
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function ClaimModal({ product, onClose }: { product: Product; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [description, setDescription] = useState('')
  const [priority, setPriority] = useState<Priority>('MEDIUM')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const file = useMutation({
    mutationFn: () => claimApi.file({ productId: product.id, issueDescription: description, priority }),
    onSuccess: () => {
      toast.success('Claim filed. A service agent will review it soon.')
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
      queryClient.invalidateQueries() // an open claim may already exist, or the warranty has just ended
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    file.mutate()
  }

  return (
    <Modal title="File a warranty claim" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-600">
          {product.brand} {product.name} · covered until {formatDay(product.warrantyEnd)}
        </p>
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <TextAreaField
          label="What is wrong?"
          rows={4}
          maxLength={1000}
          placeholder="Describe the problem and when it started"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          error={fieldErrors.issueDescription}
          hint={`${description.length}/1000`}
        />
        <SelectField label="How urgent is it?" value={priority} onChange={(e) => setPriority(e.target.value as Priority)} error={fieldErrors.priority}>
          <option value="LOW">Low: it can wait</option>
          <option value="MEDIUM">Medium: I need it fixed soon</option>
          <option value="HIGH">High: I cannot use it at all</option>
        </SelectField>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={file.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={file.isPending}>
            Submit claim
          </Button>
        </div>
      </form>
    </Modal>
  )
}
