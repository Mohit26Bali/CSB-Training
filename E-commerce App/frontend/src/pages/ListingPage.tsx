import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { getCategory, searchProducts } from '../api/endpoints'
import type { FacetOption, SearchFacets } from '../api/types'
import { ProductCard } from '../components/ProductCard'
import { Button, EmptyState, ErrorState, PageSpinner } from '../components/ui'
import { errorMessage } from '../hooks'

const SORTS = [
  ['relevance', 'Relevance'],
  ['popularity', 'Popularity'],
  ['price_asc', 'Price: low to high'],
  ['price_desc', 'Price: high to low'],
  ['discount', 'Discount'],
  ['rating', 'Customer rating'],
  ['newest', 'Newest first'],
] as const

/** Filters that live in the URL, so a filtered page can be bookmarked, shared and navigated back to. */
const FILTER_KEYS = ['brand', 'sizes', 'minPrice', 'maxPrice', 'minRating', 'minDiscount', 'inStock'] as const

function useListParams() {
  const [params, setParams] = useSearchParams()
  const list = (key: string) => (params.get(key) ?? '').split(',').filter(Boolean)
  const update = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)))
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }
  const toggleInList = (key: string, value: string) => {
    const current = list(key)
    const next = current.includes(value) ? current.filter((v) => v !== value) : [...current, value]
    update({ [key]: next.join(',') || null })
  }
  return { params, list, update, toggleInList }
}

function FilterGroup({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="border-b border-slate-100 px-4 py-4">
      <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-slate-500">{title}</p>
      {children}
    </div>
  )
}

function PriceFilter() {
  const { params, update } = useListParams()
  const [min, setMin] = useState(params.get('minPrice') ?? '')
  const [max, setMax] = useState(params.get('maxPrice') ?? '')
  const apply = (e: FormEvent) => {
    e.preventDefault()
    update({ minPrice: min || null, maxPrice: max || null })
  }
  return (
    <form onSubmit={apply} className="flex items-center gap-2">
      <input aria-label="Minimum price" inputMode="numeric" value={min} onChange={(e) => setMin(e.target.value.replace(/\D/g, ''))}
        placeholder="Min ₹" className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm" />
      <span className="text-slate-400">–</span>
      <input aria-label="Maximum price" inputMode="numeric" value={max} onChange={(e) => setMax(e.target.value.replace(/\D/g, ''))}
        placeholder="Max ₹" className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm" />
      <button type="submit" className="rounded bg-brand-600 px-2.5 py-1.5 text-xs font-semibold text-white hover:bg-brand-700">Go</button>
    </form>
  )
}

function CheckList({ name, options, selected, onToggle }: { name: string; options: FacetOption[]; selected: string[]; onToggle: (v: string) => void }) {
  const [showAll, setShowAll] = useState(false)
  const visible = showAll ? options : options.slice(0, 7)
  return (
    <div className="space-y-1.5">
      {visible.map((o) => (
        <label key={o.value} className="flex cursor-pointer items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" name={name} checked={selected.includes(o.value)} onChange={() => onToggle(o.value)} className="h-4 w-4 accent-brand-600" />
          <span className="flex-1">{o.label}</span>
          <span className="text-xs text-slate-400">{o.count}</span>
        </label>
      ))}
      {options.length > 7 && (
        <button type="button" onClick={() => setShowAll((s) => !s)} className="text-xs font-semibold text-brand-700">
          {showAll ? 'Show less' : `${options.length - 7} more`}
        </button>
      )}
    </div>
  )
}

function RadioList({ name, options, value, onChange }: { name: string; options: FacetOption[]; value: string | null; onChange: (v: string | null) => void }) {
  return (
    <div className="space-y-1.5">
      {options.map((o) => (
        <label key={o.value} className="flex cursor-pointer items-center gap-2 text-sm text-slate-700">
          <input type="radio" name={name} checked={value === o.value} onChange={() => onChange(o.value)} className="h-4 w-4 accent-brand-600" />
          <span className="flex-1">{o.label}</span>
          <span className="text-xs text-slate-400">{o.count}</span>
        </label>
      ))}
      {value && (
        <button type="button" onClick={() => onChange(null)} className="text-xs font-semibold text-brand-700">Clear</button>
      )}
    </div>
  )
}

