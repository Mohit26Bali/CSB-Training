import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../../api/client'
import { customerApi } from '../../api/endpoints'
import type { BookingInput, DayLoad } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Plate } from '../../components/VehiclePhoto'
import { Button, EmptyState, ErrorState, formatDay, formatMoney, LoadingState, PageHeader, SelectField, TextAreaField } from '../../components/ui'

const DAYS = 35

function isoToday(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function BookPage() {
  const toast = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [params] = useSearchParams()
  const [form, setForm] = useState<BookingInput>({ vehicleId: Number(params.get('vehicle')) || '', serviceTypeId: '', serviceDate: '', notes: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const vehicles = useQuery({ queryKey: ['vehicles'], queryFn: customerApi.vehicles })
  const vehicle = vehicles.data?.find((v) => v.id === form.vehicleId)
  const services = useQuery({ queryKey: ['catalog', vehicle?.type ?? 'CAR'], queryFn: () => customerApi.services(vehicle?.type) })
  const from = useMemo(() => isoToday(), [])
  const availability = useQuery({ queryKey: ['availability', from], queryFn: () => customerApi.availability(from, DAYS) })
  const service = services.data?.find((s) => s.id === form.serviceTypeId)

  /** A date can be chosen when every day of the service has a free bay. */
  const days = availability.data ?? []
  const fits = (index: number): boolean => {
    const span = service?.durationDays ?? 1
    const needed = days.slice(index, index + span)
    return needed.length === span && needed.every((d) => d.free > 0)
  }

  const book = useMutation({
    mutationFn: () => customerApi.book(form),
    onSuccess: (b) => {
      toast.success(`${b.number} booked for ${formatDay(b.startDate)}`)
      queryClient.invalidateQueries()
      navigate('/services')
    },
    onError: (err) => {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(err.fieldErrors)
        setFormError(null)
      } else {
        setFieldErrors({})
        setFormError(errorMessage(err))
        queryClient.invalidateQueries({ queryKey: ['availability'] })
      }
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    book.mutate()
  }

  if (vehicles.isPending) return <LoadingState />
  if (vehicles.isError) return <ErrorState message={errorMessage(vehicles.error)} onRetry={() => vehicles.refetch()} />
  if (vehicles.data.length === 0) {
    return (
      <>
        <PageHeader title="Book a service" />
        <EmptyState title="Add a vehicle first" hint="You need a registered vehicle to book a service." action={<Link to="/vehicles" className="font-medium text-brand-700 hover:underline">Add a vehicle</Link>} />
      </>
    )
  }

  return (
    <>
      <PageHeader title="Book a service" subtitle="Choose the vehicle, the work and a day when a bay is free." />
      <form onSubmit={submit} noValidate className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <div className="space-y-6">
          {formError && (
            <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {formError}
            </p>
          )}
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="mb-3 font-semibold text-slate-900">1. Vehicle</h2>
            <SelectField
              label="Which vehicle?"
              value={form.vehicleId}
              onChange={(e) => setForm((f) => ({ ...f, vehicleId: Number(e.target.value) || '', serviceDate: '' }))}
              error={fieldErrors.vehicleId}
            >
              <option value="">Choose…</option>
              {vehicles.data.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.number} · {v.make} {v.model}
                </option>
              ))}
            </SelectField>
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="mb-3 font-semibold text-slate-900">2. Service</h2>
            {services.isPending ? (
              <LoadingState />
            ) : services.isError ? (
              <ErrorState message={errorMessage(services.error)} />
            ) : (
              <ul className="grid gap-3 sm:grid-cols-2">
                {services.data.map((s) => (
                  <li key={s.id}>
                    <button
                      type="button"
                      aria-pressed={form.serviceTypeId === s.id}
                      onClick={() => setForm((f) => ({ ...f, serviceTypeId: s.id, serviceDate: '' }))}
                      className={`h-full w-full rounded-lg border p-3 text-left transition ${form.serviceTypeId === s.id ? 'border-brand-600 bg-brand-50 ring-2 ring-brand-500' : 'border-slate-200 hover:border-brand-300'}`}
                    >
                      <span className="flex items-baseline justify-between gap-2">
                        <span className="font-medium text-slate-900">{s.name}</span>
                        <span className="text-sm font-semibold text-slate-900">{formatMoney(s.estimatedCost ?? s.basePrice)}</span>
                      </span>
                      <span className="mt-1 block text-xs text-slate-600">{s.description}</span>
                      <span className="mt-2 block text-xs font-medium text-brand-700">
                        {s.durationDays} day{s.durationDays === 1 ? '' : 's'}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {fieldErrors.serviceTypeId && <p className="mt-2 text-sm text-rose-700">{fieldErrors.serviceTypeId}</p>}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="mb-1 font-semibold text-slate-900">3. Day</h2>
            <p className="mb-3 text-sm text-slate-600">The number shows the bays still free. Full days cannot be chosen.</p>
            {availability.isPending ? (
              <LoadingState />
            ) : availability.isError ? (
              <ErrorState message={errorMessage(availability.error)} onRetry={() => availability.refetch()} />
            ) : (
              <ul className="grid grid-cols-4 gap-2 sm:grid-cols-5 md:grid-cols-7">
                {days.map((d, i) => (
                  <DayButton key={d.date} day={d} enabled={fits(i)} selected={form.serviceDate === d.date} onPick={() => setForm((f) => ({ ...f, serviceDate: d.date }))} />
                ))}
              </ul>
            )}
            {fieldErrors.serviceDate && <p className="mt-2 text-sm text-rose-700">{fieldErrors.serviceDate}</p>}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="mb-3 font-semibold text-slate-900">4. Anything we should know?</h2>
            <TextAreaField label="Notes (optional)" rows={2} maxLength={300} placeholder="A noise, a warning light, a pick-up time…" value={form.notes} onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))} error={fieldErrors.notes} />
          </section>
        </div>

        <aside className="h-fit space-y-3 rounded-xl border border-slate-200 bg-white p-5 shadow-sm lg:sticky lg:top-20">
          <h2 className="font-semibold text-slate-900">Your booking</h2>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between gap-3">
              <dt className="text-slate-500">Vehicle</dt>
              <dd className="text-right">{vehicle ? <Plate number={vehicle.number} /> : '—'}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-slate-500">Service</dt>
              <dd className="text-right font-medium text-slate-900">{service?.name ?? '—'}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-slate-500">Day</dt>
              <dd className="text-right font-medium text-slate-900">{form.serviceDate ? formatDay(form.serviceDate) : '—'}</dd>
            </div>
            <div className="flex justify-between gap-3 border-t border-slate-100 pt-2">
              <dt className="text-slate-500">Estimated cost</dt>
              <dd className="text-lg font-semibold text-slate-900">{service?.estimatedCost != null ? formatMoney(service.estimatedCost) : '—'}</dd>
            </div>
          </dl>
          <p className="text-xs text-slate-500">The final bill is set by the workshop when the work is done.</p>
          <Button type="submit" loading={book.isPending} className="w-full">
            Book this service
          </Button>
        </aside>
      </form>
    </>
  )
}

function DayButton({ day, enabled, selected, onPick }: { day: DayLoad; enabled: boolean; selected: boolean; onPick: () => void }) {
  const [y, m, d] = day.date.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  const weekday = new Intl.DateTimeFormat(undefined, { weekday: 'short' }).format(date)
  const month = new Intl.DateTimeFormat(undefined, { month: 'short' }).format(date)
  const tone = day.free === 0 ? 'text-rose-600' : day.free <= 1 ? 'text-amber-600' : 'text-emerald-600'
  return (
    <li>
      <button
        type="button"
        disabled={!enabled}
        aria-pressed={selected}
        onClick={onPick}
        className={`w-full rounded-lg border px-1 py-2 text-center transition disabled:cursor-not-allowed disabled:opacity-45 ${selected ? 'border-brand-600 bg-brand-600 text-white' : 'border-slate-200 bg-white hover:border-brand-400'}`}
      >
        <span className={`block text-[11px] uppercase ${selected ? 'text-white/80' : 'text-slate-500'}`}>{weekday}</span>
        <span className="block text-base font-semibold">{d}</span>
        <span className={`block text-[11px] ${selected ? 'text-white/80' : 'text-slate-500'}`}>{month}</span>
        <span className={`block text-[11px] font-semibold ${selected ? 'text-white' : tone}`}>{day.free === 0 ? 'Full' : `${day.free} free`}</span>
      </button>
    </li>
  )
}
