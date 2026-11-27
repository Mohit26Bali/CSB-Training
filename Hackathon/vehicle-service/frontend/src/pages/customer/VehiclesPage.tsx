import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, errorMessage } from '../../api/client'
import { customerApi } from '../../api/endpoints'
import type { Vehicle, VehicleInput } from '../../api/types'
import { Plate, VehiclePhoto } from '../../components/VehiclePhoto'
import { useToast } from '../../components/Toast'
import { Button, ConfirmDialog, EmptyState, ErrorState, Field, formatDay, formatMoney, LoadingState, Modal, PageHeader, SelectField, statusLabel } from '../../components/ui'

export function VehiclesPage() {
  const list = useQuery({ queryKey: ['vehicles'], queryFn: customerApi.vehicles })
  const [adding, setAdding] = useState(false)
  const [viewing, setViewing] = useState<Vehicle | null>(null)
  const [removing, setRemoving] = useState<Vehicle | null>(null)

  return (
    <>
      <PageHeader title="My vehicles" subtitle="Register a vehicle once, then book as many services as you need." actions={<Button onClick={() => setAdding(true)}>Add a vehicle</Button>} />
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="No vehicles yet" hint="Add your car or bike to book a service." action={<Button onClick={() => setAdding(true)}>Add a vehicle</Button>} />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {list.data.map((v) => (
            <li key={v.id} className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
              <VehiclePhoto type={v.type} id={v.id} className="h-36 w-full" />
              <div className="space-y-3 p-4">
                <div className="flex items-center justify-between gap-2">
                  <Plate number={v.number} />
                  <span className="text-xs font-medium text-slate-500">{statusLabel(v.type)}</span>
                </div>
                <div>
                  <h2 className="font-semibold text-slate-900">
                    {v.make} {v.model}
                  </h2>
                  <p className="text-sm text-slate-600">
                    {v.year} · {v.completedServices} service{v.completedServices === 1 ? '' : 's'} done
                  </p>
                  <p className="text-sm text-slate-600">{v.nextService ? `Next service: ${formatDay(v.nextService)}` : 'No service booked'}</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Link to={`/book?vehicle=${v.id}`}>
                    <Button small>Book a service</Button>
                  </Link>
                  <Button small variant="secondary" onClick={() => setViewing(v)}>
                    History
                  </Button>
                  <Button small variant="secondary" onClick={() => setRemoving(v)}>
                    Remove
                  </Button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
      {adding && <AddVehicleModal onClose={() => setAdding(false)} />}
      {viewing && <HistoryModal vehicle={viewing} onClose={() => setViewing(null)} />}
      {removing && <RemoveDialog vehicle={removing} onClose={() => setRemoving(null)} />}
    </>
  )
}

function AddVehicleModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<VehicleInput>({ number: '', type: '', make: '', model: '', year: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => customerApi.addVehicle(form),
    onSuccess: (v) => {
      toast.success(`${v.number} added`)
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
  const set = (key: keyof VehicleInput) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    save.mutate()
  }

  return (
    <Modal title="Add a vehicle" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="Registration number" placeholder="MH12AB1234" maxLength={15} value={form.number} onChange={set('number')} error={fieldErrors.number} hint="Each number can be registered once" />
        <SelectField label="Kind of vehicle" value={form.type} onChange={set('type')} error={fieldErrors.type}>
          <option value="">Choose…</option>
          <option value="CAR">Car</option>
          <option value="SUV">SUV</option>
          <option value="BIKE">Bike or scooter</option>
          <option value="VAN">Van</option>
        </SelectField>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Make" placeholder="Maruti Suzuki" maxLength={40} value={form.make} onChange={set('make')} error={fieldErrors.make} />
          <Field label="Model" placeholder="Swift" maxLength={40} value={form.model} onChange={set('model')} error={fieldErrors.model} />
        </div>
        <Field label="Year" type="number" min={1990} placeholder="2020" value={form.year} onChange={set('year')} error={fieldErrors.year} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            Add vehicle
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function HistoryModal({ vehicle, onClose }: { vehicle: Vehicle; onClose: () => void }) {
  const history = useQuery({ queryKey: ['vehicles', vehicle.id, 'history'], queryFn: () => customerApi.history(vehicle.id) })
  return (
    <Modal title={`Service history · ${vehicle.number}`} onClose={onClose}>
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
              <p className="text-sm text-slate-600">{formatDay(b.startDate)}</p>
              {b.workNotes && <p className="mt-1 text-xs text-slate-500">{b.workNotes}</p>}
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}

function RemoveDialog({ vehicle, onClose }: { vehicle: Vehicle; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const remove = useMutation({
    mutationFn: () => customerApi.removeVehicle(vehicle.id),
    onSuccess: () => {
      toast.success(`${vehicle.number} removed`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      toast.error(errorMessage(err))
      onClose()
    },
  })
  return (
    <ConfirmDialog
      title={`Remove ${vehicle.number}?`}
      message="A vehicle that has service bookings cannot be removed."
      confirmLabel="Remove"
      danger
      loading={remove.isPending}
      onConfirm={() => remove.mutate()}
      onCancel={onClose}
    />
  )
}
