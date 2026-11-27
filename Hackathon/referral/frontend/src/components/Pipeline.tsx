import type { ReferralStatus } from '../api/types'

const stages: { status: ReferralStatus; label: string }[] = [
  { status: 'SUBMITTED', label: 'Submitted' },
  { status: 'SCREENING', label: 'Screening' },
  { status: 'INTERVIEW', label: 'Interview' },
  { status: 'SELECTED', label: 'Selected' },
]

/** The four stages of the hiring pipeline as a row of dots. A rejected referral shows no stage as reached, just "Rejected". */
export function Pipeline({ status }: { status: ReferralStatus }) {
  const rejected = status === 'REJECTED'
  const current = rejected ? -1 : stages.findIndex((s) => s.status === status)

  return (
    <ol className="flex items-center gap-1.5" aria-label={`Pipeline stage: ${rejected ? 'rejected' : stages[current].label}`}>
      {stages.map((stage, i) => {
        const done = i <= current
        return (
          <li key={stage.status} className="flex items-center gap-1.5">
            <span
              title={stage.label}
              className={`grid size-5 place-items-center rounded-full text-[10px] font-semibold ${
                done ? (status === 'SELECTED' ? 'bg-emerald-500 text-white' : 'bg-brand-600 text-white') : 'bg-slate-200 text-slate-500'
              }`}
            >
              {done ? '✓' : i + 1}
            </span>
            {i < stages.length - 1 && <span className={`h-0.5 w-5 rounded ${i < current ? 'bg-brand-600' : 'bg-slate-200'}`} />}
          </li>
        )
      })}
      <li className={`ml-2 text-xs font-medium ${rejected ? 'text-rose-700' : 'text-slate-600'}`}>{rejected ? 'Rejected' : stages[current].label}</li>
    </ol>
  )
}
