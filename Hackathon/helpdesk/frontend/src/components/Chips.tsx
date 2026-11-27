import type { PriorityName, Ticket } from '../api/types'

const priorityStyles: Record<PriorityName, string> = {
  LOW: 'bg-slate-100 text-slate-600',
  MEDIUM: 'bg-sky-100 text-sky-800',
  HIGH: 'bg-amber-100 text-amber-800',
  CRITICAL: 'bg-rose-100 text-rose-800',
}

export function PriorityChip({ priority }: { priority: PriorityName }) {
  return (
    <span className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ${priorityStyles[priority]}`}>
      <span className="size-1.5 rounded-full bg-current" />
      {priority.charAt(0) + priority.slice(1).toLowerCase()}
    </span>
  )
}

function span(hours: number) {
  const h = Math.abs(hours)
  return h >= 48 ? `${Math.round(h / 24)}d` : `${h}h`
}

/** The SLA at a glance: how long is left, how late it is, or whether it was met. */
export function SlaChip({ ticket }: { ticket: Pick<Ticket, 'slaState' | 'hoursLeft'> }) {
  const map = {
    ON_TRACK: ['bg-emerald-100 text-emerald-800', `On track · ${span(ticket.hoursLeft)} left`],
    AT_RISK: ['bg-amber-100 text-amber-800', `At risk · ${span(ticket.hoursLeft)} left`],
    BREACHED: ['bg-rose-100 text-rose-800', `Overdue by ${span(ticket.hoursLeft)}`],
    MET: ['bg-emerald-100 text-emerald-800', 'SLA met'],
    MISSED: ['bg-rose-100 text-rose-800', 'SLA missed'],
  } as const
  const [style, label] = map[ticket.slaState]
  return <span className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>{label}</span>
}
