import type { BookingStatus } from '../api/types'

const steps = [
  { key: 'BOOKED', label: 'Booked' },
  { key: 'IN_SERVICE', label: 'In service' },
  { key: 'COMPLETED', label: 'Completed' },
] as const

/** Booked → in service → completed, with the current step lit. A cancelled booking shows a single red note instead. */
export function Progress({ status }: { status: BookingStatus }) {
  if (status === 'CANCELLED') {
    return <p className="text-xs font-semibold text-rose-700">Cancelled</p>
  }
  const current = steps.findIndex((s) => s.key === status)
  return (
    <ol className="flex items-center gap-2" aria-label="Service progress">
      {steps.map((s, i) => (
        <li key={s.key} className="flex items-center gap-2">
          <span
            className={`grid size-5 place-items-center rounded-full text-[10px] font-bold ${
              i < current || status === 'COMPLETED' ? 'bg-brand-600 text-white' : i === current ? 'bg-brand-100 text-brand-800 ring-2 ring-brand-500' : 'bg-slate-200 text-slate-500'
            }`}
          >
            {i < current || status === 'COMPLETED' ? '✓' : i + 1}
          </span>
          <span className={`text-xs ${i === current ? 'font-semibold text-slate-900' : 'text-slate-500'}`}>{s.label}</span>
          {i < steps.length - 1 && <span className={`h-px w-6 ${i < current ? 'bg-brand-500' : 'bg-slate-300'}`} />}
        </li>
      ))}
    </ol>
  )
}
