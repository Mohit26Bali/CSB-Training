import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi, metaApi } from '../../api/endpoints'
import type { Assignment } from '../../api/types'
import { AssetPhoto } from '../../components/AssetPhoto'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, formatDate, formatDay, LoadingState, Modal, PageHeader, SelectField, StatusBadge, statusLabel, TextAreaField } from '../../components/ui'

const tabs = [
  { key: 'RETURN_REQUESTED', label: 'Return requests' },
  { key: 'ASSIGNED', label: 'Out with staff' },
] as const

export function ReturnsPage() {
  const [tab, setTab] = useState<'RETURN_REQUESTED' | 'ASSIGNED'>('RETURN_REQUESTED')
  const [accepting, setAccepting] = useState<Assignment | null>(null)
  const list = useQuery({ queryKey: ['admin', 'assignments', tab], queryFn: () => adminApi.assignments(tab), placeholderData: (previous) => previous })

  return (
    <>
      <PageHeader title="Returns" subtitle="Check in equipment employees have handed back. Accepting puts the asset back into the available pool." />
      <div role="tablist" aria-label="Return queue or everything out" className="mb-4 flex gap-1">
        {tabs.map((t) => (
          <button key={t.key} role="tab" aria-selected={tab === t.key} onClick={() => setTab(t.key)} className={`rounded-lg px-3 py-1.5 text-sm font-medium ${tab === t.key ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
            {t.label}
          </button>
        ))}
      </div>
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title={tab === 'RETURN_REQUESTED' ? 'No return requests' : 'Nothing is out with staff'} hint={tab === 'RETURN_REQUESTED' ? 'Requests appear here as employees raise them.' : undefined} />
      ) : (
        <ul className="space-y-3">
          {list.data.map((a) => (
            <li key={a.id} className="flex overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
              <AssetPhoto category={a.category} className="hidden w-28 shrink-0 sm:block" />
              <div className="flex min-w-0 flex-1 flex-wrap items-start justify-between gap-3 p-4">
                <div className="min-w-0 space-y-1">
                  <h2 className="font-semibold text-slate-900">
                    {a.assetName} <span className="font-mono text-xs font-normal text-slate-500">{a.assetTag}</span>
                  </h2>
                  <p className="text-sm text-slate-700">
                    {a.employeeName} <span className="text-xs text-slate-500">({a.employeeUsername})</span> · since {formatDay(a.assignedAt.slice(0, 10))}
                  </p>
                  <p className="flex flex-wrap items-center gap-2 text-sm text-slate-600">
                    <StatusBadge status={a.status} /> went out as <StatusBadge status={a.conditionOut} />
                    {a.overdue && <span className="rounded-full bg-rose-100 px-2.5 py-0.5 text-xs font-semibold text-rose-800">Overdue since {a.dueDate && formatDay(a.dueDate)}</span>}
                  </p>
                  {a.status === 'RETURN_REQUESTED' && (
                    <p className="text-sm text-amber-800">
                      Asked {formatDate(a.returnRequestedAt)}
                      {a.returnNote && `: “${a.returnNote}”`}
                    </p>
                  )}
                </div>
                {a.status === 'RETURN_REQUESTED' && <Button onClick={() => setAccepting(a)}>Check in</Button>}
              </div>
            </li>
          ))}
        </ul>
      )}
      {accepting && <AcceptModal assignment={accepting} onClose={() => setAccepting(null)} />}
    </>
  )
}

function AcceptModal({ assignment, onClose }: { assignment: Assignment; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const meta = useQuery({ queryKey: ['meta'], queryFn: metaApi.meta })
  const [condition, setCondition] = useState(assignment.conditionOut)
  const [note, setNote] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const accept = useMutation({
    mutationFn: () => adminApi.accept(assignment.id, condition, note),
    onSuccess: (a) => {
      toast.success(`${a.assetTag} is back and available`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(err.fieldErrors)
      } else {
        setFormError(errorMessage(err))
        queryClient.invalidateQueries()
      }
    },
  })

  return (
    <Modal title={`Check in ${assignment.assetTag}`} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          {assignment.assetName} from {assignment.employeeName}. It went out in {statusLabel(assignment.conditionOut).toLowerCase()} condition.
        </p>
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <SelectField label="Condition now" value={condition} onChange={(e) => setCondition(e.target.value as Assignment['conditionOut'])} error={fieldErrors.condition}>
          {(meta.data?.conditions ?? ['NEW', 'GOOD', 'FAIR', 'POOR']).map((c) => (
            <option key={c} value={c}>
              {statusLabel(c)}
            </option>
          ))}
        </SelectField>
        <TextAreaField label="Note (optional)" rows={2} maxLength={300} placeholder="Charger returned, small scratch…" value={note} onChange={(e) => setNote(e.target.value)} error={fieldErrors.note} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={accept.isPending}>
            Not yet
          </Button>
          <Button loading={accept.isPending} onClick={() => accept.mutate()}>
            Accept return
          </Button>
        </div>
      </div>
    </Modal>
  )
}
