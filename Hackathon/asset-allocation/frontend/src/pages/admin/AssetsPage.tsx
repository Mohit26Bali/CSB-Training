import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi, metaApi } from '../../api/endpoints'
import type { Asset, AssetInput, AssetStatus } from '../../api/types'
import { AssetPhoto } from '../../components/AssetPhoto'
import { useToast } from '../../components/Toast'
import { useDebounced } from '../../hooks'
import { Button, EmptyState, ErrorState, Field, formatDate, formatDay, formatMoney, LoadingState, Modal, PageHeader, SelectField, StatusBadge, statusLabel, TextAreaField } from '../../components/ui'

const statuses: AssetStatus[] = ['AVAILABLE', 'ASSIGNED', 'RETURN_REQUESTED', 'IN_REPAIR', 'RETIRED']

type Dialog = { kind: 'form'; asset: Asset | null } | { kind: 'assign'; asset: Asset } | { kind: 'history'; asset: Asset } | { kind: 'status'; asset: Asset; to: AssetStatus }

export function AssetsPage() {
  const [status, setStatus] = useState('')
  const [category, setCategory] = useState('')
  const [search, setSearch] = useState('')
  const q = useDebounced(search)
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const meta = useQuery({ queryKey: ['meta'], queryFn: metaApi.meta })
  const list = useQuery({ queryKey: ['admin', 'assets', status, category, q], queryFn: () => adminApi.assets(status, category, q), placeholderData: (previous) => previous })

  return (
    <>
      <PageHeader title="Assets" subtitle="Search by tag, serial number, name or the person holding it." actions={<Button onClick={() => setDialog({ kind: 'form', asset: null })}>New asset</Button>} />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="w-64">
          <Field label="Search" placeholder="LT-1001, serial, name, holder…" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <div className="w-44">
          <SelectField label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All</option>
            {statuses.map((s) => (
              <option key={s} value={s}>
                {statusLabel(s)}
              </option>
            ))}
          </SelectField>
        </div>
        <div className="w-44">
          <SelectField label="Category" value={category} onChange={(e) => setCategory(e.target.value)}>
            <option value="">All</option>
            {(meta.data?.categories ?? []).map((c) => (
              <option key={c} value={c}>
                {statusLabel(c)}
              </option>
            ))}
          </SelectField>
        </div>
        {(status || category || search) && (
          <Button
            variant="secondary"
            small
            onClick={() => {
              setStatus('')
              setCategory('')
              setSearch('')
            }}
          >
            Clear
          </Button>
        )}
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="No assets match" hint="Check the tag or serial number, or clear the filters." />
      ) : (
        <>
          <p className="mb-2 text-sm text-slate-600">
            {list.data.length} asset{list.data.length === 1 ? '' : 's'}
          </p>
          <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {list.data.map((a) => (
              <li key={a.id} className={`flex overflow-hidden rounded-xl border bg-white shadow-sm ${a.status === 'RETIRED' ? 'border-slate-200 opacity-70' : 'border-slate-200'}`}>
                <AssetPhoto category={a.category} className="w-24 shrink-0" />
                <div className="min-w-0 flex-1 space-y-1.5 p-3">
                  <div className="flex items-start justify-between gap-2">
                    <h2 className="truncate font-semibold text-slate-900">{a.name}</h2>
                    <StatusBadge status={a.status} />
                  </div>
                  <p className="font-mono text-xs text-slate-500">
                    {a.assetTag} · S/N {a.serialNumber}
                  </p>
                  <p className="flex items-center gap-2 text-xs text-slate-600">
                    {statusLabel(a.category)} <StatusBadge status={a.condition} />
                  </p>
                  {a.holderName && (
                    <p className="text-xs text-slate-700">
                      With <span className="font-medium">{a.holderName}</span> since {formatDay((a.heldSince ?? '').slice(0, 10))}
                    </p>
                  )}
                  <div className="flex flex-wrap gap-1.5 pt-1">
                    {a.status === 'AVAILABLE' && (
                      <Button small onClick={() => setDialog({ kind: 'assign', asset: a })}>
                        Assign
                      </Button>
                    )}
                    <Button small variant="secondary" onClick={() => setDialog({ kind: 'history', asset: a })}>
                      History
                    </Button>
                    {a.status !== 'RETIRED' && (
                      <Button small variant="secondary" onClick={() => setDialog({ kind: 'form', asset: a })}>
                        Edit
                      </Button>
                    )}
                    {a.status === 'AVAILABLE' && (
                      <Button small variant="secondary" onClick={() => setDialog({ kind: 'status', asset: a, to: 'IN_REPAIR' })}>
                        To repair
                      </Button>
                    )}
                    {a.status === 'IN_REPAIR' && (
                      <Button small variant="secondary" onClick={() => setDialog({ kind: 'status', asset: a, to: 'AVAILABLE' })}>
                        Repaired
                      </Button>
                    )}
                    {(a.status === 'AVAILABLE' || a.status === 'IN_REPAIR') && (
                      <Button small variant="secondary" onClick={() => setDialog({ kind: 'status', asset: a, to: 'RETIRED' })}>
                        Retire
                      </Button>
                    )}
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
      {dialog?.kind === 'form' && <AssetFormModal asset={dialog.asset} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'assign' && <AssignModal asset={dialog.asset} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'history' && <HistoryModal asset={dialog.asset} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'status' && <StatusModal asset={dialog.asset} to={dialog.to} onClose={() => setDialog(null)} />}
    </>
  )
}

function AssetFormModal({ asset, onClose }: { asset: Asset | null; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const meta = useQuery({ queryKey: ['meta'], queryFn: metaApi.meta })
  const [form, setForm] = useState<AssetInput>({
    assetTag: asset?.assetTag ?? '',
    serialNumber: asset?.serialNumber ?? '',
    name: asset?.name ?? '',
    category: asset?.category ?? '',
    brand: asset?.brand ?? '',
    model: asset?.model ?? '',
    purchaseDate: asset?.purchaseDate ?? '',
    purchasePrice: asset?.purchasePrice ?? '',
    condition: asset?.condition ?? 'NEW',
    notes: asset?.notes ?? '',
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => (asset ? adminApi.updateAsset(asset.id, form) : adminApi.createAsset(form)),
    onSuccess: (saved) => {
      toast.success(asset ? `Saved ${saved.assetTag}` : `Added ${saved.assetTag}`)
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
  const set = (key: keyof AssetInput) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    save.mutate()
  }

  return (
    <Modal title={asset ? `Edit ${asset.assetTag}` : 'New asset'} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <div className="grid grid-cols-2 gap-4">
          <Field label="Asset tag" placeholder="LT-1001" maxLength={11} value={form.assetTag} onChange={set('assetTag')} error={fieldErrors.assetTag} />
          <Field label="Serial number" maxLength={30} value={form.serialNumber} onChange={set('serialNumber')} error={fieldErrors.serialNumber} />
        </div>
        <Field label="Name" placeholder="Dell Latitude 5440" maxLength={80} value={form.name} onChange={set('name')} error={fieldErrors.name} />
        <div className="grid grid-cols-2 gap-4">
          <SelectField label="Category" value={form.category} onChange={set('category')} error={fieldErrors.category}>
            <option value="">Choose…</option>
            {(meta.data?.categories ?? []).map((c) => (
              <option key={c} value={c}>
                {statusLabel(c)}
              </option>
            ))}
          </SelectField>
          <SelectField label="Condition" value={form.condition} onChange={set('condition')} error={fieldErrors.condition}>
            {(meta.data?.conditions ?? ['NEW', 'GOOD', 'FAIR', 'POOR']).map((c) => (
              <option key={c} value={c}>
                {statusLabel(c)}
              </option>
            ))}
          </SelectField>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Brand" maxLength={40} value={form.brand} onChange={set('brand')} error={fieldErrors.brand} />
          <Field label="Model" maxLength={40} value={form.model} onChange={set('model')} error={fieldErrors.model} />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Purchase date" type="date" value={form.purchaseDate} onChange={set('purchaseDate')} error={fieldErrors.purchaseDate} />
          <Field label="Price (₹)" type="number" min={0} value={form.purchasePrice} onChange={set('purchasePrice')} error={fieldErrors.purchasePrice} />
        </div>
        <TextAreaField label="Notes" rows={2} maxLength={300} value={form.notes} onChange={set('notes')} error={fieldErrors.notes} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {asset ? 'Save changes' : 'Add asset'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function AssignModal({ asset, onClose }: { asset: Asset; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const q = useDebounced(search)
  const [employee, setEmployee] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [note, setNote] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const people = useQuery({ queryKey: ['admin', 'employees', q], queryFn: () => adminApi.employees(q), placeholderData: (previous) => previous })

  const assign = useMutation({
    mutationFn: () => adminApi.assign(asset.id, employee, dueDate, note),
    onSuccess: (a) => {
      toast.success(`${a.assetTag} assigned to ${a.employeeName}`)
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
        queryClient.invalidateQueries()
      }
    },
  })

  return (
    <Modal title={`Assign ${asset.assetTag}`} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          {asset.name} · {statusLabel(asset.condition)} condition
        </p>
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="Find the employee" placeholder="Name or username…" value={search} onChange={(e) => setSearch(e.target.value)} error={fieldErrors.employeeUsername} />
        <ul className="max-h-48 divide-y divide-slate-100 overflow-y-auto rounded-lg border border-slate-200">
          {(people.data ?? []).map((p) => (
            <li key={p.id}>
              <button
                type="button"
                aria-pressed={employee === p.username}
                onClick={() => setEmployee(p.username)}
                className={`flex w-full items-center justify-between px-3 py-2 text-left text-sm ${employee === p.username ? 'bg-brand-50 font-semibold text-brand-900' : 'hover:bg-slate-50'}`}
              >
                <span>
                  {p.fullname} <span className="text-xs text-slate-500">{p.username}</span>
                </span>
                <span className="text-xs text-slate-500">{p.activeAssets} held</span>
              </button>
            </li>
          ))}
        </ul>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Due back (optional)" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} error={fieldErrors.dueDate} />
          <Field label="Note (optional)" maxLength={300} value={note} onChange={(e) => setNote(e.target.value)} error={fieldErrors.note} />
        </div>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={assign.isPending}>
            Cancel
          </Button>
          <Button loading={assign.isPending} onClick={() => assign.mutate()}>
            {employee ? `Assign to ${employee}` : 'Assign'}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

function HistoryModal({ asset, onClose }: { asset: Asset; onClose: () => void }) {
  const history = useQuery({ queryKey: ['admin', 'assets', asset.id, 'history'], queryFn: () => adminApi.history(asset.id) })
  return (
    <Modal title={`${asset.assetTag} · assignment history`} onClose={onClose}>
      <p className="mb-3 text-sm text-slate-600">
        {asset.name} · S/N {asset.serialNumber}
        {asset.purchasePrice != null && ` · bought for ${formatMoney(asset.purchasePrice)}`}
      </p>
      {history.isPending ? (
        <LoadingState />
      ) : history.isError ? (
        <ErrorState message={errorMessage(history.error)} />
      ) : history.data.length === 0 ? (
        <p className="text-sm text-slate-600">This asset has never been assigned.</p>
      ) : (
        <ol className="space-y-4 border-l-2 border-slate-200 pl-4">
          {history.data.map((a) => (
            <li key={a.id} className="relative">
              <span className={`absolute -left-[1.4rem] top-1.5 size-3 rounded-full ${a.status === 'RETURNED' ? 'bg-slate-400' : 'bg-brand-600'}`} />
              <p className="font-medium text-slate-900">
                {a.employeeName} <StatusBadge status={a.status} />
              </p>
              <p className="text-sm text-slate-600">
                From {formatDay(a.assignedAt.slice(0, 10))} ({statusLabel(a.conditionOut)} out)
                {a.returnedAt && ` to ${formatDay(a.returnedAt.slice(0, 10))} (${statusLabel(a.conditionIn ?? '')} back)`}
              </p>
              {a.returnRequestedAt && a.status === 'RETURN_REQUESTED' && <p className="text-xs text-amber-700">Return requested {formatDate(a.returnRequestedAt)}</p>}
              {a.receiveNote && <p className="text-xs text-slate-500">{a.receiveNote}</p>}
            </li>
          ))}
        </ol>
      )}
    </Modal>
  )
}

function StatusModal({ asset, to, onClose }: { asset: Asset; to: AssetStatus; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const change = useMutation({
    mutationFn: () => adminApi.setStatus(asset.id, to),
    onSuccess: (a) => {
      toast.success(`${a.assetTag} is now ${statusLabel(a.status).toLowerCase()}`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      toast.error(errorMessage(err))
      queryClient.invalidateQueries()
      onClose()
    },
  })
  const text: Record<string, string> = {
    IN_REPAIR: 'It will not be offered for assignment until it is repaired.',
    AVAILABLE: 'It goes back into the pool and can be assigned again.',
    RETIRED: 'A retired asset can never be used again. This cannot be undone.',
  }
  return (
    <Modal title={`${statusLabel(to)}: ${asset.assetTag}?`} onClose={onClose}>
      <p className="text-sm text-slate-600">{text[to]}</p>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose} disabled={change.isPending}>
          Cancel
        </Button>
        <Button variant={to === 'RETIRED' ? 'danger' : 'primary'} loading={change.isPending} onClick={() => change.mutate()}>
          Yes, {statusLabel(to).toLowerCase()}
        </Button>
      </div>
    </Modal>
  )
}
