import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { employeeApi } from '../../api/endpoints'
import type { Assignment } from '../../api/types'
import { AssetPhoto } from '../../components/AssetPhoto'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, formatDate, formatDay, LoadingState, Modal, PageHeader, StatusBadge, TextAreaField } from '../../components/ui'

export function MyAssetsPage() {
  const list = useQuery({ queryKey: ['my', 'assets'], queryFn: employeeApi.mine })
  const [returning, setReturning] = useState<Assignment | null>(null)

  return (
    <>
      <PageHeader title="My equipment" subtitle="Everything IT has handed to you. Raise a return request when you no longer need something." />
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="You are not holding any equipment" hint="When IT assigns something to you it appears here." />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {list.data.map((a) => (
            <li key={a.id} className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
              <AssetPhoto category={a.category} className="h-36 w-full" />
              <div className="space-y-2 p-4">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <h2 className="truncate font-semibold text-slate-900">{a.assetName}</h2>
                    <p className="font-mono text-xs text-slate-500">
                      {a.assetTag} · S/N {a.serialNumber}
                    </p>
                  </div>
                  <StatusBadge status={a.status} />
                </div>
                <p className="text-sm text-slate-600">
                  With you since {formatDay(a.assignedAt.slice(0, 10))} · came out in <StatusBadge status={a.conditionOut} /> condition
                </p>
                {a.dueDate && (
                  <p className={`text-sm ${a.overdue ? 'font-semibold text-rose-700' : 'text-slate-600'}`}>
                    {a.overdue ? 'Was due back on' : 'Due back by'} {formatDay(a.dueDate)}
                  </p>
                )}
                {a.note && <p className="text-xs text-slate-500">“{a.note}”</p>}
                {a.status === 'RETURN_REQUESTED' ? (
                  <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">You asked to return this on {formatDate(a.returnRequestedAt)}. Hand it to IT and they will check it in.</p>
                ) : (
                  <Button small variant="secondary" onClick={() => setReturning(a)}>
                    Return this
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      {returning && <ReturnModal assignment={returning} onClose={() => setReturning(null)} />}
    </>
  )
}

function ReturnModal({ assignment, onClose }: { assignment: Assignment; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)

  const request = useMutation({
    mutationFn: () => employeeApi.requestReturn(assignment.id, note),
    onSuccess: (a) => {
      toast.success(`Return requested for ${a.assetTag}`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(errorMessage(err))
      queryClient.invalidateQueries()
    },
  })

  return (
    <Modal title={`Return ${assignment.assetName}?`} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          {assignment.assetTag}. IT will be notified; hand the equipment over (with chargers and cables) and they will confirm it is back.
        </p>
        <TextAreaField label="Anything IT should know? (optional)" rows={2} maxLength={300} placeholder="Upgrading, project finished, screen flickers…" value={note} onChange={(e) => setNote(e.target.value)} error={error ?? undefined} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={request.isPending}>
            Keep it
          </Button>
          <Button loading={request.isPending} onClick={() => request.mutate()}>
            Request return
          </Button>
        </div>
      </div>
    </Modal>
  )
}
