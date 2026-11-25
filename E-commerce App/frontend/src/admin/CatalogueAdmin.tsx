import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  createBrand, createCategory, deleteBrand, deleteCategory, getAdminCategories, getBrands, renameBrand, renameCategory,
} from '../api/admin'
import type { CategoryNode } from '../api/types'
import { useToast } from '../components/Toast'
import { Button, Card, ErrorState, PageSpinner } from '../components/ui'
import { errorMessage } from '../hooks'
import { plural } from '../lib/format'
import { PageHeader } from './AdminLayout'

/** A name with Rename / Delete actions; renaming happens in place. */
function EditableRow({ name, meta, onRename, onDelete, extra }: {
  name: string; meta: string; onRename: (n: string) => void; onDelete: () => void; extra?: React.ReactNode
}) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState(name)
  if (editing) {
    return (
      <form className="flex flex-1 gap-2" onSubmit={(e) => { e.preventDefault(); onRename(value.trim()); setEditing(false) }}>
        <input aria-label="New name" value={value} autoFocus onChange={(e) => setValue(e.target.value)} className="flex-1 rounded border border-slate-300 px-2 py-1 text-sm" />
        <Button type="submit" className="px-3 py-1">Save</Button>
        <Button type="button" variant="outline" className="px-3 py-1" onClick={() => setEditing(false)}>Cancel</Button>
      </form>
    )
  }
  return (
    <div className="flex flex-1 flex-wrap items-center gap-2">
      <span className="font-medium text-slate-800">{name}</span>
      <span className="text-xs text-slate-500">{meta}</span>
      <span className="ml-auto flex gap-1">
        {extra}
        <Button variant="ghost" className="px-2 py-1" onClick={() => setEditing(true)}>Rename</Button>
        <Button variant="ghost" className="px-2 py-1 text-red-600" onClick={() => window.confirm(`Delete "${name}"?`) && onDelete()}>Delete</Button>
      </span>
    </div>
  )
}

function useAction(invalidate: string) {
  const queryClient = useQueryClient()
  const toast = useToast()
  return useMutation({
    mutationFn: ({ run }: { run: () => Promise<unknown>; done: string }) => run(),
    onSuccess: (_, { done }) => {
      queryClient.invalidateQueries({ queryKey: ['admin', invalidate] })
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      toast(done)
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })
}

function AddForm({ placeholder, onAdd }: { placeholder: string; onAdd: (name: string) => void }) {
  const [name, setName] = useState('')
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!name.trim()) return
    onAdd(name.trim())
    setName('')
  }
  return (
    <form onSubmit={submit} className="flex gap-2">
      <input aria-label={placeholder} value={name} onChange={(e) => setName(e.target.value)} placeholder={placeholder} className="flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm" />
      <Button type="submit" variant="outline" className="py-1.5">Add</Button>
    </form>
  )
}

function Department({ dept, act }: { dept: CategoryNode; act: ReturnType<typeof useAction> }) {
  const [adding, setAdding] = useState(false)
  return (
    <li className="rounded-md border border-slate-200">
      <div className="flex items-center bg-slate-50 px-3 py-2">
        <EditableRow name={dept.name} meta={`${plural(dept.children.length, 'sub-category', 'sub-categories')} · ${plural(dept.productCount, 'product')}`}
          onRename={(name) => act.mutate({ run: () => renameCategory(dept.id, name), done: 'Category renamed' })}
          onDelete={() => act.mutate({ run: () => deleteCategory(dept.id), done: 'Category deleted' })}
          extra={<Button variant="ghost" className="px-2 py-1" onClick={() => setAdding((a) => !a)}>+ Sub-category</Button>} />
      </div>
      <ul className="divide-y divide-slate-100">
        {dept.children.map((c) => (
          <li key={c.id} className="flex items-center py-1.5 pl-8 pr-3">
            <EditableRow name={c.name} meta={`${plural(c.productCount, 'product')} · /c/${c.slug}`}
              onRename={(name) => act.mutate({ run: () => renameCategory(c.id, name), done: 'Category renamed' })}
              onDelete={() => act.mutate({ run: () => deleteCategory(c.id), done: 'Category deleted' })} />
          </li>
        ))}
        {adding && (
          <li className="py-2 pl-8 pr-3">
            <AddForm placeholder={`New sub-category of ${dept.name}`}
              onAdd={(name) => act.mutate({ run: () => createCategory({ name, parentId: dept.id }), done: 'Sub-category added' })} />
          </li>
        )}
      </ul>
    </li>
  )
}

export function CatalogueAdminPage() {
  const categories = useQuery({ queryKey: ['admin', 'categories'], queryFn: getAdminCategories })
  const brands = useQuery({ queryKey: ['admin', 'brands'], queryFn: getBrands })
  const categoryAction = useAction('categories')
  const brandAction = useAction('brands')
  const [brandFilter, setBrandFilter] = useState('')

  if (categories.isPending || brands.isPending) return <PageSpinner />
  if (categories.isError) return <ErrorState message={errorMessage(categories.error)} />
  const shownBrands = (brands.data ?? []).filter((b) => b.name.toLowerCase().includes(brandFilter.toLowerCase()))

  return (
    <div>
      <PageHeader title="Categories & brands" />
      <p className="-mt-3 mb-4 text-sm text-slate-500">
        Products sit in sub-categories. Anything that still has products (or sub-categories) cannot be deleted.
      </p>
      <div className="grid gap-4 xl:grid-cols-[3fr_2fr]">
        <Card>
          <h2 className="mb-3 font-semibold">Categories</h2>
          <ul className="space-y-3">
            {categories.data.map((d) => <Department key={d.id} dept={d} act={categoryAction} />)}
          </ul>
          <div className="mt-4 border-t border-slate-100 pt-4">
            <AddForm placeholder="New department (top-level category)"
              onAdd={(name) => categoryAction.mutate({ run: () => createCategory({ name, parentId: null }), done: 'Department added' })} />
          </div>
        </Card>
        <Card>
          <h2 className="mb-3 font-semibold">Brands <span className="text-sm font-normal text-slate-500">({brands.data?.length})</span></h2>
          <AddForm placeholder="New brand" onAdd={(name) => brandAction.mutate({ run: () => createBrand(name), done: 'Brand added' })} />
          <input aria-label="Filter brands" value={brandFilter} onChange={(e) => setBrandFilter(e.target.value)} placeholder="Filter brands"
            className="mt-3 w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm" />
          <ul className="mt-2 max-h-[600px] divide-y divide-slate-100 overflow-y-auto">
            {shownBrands.map((b) => (
              <li key={b.id} className="flex items-center py-1.5">
                <EditableRow name={b.name} meta={plural(b.productCount, 'product')}
                  onRename={(name) => brandAction.mutate({ run: () => renameBrand(b.id, name), done: 'Brand renamed' })}
                  onDelete={() => brandAction.mutate({ run: () => deleteBrand(b.id), done: 'Brand deleted' })} />
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </div>
  )
}
