import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { Allocation } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Button, ConfirmDialog, EmptyState, ErrorState, formatDate, LoadingState, PageHeader, StatusBadge, statusLabel } from '../../components/ui'

export function ResidentsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [showPast, setShowPast] = useState(false)
  const [leaving, setLeaving] = useState<Allocation | null>(null)
  const list = useQuery({ queryKey: ['admin', 'allocations', showPast], queryFn: () => adminApi.allocations(!showPast), placeholderData: (previous) => previous })

  const vacate = useMutation({
    mutationFn: (a: Allocation) => adminApi.vacate(a.id),
    onSuccess: (a) => toast.success(`${a.studentName} has left room ${a.roomNumber}`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => {
      setLeaving(null)
      queryClient.invalidateQueries()
    },
  })

  return (
    <>
      <PageHeader
        title="Residents"
        subtitle="Who lives where. Vacate a room when a student leaves."
        actions={
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" checked={showPast} onChange={(e) => setShowPast(e.target.checked)} />
            Include past residents
          </label>
        }
      />
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="Nobody is living in the hostel yet" />
      ) : (
        <ul className="space-y-3">
          {list.data.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-medium text-slate-900">{a.studentName}</p>
                  <StatusBadge status={a.active ? 'ACTIVE' : 'CLOSED'} />
                </div>
                <p className="text-sm text-slate-600">
                  Room {a.roomNumber} · {statusLabel(a.roomType)} · floor {a.floor}
                </p>
                <p className="text-xs text-slate-500">
                  Since {formatDate(a.allocatedAt)}
                  {a.vacatedAt && `, left ${formatDate(a.vacatedAt)}`}
                </p>
              </div>
              {a.active && (
                <Button variant="secondary" small onClick={() => setLeaving(a)}>
                  Vacate
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {leaving && (
        <ConfirmDialog
          title="Vacate this room?"
          message={`${leaving.studentName} leaves room ${leaving.roomNumber}. The bed becomes free for the next request.`}
          confirmLabel="Vacate"
          danger
          loading={vacate.isPending}
          onConfirm={() => vacate.mutate(leaving)}
          onCancel={() => setLeaving(null)}
        />
      )}
    </>
  )
}
