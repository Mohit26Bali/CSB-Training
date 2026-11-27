import type { Aging, PriorityName, RequestStatus } from '../api/types'

const priorityStyles: Record<PriorityName, string> = {
  LOW: 'bg-slate-100 text-slate-600',
  MEDIUM: 'bg-sky-100 text-sky-800',
  HIGH: 'bg-amber-100 text-amber-800',
  EMERGENCY: 'bg-rose-100 text-rose-800',
}

export function PriorityChip({ priority }: { priority: PriorityName }) {
  return (
    <span className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ${priorityStyles[priority]}`}>
      <span className="size-1.5 rounded-full bg-current" />
      {priority.charAt(0) + priority.slice(1).toLowerCase()}
    </span>
  )
}

/** How long an active request has been waiting: fresh, ageing (2 to 4 days) or stale (5 days or more). */
export function AgingChip({ aging, days }: { aging: Aging | null; days: number }) {
  if (!aging) return null
  const map = {
    FRESH: ['bg-emerald-100 text-emerald-800', days === 0 ? 'Today' : `${days} day${days === 1 ? '' : 's'}`],
    AGING: ['bg-amber-100 text-amber-800', `Ageing · ${days} days`],
    STALE: ['bg-rose-100 text-rose-800', `Stale · ${days} days`],
  } as const
  const [style, label] = map[aging]
  return <span className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>{label}</span>
}

const steps: { status: RequestStatus; label: string }[] = [
  { status: 'OPEN', label: 'Reported' },
  { status: 'ASSIGNED', label: 'Assigned' },
  { status: 'IN_PROGRESS', label: 'In progress' },
  { status: 'RESOLVED', label: 'Resolved' },
  { status: 'CLOSED', label: 'Closed' },
]

/** Five dots joined by a line: the request's journey from reported to closed. */
export function Journey({ status }: { status: RequestStatus }) {
  const at = steps.findIndex((s) => s.status === status)
  return (
    <ol className="flex items-start" aria-label="Progress">
      {steps.map((s, i) => (
        <li key={s.status} className="flex flex-1 flex-col items-center text-center">
          <div className="flex w-full items-center">
            <span className={`h-0.5 flex-1 ${i === 0 ? 'bg-transparent' : i <= at ? 'bg-brand-600' : 'bg-slate-200'}`} />
            <span className={`grid size-5 shrink-0 place-items-center rounded-full text-[10px] font-bold ${i < at ? 'bg-brand-600 text-white' : i === at ? 'bg-brand-600 text-white ring-4 ring-brand-100' : 'bg-slate-200 text-slate-500'}`}>
              {i < at ? '✓' : i + 1}
            </span>
            <span className={`h-0.5 flex-1 ${i === steps.length - 1 ? 'bg-transparent' : i < at ? 'bg-brand-600' : 'bg-slate-200'}`} />
          </div>
          <span className={`mt-1 text-[11px] ${i === at ? 'font-semibold text-slate-900' : 'text-slate-500'}`}>{s.label}</span>
        </li>
      ))}
    </ol>
  )
}