function Filters({ facets, onSearchPage }: { facets: SearchFacets; onSearchPage: boolean }) {
  const { params, list, update, toggleInList } = useListParams()
  const active = FILTER_KEYS.some((k) => params.get(k)) || (onSearchPage && params.get('category'))
  return (
    <div className="rounded-lg bg-white shadow-sm">
      <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
        <p className="font-semibold text-slate-900">Filters</p>
        {active && (
          <button className="text-xs font-semibold text-brand-700" onClick={() => {
            const cleared: Record<string, null> = { category: null }
            FILTER_KEYS.forEach((k) => (cleared[k] = null))
            update(onSearchPage ? cleared : { ...cleared, category: params.get('category') })
          }}>
            Clear all
          </button>
        )}
      </div>
      {onSearchPage && facets.categories.length > 1 && (
        <FilterGroup title="Category">
          <RadioList name="category" options={facets.categories} value={params.get('category')} onChange={(v) => update({ category: v })} />
        </FilterGroup>
      )}
      {facets.price && (
        <FilterGroup title="Price">
          <PriceFilter key={`${params.get('minPrice')}-${params.get('maxPrice')}`} />
        </FilterGroup>
      )}
      {facets.brands.length > 0 && (
        <FilterGroup title="Brand">
          <CheckList name="brand" options={facets.brands} selected={list('brand')} onToggle={(v) => toggleInList('brand', v)} />
        </FilterGroup>
      )}
      {facets.sizes.length > 0 && (
        <FilterGroup title="Size">
          <div className="flex flex-wrap gap-2">
            {facets.sizes.map((s) => {
              const on = list('sizes').includes(s.value)
              return (
                <button key={s.value} type="button" aria-pressed={on} onClick={() => toggleInList('sizes', s.value)}
                  className={`rounded-full border px-3 py-1 text-xs font-medium ${on ? 'border-brand-600 bg-brand-600 text-white' : 'border-slate-300 text-slate-700 hover:border-brand-500'}`}>
                  {s.label}
                </button>
              )
            })}
          </div>
        </FilterGroup>
      )}
      {facets.ratings.length > 0 && (
        <FilterGroup title="Customer ratings">
          <RadioList name="rating" options={facets.ratings} value={params.get('minRating')} onChange={(v) => update({ minRating: v })} />
        </FilterGroup>
      )}
      {facets.discounts.length > 0 && (
        <FilterGroup title="Discount">
          <RadioList name="discount" options={facets.discounts} value={params.get('minDiscount')} onChange={(v) => update({ minDiscount: v })} />
        </FilterGroup>
      )}
      <FilterGroup title="Availability">
        <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={params.get('inStock') === 'true'} onChange={(e) => update({ inStock: e.target.checked ? 'true' : null })}
            className="h-4 w-4 accent-brand-600" />
          Exclude out of stock
        </label>
      </FilterGroup>
    </div>
  )
}

