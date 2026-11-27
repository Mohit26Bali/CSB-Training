import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { Allocation } from '../../api/types'
import { floorLabel } from '../../components/SlotMap'
import { useToast } from '../../components/Toast'
import { Button, ConfirmDialog, EmptyState, ErrorState, formatDate, LoadingState, PageHeader, StatusBadge } from '../../components/ui'

export function AllocationsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [showPast, setShowPast] = useState(false)
  const [releasing, setReleasing] = useState<Allocation | null>(null)
  const list = useQuery({ queryKey: ['admin', 'allocations', showPast], queryFn: () => adminApi.allocations(!showPast), placeholderData: (previous) => previous })

  const release = useMutation({
    mutationFn: (a: Allocation) => adminApi.release(a.id),
    onSuccess: (a) => toast.success(`Slot ${a.slotCode} is free again`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => {
      setReleasing(null)
      queryClient.invalidateQueries()
    },
  })

  return (
    <>
      <PageHeader
        title="Allocations"
        subtitle="Who holds which slot. Release a slot when the allocation ends."
        actions={
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" checked={showPast} onChange={(e) => setShowPast(e.target.checked)} />
            Include released
          </label>
        }
      />
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="No slots are allocated" />
      ) : (
        <ul className="space-y-3">
          {list.data.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-medium text-slate-900">{a.employeeName}</p>
                  <StatusBadge status={a.active ? 'ACTIVE' : 'CLOSED'} />
                </div>
                <p className="text-sm text-slate-600">
                  Slot {a.slotCode} · {floorLabel(a.floor)} · <span className="font-mono">{a.vehicleNumber}</span>
                </p>
                <p className="text-xs text-slate-500">
                  Since {formatDate(a.allocatedAt)}
                  {a.releasedAt && `, released ${formatDate(a.releasedAt)} by ${a.releasedBy}`}
                </p>
              </div>
              {a.active && (
                <Button variant="secondary" small onClick={() => setReleasing(a)}>
                  Release
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {releasing && (
        <ConfirmDialog
          title="Release this slot?"
          message={`${releasing.employeeName} loses slot ${releasing.slotCode}. It becomes free for the next request.`}
          confirmLabel="Release"
          danger
          loading={release.isPending}
          onConfirm={() => release.mutate(releasing)}
          onCancel={() => setReleasing(null)}
        />
      )}
    </>
  )
}
