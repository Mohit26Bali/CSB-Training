import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import {
  createProduct, getAdminCategories, getAdminProduct, getBrands, listProducts, setProductActive, updateProduct,
  type AdminProduct, type ProductInput,
} from '../api/admin'
import type { VariantType } from '../api/types'
import { useToast } from '../components/Toast'
import { Button, Card, ErrorState, Field, FormError, PageSpinner, SelectField } from '../components/ui'
import { errorMessage } from '../hooks'
import { formatPrice } from '../lib/format'
import { PageHeader, Pager, td, th, Toggle } from './AdminLayout'

function useCategoryOptions() {
  const categories = useQuery({ queryKey: ['admin', 'categories'], queryFn: getAdminCategories })
  return categories.data ?? []
}

export function ProductsAdminPage() {
  const [params, setParams] = useSearchParams()
  const queryClient = useQueryClient()
  const toast = useToast()
  const departments = useCategoryOptions()
  const q = params.get('q') ?? ''
  const category = params.get('category') ?? ''
  const active = params.get('active') ?? ''
  const page = Number(params.get('page') ?? 0)
  const [search, setSearch] = useState(q)
  const products = useQuery({
    queryKey: ['admin', 'products', q, category, active, page],
    queryFn: () => listProducts({ q, category, active, page }),
    placeholderData: keepPreviousData,
  })
  const toggle = useMutation({
    mutationFn: ({ id, on }: { id: number; on: boolean }) => setProductActive(id, on),
    onSuccess: (p) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] })
      toast(p.active ? `${p.title} is visible in the shop` : `${p.title} is hidden from the shop`)
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })
  const set = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)))
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }

  return (
    <div>
      <PageHeader title="Products" actions={<Link to="/admin/products/new"><Button>+ Add product</Button></Link>}>
        <form className="mt-4 flex flex-wrap gap-2" onSubmit={(e: FormEvent) => { e.preventDefault(); set({ q: search.trim() }) }}>
          <label htmlFor="product-search" className="sr-only">Search products</label>
          <input id="product-search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search title, brand, tag"
            className="min-w-56 flex-1 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm sm:max-w-xs" />
          <select aria-label="Category" value={category} onChange={(e) => set({ category: e.target.value })} className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm">
            <option value="">All categories</option>
            {departments.map((d) => (
              <optgroup key={d.slug} label={d.name}>
                <option value={d.slug}>All {d.name}</option>
                {d.children.map((c) => <option key={c.slug} value={c.slug}>{c.name}</option>)}
              </optgroup>
            ))}
          </select>
          <select aria-label="Visibility" value={active} onChange={(e) => set({ active: e.target.value })} className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm">
            <option value="">Visible and hidden</option>
            <option value="true">Visible in shop</option>
            <option value="false">Hidden</option>
          </select>
          <Button type="submit" variant="outline">Search</Button>
        </form>
      </PageHeader>

      {products.isPending ? <PageSpinner /> : products.isError ? <ErrorState message={errorMessage(products.error)} /> : (
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
            <table className={`w-full min-w-[860px] text-sm ${products.isFetching ? 'opacity-60' : ''}`}>
              <thead className="bg-slate-50">
                <tr><th className={th}>Product</th><th className={th}>Category</th><th className={`${th} text-right`}>Price</th><th className={`${th} text-right`}>Stock</th><th className={th}>Rating</th><th className={th}>Visible</th></tr>
              </thead>
              <tbody>
                {products.data.items.length === 0 && <tr><td colSpan={6} className="px-4 py-10 text-center text-slate-500">No products match.</td></tr>}
                {products.data.items.map((p) => (
                  <tr key={p.id} className="border-t border-slate-100 hover:bg-slate-50">
                    <td className={td}>
                      <Link to={`/admin/products/${p.id}`} className="flex items-center gap-3">
                        <img src={p.thumbnailUrl} alt="" className="h-10 w-10 rounded bg-slate-50 object-contain" />
                        <span>
                          <span className="font-medium text-slate-900 hover:text-brand-700">{p.title}</span>
                          <span className="block text-xs text-slate-500">{p.brand ?? 'No brand'} · {p.variantCount} {p.variantCount === 1 ? 'variant' : 'variants'}</span>
                        </span>
                      </Link>
                    </td>
                    <td className={td}>{p.category}</td>
                    <td className={`${td} text-right`}>{formatPrice(p.price)}{p.discountPercent > 0 && <span className="block text-xs text-green-700">{p.discountPercent}% off</span>}</td>
                    <td className={`${td} text-right font-medium ${p.totalStock === 0 ? 'text-red-600' : p.totalStock <= 5 ? 'text-amber-600' : ''}`}>{p.totalStock}</td>
                    <td className={td}>{p.ratingCount ? `${Number(p.rating).toFixed(1)} ★ (${p.ratingCount})` : '–'}</td>
                    <td className={td}><Toggle checked={p.active} label={`Show ${p.title} in the shop`} onChange={(on) => toggle.mutate({ id: p.id, on })} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager page={products.data.page} totalPages={products.data.totalPages} totalItems={products.data.totalItems} onPage={(p) => set({ page: String(p) })} />
        </Card>
      )}
    </div>
  )
}

const EMPTY: ProductInput = {
  title: '', description: '', categoryId: null, brandId: null, variantType: 'NONE', images: [''],
  variants: [{ sku: '', label: 'Standard', price: null, mrp: null, stock: null }], specs: [], tags: [],
  warranty: '', shippingInfo: '', returnPolicy: '', active: true,
}

const toInput = (p: AdminProduct): ProductInput => ({
  title: p.title, description: p.description, categoryId: p.categoryId, brandId: p.brandId, variantType: p.variantType,
  images: p.images, variants: p.variants.map((v) => ({ ...v })), specs: p.specs, tags: p.tags,
  warranty: p.warranty ?? '', shippingInfo: p.shippingInfo ?? '', returnPolicy: p.returnPolicy ?? '', active: p.active,
})

const VARIANT_TYPES: [VariantType, string][] = [['NONE', 'No options (one variant)'], ['SIZE', 'Sizes'], ['STORAGE', 'Storage'], ['CONFIGURATION', 'Configurations']]

/** Create or edit a product: details, images, variants (price, MRP, stock each), specifications and tags. */
function ProductForm({ initial, productId }: { initial: ProductInput; productId?: number }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useToast()
  const departments = useCategoryOptions()
  const brands = useQuery({ queryKey: ['admin', 'brands'], queryFn: getBrands })
  const [form, setForm] = useState<ProductInput>(initial)
  const [tags, setTags] = useState(initial.tags.join(', '))
  // Image previews follow the URL fields only when a field is left, not on every keystroke (no half-typed requests)
  const [previews, setPreviews] = useState<string[]>(initial.images)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: (body: ProductInput) => (productId ? updateProduct(productId, body) : createProduct(body)),
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['admin'] })
      queryClient.invalidateQueries({ queryKey: ['product', saved.slug] })
      toast(productId ? 'Product saved' : 'Product created')
      if (!productId) navigate(`/admin/products/${saved.id}`, { replace: true })
      else queryClient.setQueryData(['admin', 'product', productId], saved)
    },
    onError: (e) => {
      if (e instanceof ApiError) {
        setErrors(e.fieldErrors)
        setError(e.message)
      }
      window.scrollTo({ top: 0, behavior: 'smooth' })
    },
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setErrors({})
    setError(null)
    save.mutate({
      ...form,
      images: form.images.map((i) => i.trim()).filter(Boolean),
      tags: tags.split(',').map((t) => t.trim()).filter(Boolean),
      specs: form.specs.filter((s) => s.name.trim() || s.value.trim()),
    })
  }
  const setVariant = (i: number, key: string, value: string) =>
    setForm({ ...form, variants: form.variants.map((v, j) => (j === i ? { ...v, [key]: ['price', 'mrp', 'stock'].includes(key) ? (value === '' ? null : Number(value)) : value } : v)) })
  const err = (key: string) => errors[key]

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      {error && <FormError message={error} />}
      <Card>
        <h2 className="mb-4 font-semibold">Details</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Title" name="title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} error={err('title')} className="sm:col-span-2" />
          <div className="sm:col-span-2">
            <label htmlFor="description" className="mb-1 block text-sm font-medium text-slate-700">Description</label>
            <textarea id="description" rows={4} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })}
              className={`w-full rounded-md border px-3 py-2 text-sm ${err('description') ? 'border-red-400' : 'border-slate-300'}`} />
            {err('description') && <p className="mt-1 text-xs text-red-600">{err('description')}</p>}
          </div>
          <SelectField label="Category" name="categoryId" value={form.categoryId ?? ''} error={err('categoryId')}
            onChange={(e) => setForm({ ...form, categoryId: e.target.value ? Number(e.target.value) : null })}>
            <option value="">Choose a sub-category</option>
            {departments.map((d) => (
              <optgroup key={d.id} label={d.name}>
                {d.children.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </optgroup>
            ))}
          </SelectField>
          <SelectField label="Brand" name="brandId" value={form.brandId ?? ''} onChange={(e) => setForm({ ...form, brandId: e.target.value ? Number(e.target.value) : null })}>
            <option value="">No brand</option>
            {(brands.data ?? []).map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </SelectField>
          <Field label="Warranty" name="warranty" value={form.warranty} onChange={(e) => setForm({ ...form, warranty: e.target.value })} placeholder="1 year warranty" />
          <Field label="Shipping" name="shippingInfo" value={form.shippingInfo} onChange={(e) => setForm({ ...form, shippingInfo: e.target.value })} placeholder="Ships in 3-5 business days" />
          <Field label="Return policy" name="returnPolicy" value={form.returnPolicy} onChange={(e) => setForm({ ...form, returnPolicy: e.target.value })} placeholder="7 days return policy" />
          <Field label="Tags (comma separated)" name="tags" value={tags} onChange={(e) => setTags(e.target.value)} placeholder="cotton, t-shirt" />
          <label className="flex items-center gap-3 text-sm sm:col-span-2">
            <Toggle checked={form.active} label="Visible in the shop" onChange={(active) => setForm({ ...form, active })} /> Visible in the shop
          </label>
        </div>
      </Card>

      <Card>
        <h2 className="mb-1 font-semibold">Images</h2>
        <p className="mb-4 text-xs text-slate-500">Image URLs (http/https) or site paths. The first image is the thumbnail.</p>
        {err('images') && <p className="mb-2 text-xs text-red-600">{err('images')}</p>}
        <div className="space-y-2">
          {form.images.map((url, i) => (
            <div key={i} className="flex items-center gap-2">
              <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded bg-slate-50">
                {previews[i] && <img src={previews[i]} alt="" className="max-h-full max-w-full object-contain" />}
              </span>
              <input aria-label={`Image ${i + 1} URL`} value={url} onChange={(e) => setForm({ ...form, images: form.images.map((u, j) => (j === i ? e.target.value : u)) })}
                onBlur={() => setPreviews(form.images.map((u) => u.trim()))}
                className={`flex-1 rounded-md border px-3 py-2 text-sm ${err(`images[${i}]`) ? 'border-red-400' : 'border-slate-300'}`} placeholder="https://..." />
              <Button type="button" variant="ghost" disabled={form.images.length === 1} onClick={() => {
                setForm({ ...form, images: form.images.filter((_, j) => j !== i) })
                setPreviews(previews.filter((_, j) => j !== i))
              }}>Remove</Button>
            </div>
          ))}
        </div>
        {form.images.length < 10 && <Button type="button" variant="ghost" className="mt-2" onClick={() => setForm({ ...form, images: [...form.images, ''] })}>+ Add image</Button>}
      </Card>

      <Card>
        <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="font-semibold">Variants</h2>
            <p className="text-xs text-slate-500">Each variant has its own SKU, price, MRP and stock. The shop shows the cheapest price.</p>
          </div>
          <SelectField label="Options" name="variantType" value={form.variantType} className="w-56"
            onChange={(e) => {
              const variantType = e.target.value as VariantType
              setForm({ ...form, variantType, variants: variantType === 'NONE' ? [{ ...form.variants[0], label: 'Standard' }] : form.variants })
            }}>
            {VARIANT_TYPES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </SelectField>
        </div>
        {err('variants') && <p className="mb-2 text-xs text-red-600">{err('variants')}</p>}
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <thead><tr><th className={th}>Label</th><th className={th}>SKU</th><th className={th}>Price ₹</th><th className={th}>MRP ₹</th><th className={th}>Stock</th><th className={th} /></tr></thead>
            <tbody>
              {form.variants.map((v, i) => (
                <tr key={v.id ?? `new-${i}`}>
                  {(['label', 'sku', 'price', 'mrp', 'stock'] as const).map((key) => (
                    <td key={key} className="px-2 py-1.5">
                      <input aria-label={`Variant ${i + 1} ${key}`} value={v[key] ?? ''} disabled={key === 'label' && form.variantType === 'NONE'}
                        inputMode={['price', 'mrp', 'stock'].includes(key) ? 'numeric' : undefined}
                        onChange={(e) => setVariant(i, key, ['price', 'mrp', 'stock'].includes(key) ? e.target.value.replace(/[^\d]/g, '') : e.target.value)}
                        className={`w-full rounded border px-2 py-1.5 disabled:bg-slate-50 ${err(`variants[${i}].${key}`) ? 'border-red-400' : 'border-slate-300'}`} />
                      {err(`variants[${i}].${key}`) && <p className="mt-0.5 text-[11px] text-red-600">{err(`variants[${i}].${key}`)}</p>}
                    </td>
                  ))}
                  <td className="px-2 py-1.5">
                    <Button type="button" variant="ghost" disabled={form.variants.length === 1} onClick={() => setForm({ ...form, variants: form.variants.filter((_, j) => j !== i) })}>Remove</Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {form.variantType !== 'NONE' && form.variants.length < 20 && (
          <Button type="button" variant="ghost" className="mt-2"
            onClick={() => setForm({ ...form, variants: [...form.variants, { sku: '', label: '', price: null, mrp: null, stock: null }] })}>
            + Add variant
          </Button>
        )}
      </Card>

      <Card>
        <h2 className="mb-4 font-semibold">Specifications</h2>
        <div className="space-y-2">
          {form.specs.map((s, i) => (
            <div key={i} className="flex gap-2">
              <input aria-label={`Specification ${i + 1} name`} value={s.name} placeholder="Name, e.g. Fabric" onChange={(e) => setForm({ ...form, specs: form.specs.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)) })}
                className="w-48 rounded-md border border-slate-300 px-3 py-2 text-sm" />
              <input aria-label={`Specification ${i + 1} value`} value={s.value} placeholder="Value, e.g. 100% cotton" onChange={(e) => setForm({ ...form, specs: form.specs.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)) })}
                className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm" />
              <Button type="button" variant="ghost" onClick={() => setForm({ ...form, specs: form.specs.filter((_, j) => j !== i) })}>Remove</Button>
            </div>
          ))}
        </div>
        <Button type="button" variant="ghost" className="mt-2" onClick={() => setForm({ ...form, specs: [...form.specs, { name: '', value: '' }] })}>+ Add specification</Button>
      </Card>

      <div className="sticky bottom-0 flex gap-3 border-t border-slate-200 bg-slate-100/95 py-3 backdrop-blur">
        <Button type="submit" variant="accent" busy={save.isPending}>{productId ? 'Save changes' : 'Create product'}</Button>
        <Link to="/admin/products"><Button type="button" variant="outline">Back to products</Button></Link>
      </div>
    </form>
  )
}

export function ProductEditPage() {
  const { id } = useParams()
  const productId = id === 'new' ? undefined : Number(id)
  const product = useQuery({ queryKey: ['admin', 'product', productId], queryFn: () => getAdminProduct(productId!), enabled: !!productId })
  if (productId && product.isPending) return <PageSpinner />
  if (productId && product.isError) return <ErrorState message={errorMessage(product.error)} />
  const p = product.data
  return (
    <div>
      <PageHeader title={p ? p.title : 'New product'}
        actions={p && <a href={`/p/${p.slug}`} target="_blank" rel="noreferrer" className="text-sm font-semibold text-brand-700">{p.active ? 'View in shop ↗' : 'Hidden from the shop'}</a>} />
      <ProductForm key={productId ?? 'new'} initial={p ? toInput(p) : EMPTY} productId={productId} />
    </div>
  )
}