function Pagination({ page, totalPages }: { page: number; totalPages: number }) {
  const { update } = useListParams()
  if (totalPages <= 1) return null
  const pages = Array.from({ length: totalPages }, (_, i) => i).filter((i) => Math.abs(i - page) <= 2 || i === 0 || i === totalPages - 1)
  const go = (p: number) => {
    update({ page: p === 0 ? null : String(p) })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  return (
    <nav aria-label="Pages" className="mt-6 flex items-center justify-center gap-1">
      <Button variant="outline" disabled={page === 0} onClick={() => go(page - 1)}>Previous</Button>
      {pages.map((p, i) => (
        <span key={p} className="flex items-center">
          {i > 0 && p - pages[i - 1] > 1 && <span className="px-1 text-slate-400">…</span>}
          <button onClick={() => go(p)} aria-current={p === page ? 'page' : undefined}
            className={`h-9 w-9 rounded-full text-sm font-medium ${p === page ? 'bg-brand-600 text-white' : 'text-slate-700 hover:bg-slate-200'}`}>
            {p + 1}
          </button>
        </span>
      ))}
      <Button variant="outline" disabled={page >= totalPages - 1} onClick={() => go(page + 1)}>Next</Button>
    </nav>
  )
}

/** /c/:slug (a category) and /search?q= (text search): product grid with filters, sorting and paging. */
export function ListingPage() {
  const { slug } = useParams()
  const { params, update } = useListParams()
  const [filtersOpen, setFiltersOpen] = useState(false)
  const q = params.get('q') ?? ''
  const onSearchPage = !slug

  const query = new URLSearchParams()
  if (slug) query.set('category', slug)
  params.forEach((value, key) => {
    if (key !== 'category' || onSearchPage) query.set(key, value)
  })
  query.set('size', '24')

  const category = useQuery({ queryKey: ['category', slug], queryFn: () => getCategory(slug!), enabled: !!slug })
  const products = useQuery({
    queryKey: ['products', query.toString()],
    queryFn: () => searchProducts(query.toString()),
    placeholderData: keepPreviousData,
  })

  if (products.isPending) return <PageSpinner />
  if (products.isError) return <ErrorState message={errorMessage(products.error)} onRetry={() => products.refetch()} />
  const data = products.data
  const heading = slug ? category.data?.name ?? '' : q ? `Results for "${q}"` : 'All products'
  const sorts = SORTS.filter(([value]) => value !== 'relevance' || q)

  return (
    <div>
      {category.data && (
        <nav aria-label="Breadcrumb" className="mb-3 text-xs text-slate-500">
          <Link to="/" className="hover:text-brand-700">Home</Link>
          {category.data.breadcrumb.map((b) => (
            <span key={b.slug}>
              {' › '}
              <Link to={`/c/${b.slug}`} className="hover:text-brand-700">{b.name}</Link>
            </span>
          ))}
        </nav>
      )}

      <div className="grid gap-4 lg:grid-cols-[260px_1fr]">
        <aside className={`${filtersOpen ? 'block' : 'hidden'} lg:block`}>
          <Filters facets={data.facets} onSearchPage={onSearchPage} />
        </aside>

        <div className="min-w-0">
          <div className="rounded-lg bg-white p-4 shadow-sm">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h1 className="text-lg font-semibold text-slate-900">
                {heading}
                <span className="ml-2 text-sm font-normal text-slate-500">
                  {data.totalItems.toLocaleString('en-IN')} {data.totalItems === 1 ? 'product' : 'products'}
                </span>
              </h1>
              <Button variant="outline" className="lg:hidden" onClick={() => setFiltersOpen((o) => !o)} aria-expanded={filtersOpen}>
                {filtersOpen ? 'Hide filters' : 'Filters'}
              </Button>
            </div>
            {category.data && category.data.children.length > 0 && (
              <div className="mt-3 flex flex-wrap gap-2">
                {category.data.children.map((c) => (
                  <Link key={c.slug} to={`/c/${c.slug}`} className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-brand-100 hover:text-brand-800">
                    {c.name} <span className="text-slate-400">({c.productCount})</span>
                  </Link>
                ))}
              </div>
            )}
            <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-slate-100 pt-3 text-sm" role="group" aria-label="Sort by">
              <span className="font-medium text-slate-700">Sort by</span>
              {sorts.map(([value, label]) => (
                <button key={value} onClick={() => update({ sort: value })} aria-pressed={data.sort === value}
                  className={data.sort === value ? 'border-b-2 border-brand-600 pb-0.5 font-semibold text-brand-700' : 'text-slate-600 hover:text-brand-700'}>
                  {label}
                </button>
              ))}
            </div>
          </div>

          {data.items.length === 0 ? (
            <div className="mt-4">
              <EmptyState title="No products found" text="Try a different search or remove some filters." />
            </div>
          ) : (
            <div className={`mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-4 ${products.isFetching ? 'opacity-60' : ''}`}>
              {data.items.map((p) => (
                <ProductCard key={p.id} product={p} />
              ))}
            </div>
          )}
          <Pagination page={data.page} totalPages={data.totalPages} />
        </div>
      </div>
    </div>
  )
}
