import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { providerApi } from '../../api/endpoints'
import type { BulkInput, Slot, SlotInput } from '../../api/types'
import { useToast } from '../../components/Toast'
import { addDays, formatTime, isoDay, longDay, parseDay, weekday } from '../../dates'
import { Button, ErrorState, Field, LoadingState, Modal, PageHeader } from '../../components/ui'
import { CancelModal, type CancelTarget } from './CancelModal'
import { useConfirm } from './useConfirm'

const tone: Record<Slot['status'], string> = {
  AVAILABLE: 'border-emerald-300 bg-emerald-50 text-emerald-900 hover:bg-emerald-100',
  PENDING: 'border-amber-300 bg-amber-50 text-amber-900 hover:bg-amber-100',
  CONFIRMED: 'border-brand-400 bg-brand-100 text-brand-900 hover:bg-brand-200',
}

export function CalendarPage() {
  const today = useMemo(() => isoDay(new Date()), [])
  const [from, setFrom] = useState(today)
  const [adding, setAdding] = useState(false)
  const [open, setOpen] = useState<Slot | null>(null)
  const slots = useQuery({ queryKey: ['provider', 'slots', from], queryFn: () => providerApi.slots(from, 7), placeholderData: (previous) => previous })
  const dates = Array.from({ length: 7 }, (_, i) => addDays(from, i))

  return (
    <>
      <PageHeader
        title="Calendar"
        subtitle="Green slots are open, amber ones wait for you to confirm, purple ones are confirmed."
        actions={<Button onClick={() => setAdding(true)}>Add slots</Button>}
      />
      <div className="mb-4 flex items-center gap-2">
        <Button variant="secondary" small onClick={() => setFrom(addDays(from, -7))}>
          ← Previous week
        </Button>
        <Button variant="secondary" small disabled={from === today} onClick={() => setFrom(today)}>
          Today
        </Button>
        <Button variant="secondary" small onClick={() => setFrom(addDays(from, 7))}>
          Next week →
        </Button>
        <span className="ml-2 text-sm text-slate-600">
          {longDay(dates[0])} to {longDay(dates[6])}
        </span>
      </div>
      {slots.isPending ? (
        <LoadingState />
      ) : slots.isError ? (
        <ErrorState message={errorMessage(slots.error)} onRetry={() => slots.refetch()} />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-7">
          {dates.map((d) => {
            const list = slots.data.filter((s) => s.start.slice(0, 10) === d)
            return (
              <section key={d} className={`rounded-xl border bg-white p-3 shadow-sm ${d === today ? 'border-brand-400' : 'border-slate-200'}`}>
                <h2 className="mb-2 text-center">
                  <span className="block text-[11px] uppercase text-slate-500">{weekday(d)}</span>
                  <span className="text-xl font-semibold text-slate-900">{parseDay(d).getDate()}</span>
                </h2>
                {list.length === 0 ? (
                  <p className="py-4 text-center text-xs text-slate-400">No slots</p>
                ) : (
                  <ul className="space-y-1.5">
                    {list.map((s) => (
                      <li key={s.id}>
                        <button onClick={() => setOpen(s)} className={`w-full rounded-lg border px-2 py-1.5 text-left text-xs transition ${tone[s.status]}`}>
                          <span className="block font-semibold">{formatTime(s.start)}</span>
                          <span className="block truncate">{s.customerName ?? 'Open'}</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            )
          })}
        </div>
      )}
      {adding && <AddSlotsModal defaultDate={from < today ? today : from} onClose={() => setAdding(false)} />}
      {open && <SlotModal slot={open} onClose={() => setOpen(null)} />}
    </>
  )
}

function SlotModal({ slot, onClose }: { slot: Slot; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const confirm = useConfirm()
  const [cancelling, setCancelling] = useState<CancelTarget | null>(null)
  const close = useMutation({
    mutationFn: () => providerApi.closeSlot(slot.id),
    onSuccess: () => {
      toast.success('Slot withdrawn')
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  if (cancelling) return <CancelModal appointment={cancelling} onClose={onClose} />

  return (
    <Modal title={`${longDay(slot.start)}, ${formatTime(slot.start)} to ${formatTime(slot.end)}`} onClose={onClose}>
      <div className="space-y-4">
        {slot.status === 'AVAILABLE' ? (
          <>
            <p className="text-sm text-slate-600">This slot is open for booking ({slot.minutes} minutes). Withdraw it if you are not available.</p>
            <div className="flex justify-end gap-2">
              <Button variant="secondary" onClick={onClose}>
                Close
              </Button>
              <Button variant="danger" loading={close.isPending} onClick={() => close.mutate()}>
                Withdraw slot
              </Button>
            </div>
          </>
        ) : (
          <>
            <dl className="space-y-1 text-sm">
              <div className="flex justify-between">
                <dt className="text-slate-500">Customer</dt>
                <dd className="font-medium text-slate-900">{slot.customerName}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500">Status</dt>
                <dd className="font-medium text-slate-900">{slot.status === 'PENDING' ? 'Waiting for you' : 'Confirmed'}</dd>
              </div>
              {slot.reason && (
                <div className="flex justify-between gap-4">
                  <dt className="text-slate-500">About</dt>
                  <dd className="text-right text-slate-900">{slot.reason}</dd>
                </div>
              )}
            </dl>
            <div className="flex justify-end gap-2">
              <Button
                variant="secondary"
                onClick={() => slot.appointmentId && setCancelling({ id: slot.appointmentId, number: 'this appointment', customerName: slot.customerName ?? '', start: slot.start })}
              >
                Cancel appointment
              </Button>
              {slot.status === 'PENDING' && slot.appointmentId && (
                <Button
                  loading={confirm.isPending}
                  onClick={() => confirm.mutate(slot.appointmentId as number, { onSuccess: onClose })}
                >
                  Confirm
                </Button>
              )}
            </div>
          </>
        )}
      </div>
    </Modal>
  )
}

function AddSlotsModal({ defaultDate, onClose }: { defaultDate: string; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [mode, setMode] = useState<'single' | 'bulk'>('bulk')
  const [single, setSingle] = useState<SlotInput>({ date: defaultDate, startTime: '09:00', minutes: 30 })
  const [bulk, setBulk] = useState<BulkInput>({ date: defaultDate, fromTime: '09:00', toTime: '12:00', minutes: 30 })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => (mode === 'single' ? providerApi.createSlot(single).then(() => 1) : providerApi.createBulk(bulk).then((r) => r.length)),
    onSuccess: (count) => {
      toast.success(count === 1 ? 'Slot added' : `${count} slots added`)
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

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    save.mutate()
  }

  return (
    <Modal title="Add slots" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <div role="tablist" aria-label="One slot or many" className="flex gap-1">
          {(['bulk', 'single'] as const).map((m) => (
            <button
              type="button"
              key={m}
              role="tab"
              aria-selected={mode === m}
              onClick={() => {
                setMode(m)
                setFieldErrors({})
                setFormError(null)
              }}
              className={`rounded-lg px-3 py-1.5 text-sm font-medium ${mode === m ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
            >
              {m === 'bulk' ? 'A whole stretch of time' : 'One slot'}
            </button>
          ))}
        </div>
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        {mode === 'single' ? (
          <>
            <Field label="Date" type="date" value={single.date} onChange={(e) => setSingle((s) => ({ ...s, date: e.target.value }))} error={fieldErrors.date} />
            <div className="grid grid-cols-2 gap-4">
              <Field label="Starts at" type="time" value={single.startTime} onChange={(e) => setSingle((s) => ({ ...s, startTime: e.target.value }))} error={fieldErrors.startTime} />
              <Field label="Length (minutes)" type="number" min={15} max={180} step={5} value={single.minutes} onChange={(e) => setSingle((s) => ({ ...s, minutes: e.target.value }))} error={fieldErrors.minutes} />
            </div>
          </>
        ) : (
          <>
            <Field label="Date" type="date" value={bulk.date} onChange={(e) => setBulk((s) => ({ ...s, date: e.target.value }))} error={fieldErrors.date} />
            <div className="grid grid-cols-3 gap-4">
              <Field label="From" type="time" value={bulk.fromTime} onChange={(e) => setBulk((s) => ({ ...s, fromTime: e.target.value }))} error={fieldErrors.fromTime} />
              <Field label="To" type="time" value={bulk.toTime} onChange={(e) => setBulk((s) => ({ ...s, toTime: e.target.value }))} error={fieldErrors.toTime} />
              <Field label="Each slot (min)" type="number" min={15} max={180} step={5} value={bulk.minutes} onChange={(e) => setBulk((s) => ({ ...s, minutes: e.target.value }))} error={fieldErrors.minutes} />
            </div>
            <p className="text-xs text-slate-500">The window is cut into back-to-back slots, for example 09:00 to 12:00 in 30 minutes makes six. Up to 24 at a time.</p>
          </>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {mode === 'single' ? 'Add slot' : 'Add slots'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
