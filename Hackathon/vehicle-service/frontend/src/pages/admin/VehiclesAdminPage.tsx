import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { Vehicle } from '../../api/types'
import { Plate, VehiclePhoto } from '../../components/VehiclePhoto'
import { useDebounced } from '../../hooks'
import { Button, EmptyState, ErrorState, Field, formatDay, formatMoney, LoadingState, Modal, PageHeader, statusLabel } from '../../components/ui'

export function VehiclesAdminPage() {
  const [search, setSearch] = useState('')
  const q = useDebounced(search)
  const [viewing, setViewing] = useState<Vehicle | null>(null)
  const list = useQuery({ queryKey: ['admin', 'vehicles', q], queryFn: () => adminApi.vehicles(q), placeholderData: (previous) => previous })

  return (
    <>
      <PageHeader title="Vehicles" subtitle="Search by registration number, make, model or owner." />
      <div className="mb-4 max-w-md">
        <Field label="Search" placeholder="MH12AB1234, Swift, Aarav…" value={search} onChange={(e) => setSearch(e.target.value)} />
      </div>
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="No vehicle found" hint="Check the number or try part of the owner's name." />
      ) : (
        <>
          <p className="mb-2 text-sm text-slate-600">
            {list.data.length} vehicle{list.data.length === 1 ? '' : 's'}
          </p>
          <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {list.data.map((v) => (
              <li key={v.id} className="flex overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                <VehiclePhoto type={v.type} id={v.id} className="w-24 shrink-0" />
                <div className="min-w-0 flex-1 space-y-1 p-3">
                  <Plate number={v.number} />
                  <p className="truncate font-semibold text-slate-900">
                    {v.make} {v.model} <span className="font-normal text-slate-500">({v.year})</span>
                  </p>
                  <p className="truncate text-sm text-slate-600">
                    {statusLabel(v.type)} · {v.ownerName}
                  </p>
                  <p className="text-xs text-slate-500">
                    {v.completedServices} done · {v.nextService ? `next ${formatDay(v.nextService)}` : 'nothing booked'}
                  </p>
                  <Button small variant="secondary" onClick={() => setViewing(v)}>
                    Service history
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
      {viewing && <HistoryModal vehicle={viewing} onClose={() => setViewing(null)} />}
    </>
  )
}

function HistoryModal({ vehicle, onClose }: { vehicle: Vehicle; onClose: () => void }) {
  const history = useQuery({ queryKey: ['admin', 'vehicles', vehicle.id, 'history'], queryFn: () => adminApi.history(vehicle.id) })
  return (
    <Modal title={`${vehicle.number} · ${vehicle.make} ${vehicle.model}`} onClose={onClose}>
      <p className="mb-3 text-sm text-slate-600">Owner: {vehicle.ownerName}</p>
      {history.isPending ? (
        <LoadingState />
      ) : history.isError ? (
        <ErrorState message={errorMessage(history.error)} />
      ) : history.data.length === 0 ? (
        <p className="text-sm text-slate-600">No finished services yet.</p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {history.data.map((b) => (
            <li key={b.id} className="py-3">
              <div className="flex items-baseline justify-between gap-3">
                <p className="font-medium text-slate-900">{b.serviceName}</p>
                <p className="text-sm font-semibold text-slate-900">{formatMoney(b.finalCost ?? b.estimatedCost)}</p>
              </div>
              <p className="text-sm text-slate-600">
                {formatDay(b.startDate)} · by {b.completedBy}
              </p>
              {b.workNotes && <p className="mt-1 text-xs text-slate-500">{b.workNotes}</p>}
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}
